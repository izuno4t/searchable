package poc;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.util.List;
import java.util.Map;

/**
 * TASK-001 PoC: Apache Lucene + Kuromoji による日本語全文検索の動作確認。
 */
public final class JapaneseSearchPoc {

    private static final List<Map<String, String>> SAMPLE_DOCS = List.of(
        Map.of(
            "id", "doc-001",
            "title", "日本語形態素解析の基礎",
            "content", "形態素解析は、日本語のテキストを単語単位に分割する処理である。"
                + "Kuromojiは広く利用されている。"
        ),
        Map.of(
            "id", "doc-002",
            "title", "全文検索エンジンの選定",
            "content", "Apache Luceneは組み込み可能な全文検索ライブラリで、"
                + "Javaから直接利用できる。BM25によるランキングを標準搭載している。"
        ),
        Map.of(
            "id", "doc-003",
            "title", "ベクトル検索とハイブリッド検索",
            "content", "ベクトル検索は意味的類似度に基づく検索で、"
                + "全文検索と組み合わせるとハイブリッド検索になる。"
        ),
        Map.of(
            "id", "doc-004",
            "title", "Kuromoji の特徴",
            "content", "Kuromojiは辞書ベースの形態素解析器で、助詞や助動詞の除去、"
                + "活用形の基本形化などをサポートする。"
        ),
        Map.of(
            "id", "doc-005",
            "title", "Sudachiとの比較",
            "content", "Sudachiは高精度な形態素解析器で、A/B/Cの分割粒度を選択できる。"
                + "辞書のカスタマイズも容易である。"
        )
    );

    private static final List<String> QUERIES = List.of(
        "日本語",
        "形態素解析",
        "Lucene 検索",
        "ハイブリッド",
        "Kuromoji 辞書"
    );

    public static void main(final String[] args) throws Exception {
        try (Directory directory = new ByteBuffersDirectory();
             Analyzer analyzer = new JapaneseAnalyzer()) {

            indexDocuments(directory, analyzer);
            searchAll(directory, analyzer);
        }
    }

    private static void indexDocuments(final Directory directory,
                                       final Analyzer analyzer) throws Exception {
        final IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (final Map<String, String> doc : SAMPLE_DOCS) {
                final Document luceneDoc = new Document();
                luceneDoc.add(new StringField("id", doc.get("id"), Field.Store.YES));
                luceneDoc.add(new TextField("title", doc.get("title"), Field.Store.YES));
                luceneDoc.add(new TextField("content", doc.get("content"), Field.Store.YES));
                writer.addDocument(luceneDoc);
            }
            writer.commit();
        }
        System.out.println("[index] " + SAMPLE_DOCS.size() + " 件のドキュメントを登録しました");
        System.out.println();
    }

    private static void searchAll(final Directory directory,
                                  final Analyzer analyzer) throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            final IndexSearcher searcher = new IndexSearcher(reader);
            final QueryParser parser = new QueryParser("content", analyzer);
            final Highlighter highlighter = new Highlighter(
                new SimpleHTMLFormatter("<mark>", "</mark>"),
                new QueryScorer(parser.parse("dummy"))
            );

            for (final String queryText : QUERIES) {
                final Query query = parser.parse(QueryParser.escape(queryText));
                final long start = System.nanoTime();
                final TopDocs hits = searcher.search(query, 5);
                final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                System.out.println("[query] " + queryText
                    + "  (hits=" + hits.totalHits.value() + ", " + elapsedMs + " ms)");

                final Highlighter hl = new Highlighter(
                    new SimpleHTMLFormatter("<mark>", "</mark>"),
                    new QueryScorer(query)
                );

                for (final ScoreDoc scoreDoc : hits.scoreDocs) {
                    final Document doc = searcher.storedFields().document(scoreDoc.doc);
                    final String content = doc.get("content");
                    final String snippet = hl.getBestFragment(
                        TokenSources.getTokenStream(
                            "content", reader.termVectors().get(scoreDoc.doc),
                            content, analyzer, hl.getMaxDocCharsToAnalyze() - 1
                        ),
                        content
                    );
                    System.out.printf("  - [%s] %s  score=%.4f%n",
                        doc.get("id"), doc.get("title"), scoreDoc.score);
                    System.out.println("      " + (snippet != null ? snippet : content));
                }
                System.out.println();
            }
        }
    }
}
