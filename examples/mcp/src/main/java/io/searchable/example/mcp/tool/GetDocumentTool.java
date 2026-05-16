package io.searchable.example.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.searchable.core.application.DocumentBrowser;
import io.searchable.core.application.DocumentSummary;
import io.searchable.example.mcp.protocol.ToolDefinition;
import io.searchable.example.mcp.protocol.ToolResult;

import java.util.Objects;

/**
 * MCP {@code get_document} tool: looks up a single document by id and
 * returns its title, indexed-at timestamp, and a snippet of the body
 * (TASK-140).
 *
 * <p>Backed by {@link DocumentBrowser} so the tool stays read-only and
 * does not require the index writer to be open.
 */
public final class GetDocumentTool implements McpTool {

    private static final int PAGE_LIMIT = 1000;

    private final DocumentBrowser browser;
    private final ObjectMapper objectMapper;

    public GetDocumentTool(final DocumentBrowser browser, final ObjectMapper objectMapper) {
        this.browser = Objects.requireNonNull(browser);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public ToolDefinition definition() {
        final ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        final ObjectNode properties = schema.putObject("properties");
        properties.putObject("namespace_id")
            .put("type", "string")
            .put("description", "Namespace containing the document");
        properties.putObject("document_id")
            .put("type", "string")
            .put("description", "Identifier of the document to fetch");
        schema.putArray("required").add("namespace_id").add("document_id");
        return new ToolDefinition(
            "get_document",
            "Fetch a single document by namespace + id.",
            schema);
    }

    @Override
    public ToolResult execute(final JsonNode arguments) {
        if (arguments == null
            || !arguments.hasNonNull("namespace_id")
            || !arguments.hasNonNull("document_id")) {
            return ToolResult.error("namespace_id and document_id are required");
        }
        final String namespaceId = arguments.get("namespace_id").asText();
        final String documentId = arguments.get("document_id").asText();
        try {
            // DocumentBrowser exposes only a paged enumeration; scan a
            // reasonable window for the requested id.
            int offset = 0;
            while (true) {
                final var page = browser.list(namespaceId, offset, PAGE_LIMIT);
                for (final DocumentSummary doc : page.items()) {
                    if (doc.id().equals(documentId)) {
                        return ToolResult.text(format(doc));
                    }
                }
                offset += page.items().size();
                if (page.items().isEmpty() || offset >= page.total()) {
                    return ToolResult.error("Document not found: " + namespaceId + "/" + documentId);
                }
            }
        } catch (RuntimeException e) {
            return ToolResult.error("Failed to read document: " + e.getMessage());
        }
    }

    private String format(final DocumentSummary doc) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Document: ").append(doc.namespaceId()).append('/')
            .append(doc.id()).append('\n');
        sb.append("Title: ").append(doc.title()).append('\n');
        if (doc.indexedAt() != null) {
            sb.append("Indexed at: ").append(doc.indexedAt()).append('\n');
        }
        sb.append("\n").append(doc.snippet());
        return sb.toString();
    }
}
