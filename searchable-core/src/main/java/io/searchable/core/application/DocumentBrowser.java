package io.searchable.core.application;

import io.searchable.core.infrastructure.lucene.LuceneFields;
import io.searchable.core.infrastructure.lucene.LuceneIndexContext;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application-layer helper that enumerates indexed documents in a namespace.
 *
 * <p>Used by the admin UI's document-list screen; not part of the public
 * search API.
 */
public final class DocumentBrowser {

    private static final int MAX_SNIPPET_LENGTH = 200;

    private final LuceneIndexProvider provider;

    public DocumentBrowser(final LuceneIndexProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    /** Returns a page of documents, ordered by insertion. */
    public DocumentPage list(final String namespaceId, final int offset, final int limit) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        final LuceneIndexContext ctx = provider.getOrCreate(namespaceId);
        IndexSearcher searcher = null;
        try {
            searcher = ctx.acquireSearcher();
            final TopDocs hits = searcher.search(new MatchAllDocsQuery(), offset + limit);
            final long total = hits.totalHits.value();
            final List<DocumentSummary> items = new ArrayList<>();
            final int upper = Math.min(hits.scoreDocs.length, offset + limit);
            for (int i = offset; i < upper; i++) {
                items.add(toSummary(searcher, namespaceId, hits.scoreDocs[i]));
            }
            return new DocumentPage(items, total);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to list documents in " + namespaceId, e);
        } finally {
            if (searcher != null) {
                try {
                    ctx.release(searcher);
                } catch (IOException ignored) {
                    // best-effort release
                }
            }
        }
    }

    private DocumentSummary toSummary(final IndexSearcher searcher,
                                      final String namespaceId,
                                      final ScoreDoc scoreDoc) throws IOException {
        final Document doc = searcher.storedFields().document(scoreDoc.doc);
        final String parentId = doc.get(LuceneFields.PARENT_ID);
        final String id = parentId != null ? parentId : doc.get(LuceneFields.ID);
        final String title = doc.get(LuceneFields.TITLE);
        final String content = doc.get(LuceneFields.CONTENT);
        final String indexedAtRaw = doc.get(LuceneFields.INDEXED_AT_EPOCH);
        final Instant indexedAt = indexedAtRaw == null
            ? null
            : Instant.ofEpochMilli(Long.parseLong(indexedAtRaw));
        return new DocumentSummary(
            id,
            namespaceId,
            title == null ? "(no title)" : title,
            content == null ? "" : truncate(content),
            indexedAt
        );
    }

    private String truncate(final String text) {
        return text.length() <= MAX_SNIPPET_LENGTH
            ? text
            : text.substring(0, MAX_SNIPPET_LENGTH) + "...";
    }
}
