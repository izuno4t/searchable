package io.searchable.example.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.searchable.core.application.HybridSearchOrchestrator;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.application.SearchService;
import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
import io.searchable.core.infrastructure.lucene.LuceneVectorSearcher;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import io.searchable.example.mcp.config.McpCapabilitiesConfig;
import io.searchable.example.mcp.tool.SearchDocumentsTool;
import io.searchable.testkit.db.H2DatabaseFixture;
import io.searchable.testkit.embedding.FakeEmbeddingProvider;
import io.searchable.testkit.lucene.LuceneIndexFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchDocumentsToolIntegrationTest {

    @TempDir Path tempDir;

    private H2DatabaseFixture db;
    private LuceneIndexFixture index;
    private FakeEmbeddingProvider embedding;
    private HybridSearchOrchestrator hybrid;
    private McpServer server;

    @BeforeEach
    void setUp() {
        db = H2DatabaseFixture.fileBacked(tempDir);
        index = LuceneIndexFixture.create(tempDir.resolve("idx"));
        embedding = new FakeEmbeddingProvider(128);

        final JdbcNamespaceRepository nsRepo = new JdbcNamespaceRepository(db.dataSource());
        final NamespaceService nsService = new NamespaceService(nsRepo,
            new JdbcIndexMetadataRepository(db.dataSource()), index.provider(),
            GlobalConfig.defaults(),
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC));
        final LuceneIndexer indexer = new LuceneIndexer(index.provider(), embedding);
        final LuceneFullTextSearcher ft = new LuceneFullTextSearcher(index.provider());
        final LuceneVectorSearcher vec = new LuceneVectorSearcher(index.provider(), embedding);
        hybrid = new HybridSearchOrchestrator(ft, vec);
        final SearchService searchService = new SearchService(nsRepo, ft, vec, hybrid);

        nsService.create("docs", "docs", null);
        indexer.indexBatch("docs", List.of(
            Document.builder().id("d1").namespaceId("docs")
                .title("Lucene入門").content("全文検索エンジンの解説").build(),
            Document.builder().id("d2").namespaceId("docs")
                .title("ベクトル検索").content("意味的類似度に基づく検索手法").build()
        ));

        final ObjectMapper json = SearchableMcpApplication.newObjectMapper();
        server = new McpServer(json, List.of(new SearchDocumentsTool(searchService, json)),
            new McpCapabilitiesConfig(
                new McpCapabilitiesConfig.ServerInfo("searchable-mcp", "1.0.0"),
                null,
                Map.of("tools", Map.of()),
                Map.of()));
    }

    @AfterEach
    void tearDown() {
        hybrid.close();
        index.close();
        embedding.close();
        db.close();
    }

    @Test
    void toolListAdvertisesSearchDocuments() throws Exception {
        final ObjectMapper json = SearchableMcpApplication.newObjectMapper();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        server.serve(
            new ByteArrayInputStream(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}\n"
                    .getBytes(StandardCharsets.UTF_8)),
            out);
        final var tools = json.readTree(out.toString(StandardCharsets.UTF_8).trim())
            .get("result").get("tools");
        assertThat(tools.get(0).get("name").asText()).isEqualTo("search_documents");
    }

    @Test
    void toolCallReturnsSearchResults() throws Exception {
        final ObjectMapper json = SearchableMcpApplication.newObjectMapper();
        final ObjectNode args = json.createObjectNode();
        args.put("query", "全文検索");
        args.putArray("namespace_ids").add("docs");
        args.put("max_results", 5);

        final ObjectNode params = json.createObjectNode();
        params.put("name", "search_documents");
        params.set("arguments", args);

        final ObjectNode req = json.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 1);
        req.put("method", "tools/call");
        req.set("params", params);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        server.serve(new ByteArrayInputStream(
            (req.toString() + "\n").getBytes(StandardCharsets.UTF_8)), out);

        final var response = json.readTree(out.toString(StandardCharsets.UTF_8).trim());
        final var content = response.get("result").get("content");
        assertThat(content.get(0).get("text").asText()).contains("Results").contains("d1");
    }

    @Test
    void missingQueryReturnsToolError() throws Exception {
        final ObjectMapper json = SearchableMcpApplication.newObjectMapper();
        final ObjectNode params = json.createObjectNode();
        params.put("name", "search_documents");
        params.set("arguments", json.createObjectNode());

        final ObjectNode req = json.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 1);
        req.put("method", "tools/call");
        req.set("params", params);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        server.serve(new ByteArrayInputStream(
            (req.toString() + "\n").getBytes(StandardCharsets.UTF_8)), out);

        final var response = json.readTree(out.toString(StandardCharsets.UTF_8).trim());
        assertThat(response.get("result").get("isError").asBoolean()).isTrue();
    }
}
