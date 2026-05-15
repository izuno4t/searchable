package com.searchable.core.infrastructure.lucene;

import com.searchable.core.domain.search.SearchHit;
import com.searchable.core.domain.search.SearchRequest;
import com.searchable.core.domain.search.SearchResult;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TokenSources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes full-text search against a namespace's Lucene index.
 */
public final class LuceneFullTextSearcher {

    private static final String HL_PRE = "<mark>";
    private static final String HL_POST = "</mark>";

    private final LuceneIndexProvider provider;
    private final LuceneDocumentMapper mapper;

    public LuceneFullTextSearcher(final LuceneIndexProvider provider) {
        this(provider, new LuceneDocumentMapper());
    }

    public LuceneFullTextSearcher(final LuceneIndexProvider provider,
                                  final LuceneDocumentMapper mapper) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    public SearchResult search(final String namespaceId, final SearchRequest request) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        final long start = System.nanoTime();

        final LuceneIndexContext ctx = provider.getOrCreate(namespaceId);
        IndexSearcher searcher = null;
        try {
            searcher = ctx.acquireSearcher();
            final Analyzer analyzer = ctx.analyzer();
            final Query query = parseQuery(analyzer, request.query());
            final int topN = request.pagination().offset() + request.pagination().limit();
            final TopDocs hits = searcher.search(query, Math.max(topN, 1));

            final List<SearchHit> result = collectHits(searcher, hits, analyzer, query,
                request, namespaceId);
            final double maxScore = hits.scoreDocs.length == 0 ? 0.0 : hits.scoreDocs[0].score;
            final long tookMs = (System.nanoTime() - start) / 1_000_000;
            return new SearchResult(result, hits.totalHits.value(), maxScore, Map.of(), tookMs);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to execute search on namespace " + namespaceId, e);
        } finally {
            try {
                if (searcher != null) {
                    ctx.release(searcher);
                }
            } catch (IOException ignored) {
                // best-effort release
            }
        }
    }

    private Query parseQuery(final Analyzer analyzer, final String queryText) throws Exception {
        final QueryParser parser = new QueryParser(LuceneFields.CONTENT, analyzer);
        parser.setDefaultOperator(QueryParser.Operator.OR);
        return parser.parse(QueryParser.escape(queryText));
    }

    private List<SearchHit> collectHits(final IndexSearcher searcher,
                                        final TopDocs hits,
                                        final Analyzer analyzer,
                                        final Query query,
                                        final SearchRequest request,
                                        final String namespaceId) throws Exception {
        final List<SearchHit> result = new ArrayList<>();
        final int offset = request.pagination().offset();
        final int limit = request.pagination().limit();
        final int upper = Math.min(hits.scoreDocs.length, offset + limit);
        final Highlighter highlighter = request.options().highlightEnabled()
            ? new Highlighter(new SimpleHTMLFormatter(HL_PRE, HL_POST), new QueryScorer(query))
            : null;

        final IndexReader reader = searcher.getIndexReader();
        for (int i = offset; i < upper; i++) {
            final ScoreDoc scoreDoc = hits.scoreDocs[i];
            final org.apache.lucene.document.Document doc =
                searcher.storedFields().document(scoreDoc.doc);
            // PARENT_ID is the domain document id (same across all chunks);
            // fall back to chunk-level ID for indexes written before chunking.
            final String parentId = doc.get(LuceneFields.PARENT_ID);
            final String id = parentId != null ? parentId : doc.get(LuceneFields.ID);
            final String title = doc.get(LuceneFields.TITLE);
            final String content = doc.get(LuceneFields.CONTENT);
            final Map<String, Object> metadata = mapper.deserializeMetadata(
                doc.get(LuceneFields.METADATA_JSON));

            final Map<String, List<String>> highlights = highlighter == null
                ? Map.of()
                : buildHighlights(highlighter, analyzer, reader, scoreDoc.doc, content);

            result.add(new SearchHit(id, namespaceId, title, content, scoreDoc.score,
                highlights, metadata));
        }
        return result;
    }

    private Map<String, List<String>> buildHighlights(final Highlighter highlighter,
                                                      final Analyzer analyzer,
                                                      final IndexReader reader,
                                                      final int docId,
                                                      final String content) throws Exception {
        if (content == null || content.isEmpty()) {
            return Map.of();
        }
        final TokenStream stream = TokenSources.getTokenStream(LuceneFields.CONTENT,
            reader.termVectors().get(docId), content, analyzer, -1);
        final String fragment = highlighter.getBestFragment(stream, content);
        if (fragment == null) {
            return Map.of();
        }
        final Map<String, List<String>> highlights = new LinkedHashMap<>();
        highlights.put(LuceneFields.CONTENT, List.of(fragment));
        return highlights;
    }
}
