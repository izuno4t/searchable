package com.searchable.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.searchable.core.application.HybridSearchOrchestrator;
import com.searchable.core.application.NamespaceService;
import com.searchable.core.application.SearchService;
import com.searchable.core.application.config.GlobalConfig;
import com.searchable.core.domain.document.Document;
import com.searchable.core.domain.embedding.EmbeddingProvider;
import com.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import com.searchable.core.infrastructure.lucene.AnalyzerFactory;
import com.searchable.core.infrastructure.lucene.IndexLayout;
import com.searchable.core.infrastructure.lucene.LuceneFullTextSearcher;
import com.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import com.searchable.core.infrastructure.lucene.LuceneIndexer;
import com.searchable.core.infrastructure.lucene.LuceneVectorSearcher;
import com.searchable.core.infrastructure.persistence.DataSourceFactory;
import com.searchable.core.infrastructure.persistence.PersistenceConfig;
import com.searchable.core.infrastructure.persistence.SchemaInitializer;
import com.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import com.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import com.searchable.mcp.tool.SearchDocumentsTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchDocumentsToolIntegrationTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider provider;
    private HybridSearchOrchestrator hybrid;
    private McpServer server;

    @BeforeEach
    void setUp() {
        final PersistenceConfig pc = new PersistenceConfig("H2",
            "jdbc:h2:" + tempDir.resolve("db") + ";MODE=PostgreSQL", "sa", "");
        dataSource = DataSourceFactory.create(pc);
        new SchemaInitializer(dataSource).initialize();
        provider = new LuceneIndexProvider(
            new IndexLayout(tempDir.resolve("idx")), AnalyzerFactory.japanese());
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        final JdbcNamespaceRepository nsRepo = new JdbcNamespaceRepository(dataSource);
        final NamespaceService nsService = new NamespaceService(nsRepo,
            new JdbcIndexMetadataRepository(dataSource), provider, GlobalConfig.defaults(),
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC));
        final LuceneIndexer indexer = new LuceneIndexer(provider, embedding);
        final LuceneFullTextSearcher ft = new LuceneFullTextSearcher(provider);
        final LuceneVectorSearcher vec = new LuceneVectorSearcher(provider, embedding);
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
        server = new McpServer(json, List.of(new SearchDocumentsTool(searchService, json)));
    }

    @AfterEach
    void tearDown() throws Exception {
        hybrid.close();
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
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
        assertThat(content.get(0).get("text").asText()).contains("検索結果").contains("d1");
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
