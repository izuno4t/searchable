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
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TASK-003 PoC: 10万件の合成日本語コーパスで検索性能（500ms目標）を計測。
 */
public final class SearchPerformanceTest {

    private static final int DOC_COUNT = 100_000;
    private static final int WARMUP_QUERIES = 100;
    private static final int MEASURED_QUERIES = 1_000;

    private static final String[] TOPICS = {
        "形態素解析", "全文検索", "ベクトル検索", "ハイブリッド検索", "Lucene",
        "Kuromoji", "Sudachi", "BM25", "HNSW", "Onnx Runtime",
        "Namespace", "プラグイン", "REST API", "Spring Boot", "Java",
        "日本語処理", "辞書", "ストップワード", "正規化", "ハイライト",
        "インデックス", "永続化", "バックアップ", "リストア", "管理UI"
    };

    private static final String[] QUERY_TERMS = {
        "Lucene", "形態素解析", "ベクトル検索", "Kuromoji", "Namespace",
        "REST API", "BM25", "Spring Boot", "プラグイン", "辞書",
        "Apache Lucene Kuromoji", "Java 検索 ライブラリ",
        "ベクトル ハイブリッド", "全文 検索 エンジン"
    };

    public static void main(final String[] args) throws Exception {
        final Path indexDir = Files.createTempDirectory("lucene-perf-");
        try (Directory directory = new MMapDirectory(indexDir);
             Analyzer analyzer = new JapaneseAnalyzer()) {

            final long indexMs = indexDocuments(directory, analyzer);
            System.out.printf("[index] %,d docs indexed in %,d ms%n", DOC_COUNT, indexMs);

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                final IndexSearcher searcher = new IndexSearcher(reader);
                final QueryParser parser = new QueryParser("content", analyzer);

                System.out.println("[warmup] running " + WARMUP_QUERIES + " warmup queries...");
                runQueries(searcher, parser, WARMUP_QUERIES);

                System.out.println("[measure] running " + MEASURED_QUERIES + " queries...");
                final long[] latencies = runQueries(searcher, parser, MEASURED_QUERIES);

                printStats(latencies);
            }
        } finally {
            deleteRecursively(indexDir);
        }
    }

    private static long indexDocuments(final Directory directory,
                                       final Analyzer analyzer) throws Exception {
        final IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setRAMBufferSizeMB(256);
        final long start = System.nanoTime();
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (int i = 0; i < DOC_COUNT; i++) {
                final Document doc = new Document();
                doc.add(new StringField("id", "doc-" + i, Field.Store.YES));
                doc.add(new TextField("title", buildTitle(i), Field.Store.YES));
                doc.add(new TextField("content", buildContent(i), Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static String buildTitle(final int i) {
        final String topic = TOPICS[i % TOPICS.length];
        return topic + "に関する文書 #" + i;
    }

    private static String buildContent(final int i) {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final StringBuilder sb = new StringBuilder(512);
        sb.append("これは ").append(TOPICS[i % TOPICS.length]).append(" に関する文書です。 ");
        for (int j = 0; j < 6; j++) {
            sb.append(TOPICS[rnd.nextInt(TOPICS.length)]).append("、");
            sb.append("Java 21 で実装される ");
            sb.append(TOPICS[rnd.nextInt(TOPICS.length)]).append(" の解説。 ");
        }
        sb.append("詳細は ").append(TOPICS[rnd.nextInt(TOPICS.length)]).append(" を参照。");
        return sb.toString();
    }

    private static long[] runQueries(final IndexSearcher searcher,
                                     final QueryParser parser,
                                     final int count) throws Exception {
        final long[] latencies = new long[count];
        for (int i = 0; i < count; i++) {
            final String q = QUERY_TERMS[i % QUERY_TERMS.length];
            final Query query = parser.parse(QueryParser.escape(q));
            final long start = System.nanoTime();
            final TopDocs hits = searcher.search(query, 10);
            latencies[i] = (System.nanoTime() - start) / 1_000_000;
            if (hits.totalHits.value() == 0) {
                throw new IllegalStateException("Unexpected zero hits for: " + q);
            }
        }
        return latencies;
    }

    private static void printStats(final long[] latencies) {
        final long[] sorted = latencies.clone();
        Arrays.sort(sorted);
        final long min = sorted[0];
        final long p50 = sorted[(int) (sorted.length * 0.50)];
        final long p95 = sorted[(int) (sorted.length * 0.95)];
        final long p99 = sorted[(int) (sorted.length * 0.99)];
        final long max = sorted[sorted.length - 1];
        final double avg = Arrays.stream(sorted).average().orElse(0);

        System.out.println();
        System.out.println("=== Search Performance Result (10万件) ===");
        System.out.printf(Locale.ROOT, "queries: %,d%n", sorted.length);
        System.out.printf(Locale.ROOT, "min   : %,d ms%n", min);
        System.out.printf(Locale.ROOT, "avg   : %.2f ms%n", avg);
        System.out.printf(Locale.ROOT, "p50   : %,d ms%n", p50);
        System.out.printf(Locale.ROOT, "p95   : %,d ms%n", p95);
        System.out.printf(Locale.ROOT, "p99   : %,d ms%n", p99);
        System.out.printf(Locale.ROOT, "max   : %,d ms%n", max);

        final List<Long> exceeded = Arrays.stream(sorted).boxed()
            .filter(v -> v > 500L).toList();
        System.out.printf(Locale.ROOT, "over 500ms: %,d / %,d%n",
            exceeded.size(), sorted.length);
    }

    private static void deleteRecursively(final Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) { } });
        }
    }
}
