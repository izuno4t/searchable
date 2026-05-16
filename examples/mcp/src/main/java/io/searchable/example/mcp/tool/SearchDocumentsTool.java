package io.searchable.example.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.searchable.core.application.SearchService;
import io.searchable.core.domain.search.PaginationParams;
import io.searchable.core.domain.search.SearchHit;
import io.searchable.core.domain.search.SearchRequest;
import io.searchable.core.domain.search.SearchResult;
import io.searchable.core.domain.search.SearchType;
import io.searchable.example.mcp.protocol.ToolDefinition;
import io.searchable.example.mcp.protocol.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Exposes Searchable's search engine as the MCP {@code search_documents} tool.
 */
public final class SearchDocumentsTool implements McpTool {

    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final int SNIPPET_LIMIT = 200;

    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public SearchDocumentsTool(final SearchService searchService, final ObjectMapper objectMapper) {
        this.searchService = Objects.requireNonNull(searchService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public ToolDefinition definition() {
        final ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        final ObjectNode properties = schema.putObject("properties");
        properties.putObject("query")
            .put("type", "string")
            .put("description", "Search query text");
        properties.putObject("namespace_ids")
            .put("type", "array")
            .put("description", "Target namespace IDs (empty / omitted = search all)")
            .putObject("items").put("type", "string");
        properties.putObject("search_type")
            .put("type", "string")
            .put("description", "Engine selector: FULL_TEXT, VECTOR, or HYBRID")
            .putArray("enum").add("FULL_TEXT").add("VECTOR").add("HYBRID");
        properties.putObject("max_results")
            .put("type", "integer")
            .put("description", "Maximum number of hits (default 10)")
            .put("default", DEFAULT_MAX_RESULTS);

        schema.putArray("required").add("query");
        return new ToolDefinition(
            "search_documents",
            "Search documents indexed in Searchable namespaces.",
            schema
        );
    }

    @Override
    public ToolResult execute(final JsonNode arguments) {
        try {
            final SearchRequest request = parseRequest(arguments);
            final SearchResult result = searchService.search(request);
            return ToolResult.text(format(result));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("Invalid arguments: " + e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error("Search failed: " + e.getMessage());
        }
    }

    private SearchRequest parseRequest(final JsonNode args) {
        if (args == null || !args.hasNonNull("query")) {
            throw new IllegalArgumentException("'query' is required");
        }
        final String query = args.get("query").asText();

        final List<String> namespaceIds = new ArrayList<>();
        if (args.has("namespace_ids") && args.get("namespace_ids").isArray()) {
            args.get("namespace_ids").forEach(n -> namespaceIds.add(n.asText()));
        }

        final SearchType searchType = args.hasNonNull("search_type")
            ? SearchType.valueOf(args.get("search_type").asText())
            : null;

        final int maxResults = args.has("max_results")
            ? Math.max(args.get("max_results").asInt(DEFAULT_MAX_RESULTS), 1)
            : DEFAULT_MAX_RESULTS;

        return SearchRequest.builder()
            .query(query)
            .namespaceIds(namespaceIds)
            .searchType(searchType)
            .pagination(new PaginationParams(0, maxResults))
            .build();
    }

    private String format(final SearchResult result) {
        if (result.hits().isEmpty()) {
            return "0 hits.";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("検索結果: ").append(result.totalHits()).append("件ヒット (")
            .append(result.tookMs()).append(" ms)\n\n");
        int rank = 1;
        for (final SearchHit hit : result.hits()) {
            sb.append(rank++).append(". [").append(hit.namespaceId()).append("/")
                .append(hit.documentId()).append("] ").append(hit.title()).append("\n");
            sb.append("   score: ").append(String.format("%.4f", hit.score())).append("\n");
            final String snippet = snippet(hit);
            if (!snippet.isEmpty()) {
                sb.append("   ").append(snippet).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String snippet(final SearchHit hit) {
        if (hit.highlights() != null && hit.highlights().containsKey("content")) {
            final List<String> fragments = hit.highlights().get("content");
            if (fragments != null && !fragments.isEmpty()) {
                return truncate(fragments.get(0).replaceAll("</?mark>", ""));
            }
        }
        return hit.content() == null ? "" : truncate(hit.content());
    }

    private String truncate(final String text) {
        if (text.length() <= SNIPPET_LIMIT) {
            return text;
        }
        return text.substring(0, SNIPPET_LIMIT) + "...";
    }
}
