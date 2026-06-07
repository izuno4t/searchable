package poc;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TASK-123 PoC: 10万件のベクトルインデックスで kNN 検索性能を計測。
 *
 * <p>ハッシュベース埋め込み（384次元）を用いて、HNSW + DOT_PRODUCT で
 * 検索した場合のレイテンシ分布を測定。
 */
public final class VectorSearchPerformanceTest {

    private static final int DOC_COUNT = 100_000;
    private static final int DIMENSION = 384;
    private static final int WARMUP_QUERIES = 100;
    private static final int MEASURED_QUERIES = 1_000;
    private static final int TOP_K = 10;

    private static final String[] TOPICS = {
        "形態素解析", "全文検索", "ベクトル検索", "ハイブリッド検索", "Lucene",
        "Kuromoji", "Sudachi", "BM25", "HNSW", "Onnx Runtime",
        "Namespace", "プラグイン", "REST API", "Spring Boot", "Java"
    };

    public static void main(final String[] args) throws Exception {
        final Path indexDir = Files.createTempDirectory("lucene-vec-perf-");
        try (Directory directory = new MMapDirectory(indexDir);
             Analyzer analyzer = new JapaneseAnalyzer()) {

            final long indexMs = indexDocuments(directory, analyzer);
            System.out.printf("[index] %,d docs (dim=%d) indexed in %,d ms%n",
                DOC_COUNT, DIMENSION, indexMs);

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                final IndexSearcher searcher = new IndexSearcher(reader);

                System.out.println("[warmup] " + WARMUP_QUERIES + " queries...");
                runQueries(searcher, WARMUP_QUERIES);

                System.out.println("[measure] " + MEASURED_QUERIES + " queries...");
                final long[] latencies = runQueries(searcher, MEASURED_QUERIES);
                printStats(latencies);
            }
        } finally {
            deleteRecursively(indexDir);
        }
    }

    private static long indexDocuments(final Directory directory,
                                       final Analyzer analyzer) throws Exception {
        final IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setRAMBufferSizeMB(512);
        final long start = System.nanoTime();
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (int i = 0; i < DOC_COUNT; i++) {
                final Document doc = new Document();
                doc.add(new StringField("id", "doc-" + i, Field.Store.YES));
                doc.add(new TextField("content", buildContent(i), Field.Store.YES));
                doc.add(new KnnFloatVectorField("vector",
                    embed("doc-" + i + " " + TOPICS[i % TOPICS.length]),
                    VectorSimilarityFunction.DOT_PRODUCT));
                writer.addDocument(doc);
            }
            writer.commit();
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static String buildContent(final int i) {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        return TOPICS[i % TOPICS.length] + " #" + i + " "
            + TOPICS[rnd.nextInt(TOPICS.length)] + " "
            + TOPICS[rnd.nextInt(TOPICS.length)];
    }

    private static long[] runQueries(final IndexSearcher searcher, final int count)
            throws Exception {
        final long[] latencies = new long[count];
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            final String queryText = TOPICS[rnd.nextInt(TOPICS.length)] + " query #" + i;
            final float[] queryVector = embed(queryText);
            final KnnFloatVectorQuery query = new KnnFloatVectorQuery(
                "vector", queryVector, TOP_K);
            final long start = System.nanoTime();
            final TopDocs hits = searcher.search(query, TOP_K);
            latencies[i] = (System.nanoTime() - start) / 1_000_000;
            if (hits.totalHits.value() == 0) {
                throw new IllegalStateException("Unexpected zero hits");
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
        final long over500 = Arrays.stream(sorted).filter(v -> v > 500L).count();

        System.out.println();
        System.out.println("=== Vector Search Performance (10万件・dim=" + DIMENSION + ") ===");
        System.out.printf(Locale.ROOT, "queries: %,d%n", sorted.length);
        System.out.printf(Locale.ROOT, "min   : %,d ms%n", min);
        System.out.printf(Locale.ROOT, "avg   : %.2f ms%n", avg);
        System.out.printf(Locale.ROOT, "p50   : %,d ms%n", p50);
        System.out.printf(Locale.ROOT, "p95   : %,d ms%n", p95);
        System.out.printf(Locale.ROOT, "p99   : %,d ms%n", p99);
        System.out.printf(Locale.ROOT, "max   : %,d ms%n", max);
        System.out.printf(Locale.ROOT, "over 500ms: %,d / %,d%n", over500, sorted.length);
    }

    private static float[] embed(final String text) {
        final byte[] base = sha256(text.getBytes(StandardCharsets.UTF_8));
        final float[] vector = new float[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            final byte b = base[i % base.length];
            vector[i] = (b & 0xFF) / 128.0f - 1.0f;
        }
        double sum = 0.0;
        for (final float v : vector) {
            sum += (double) v * v;
        }
        final double norm = Math.sqrt(sum);
        if (norm > 0.0) {
            for (int i = 0; i < DIMENSION; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }

    private static byte[] sha256(final byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteRecursively(final Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // best-effort
                    }
                });
        }
    }
}
