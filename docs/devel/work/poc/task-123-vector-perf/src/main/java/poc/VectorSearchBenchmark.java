package poc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * TASK-123 / TASK-008 PoC: 10万件のベクトルインデックス (HNSW + DOT_PRODUCT) で
 * kNN 検索性能を計測する JMH ベンチマーク。
 *
 * <p>warm (定常状態) と cold (新規 JVM での初回投入) の両モードを単一ハーネスで
 * 計測する。ハッシュベースの 384 次元埋め込みを用いるため意味的類似度は反映しないが、
 * HNSW 探索コスト自体は実モデル使用時と概ね同等になる。
 *
 * <ul>
 *   <li>{@link #warmQuery} — {@link Mode#SampleTime} で p50/p95/p99/p99.9/max を取得</li>
 *   <li>{@link #coldQuery} — {@link Mode#SingleShotTime} で fresh JVM 初回応答を計測</li>
 * </ul>
 *
 * <p>注意: インデックス構築は約 88 秒/10万件 (Apple Silicon 実測)。{@link Fork} の値を
 * 増やすと cold 計測時間が比例して伸びる。
 *
 * <p>実行例:
 * <pre>{@code
 *   ./mvnw -q package
 *   java -jar target/benchmarks.jar
 *   java -jar target/benchmarks.jar VectorSearchBenchmark.warmQuery
 *   java -jar target/benchmarks.jar VectorSearchBenchmark.coldQuery -f 5
 *   java -jar target/benchmarks.jar -rf json -rff result.json
 * }</pre>
 */
@State(Scope.Benchmark)
public class VectorSearchBenchmark {

    private static final int DOC_COUNT = 100_000;
    private static final int DIMENSION = 384;
    private static final int TOP_K = 10;

    private static final String[] TOPICS = {
        "形態素解析", "全文検索", "ベクトル検索", "ハイブリッド検索", "Lucene",
        "Kuromoji", "Sudachi", "BM25", "HNSW", "Onnx Runtime",
        "Namespace", "プラグイン", "REST API", "Spring Boot", "Java"
    };

    private Path indexDir;
    private Directory directory;
    private Analyzer analyzer;
    private DirectoryReader reader;
    private IndexSearcher searcher;
    private int queryCursor;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        indexDir = Files.createTempDirectory("lucene-vector-bench-");
        directory = new MMapDirectory(indexDir);
        analyzer = new JapaneseAnalyzer();

        final IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setRAMBufferSizeMB(512);
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

        reader = DirectoryReader.open(directory);
        searcher = new IndexSearcher(reader);
        queryCursor = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (reader != null) {
            reader.close();
        }
        if (directory != null) {
            directory.close();
        }
        if (analyzer != null) {
            analyzer.close();
        }
        if (indexDir != null) {
            deleteRecursively(indexDir);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
    @Fork(value = 1, jvmArgs = {"-Xms3g", "-Xmx3g"})
    public TopDocs warmQuery() throws Exception {
        final int i = (queryCursor++ & 0x7FFFFFFF);
        final String queryText = TOPICS[i % TOPICS.length] + " query #" + i;
        final float[] queryVector = embed(queryText);
        final KnnFloatVectorQuery query =
            new KnnFloatVectorQuery("vector", queryVector, TOP_K);
        return searcher.search(query, TOP_K);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1, batchSize = 1)
    @Fork(value = 3, jvmArgs = {"-Xms3g", "-Xmx3g"})
    public TopDocs coldQuery() throws Exception {
        final float[] queryVector = embed(TOPICS[0] + " query #0");
        final KnnFloatVectorQuery query =
            new KnnFloatVectorQuery("vector", queryVector, TOP_K);
        return searcher.search(query, TOP_K);
    }

    private static String buildContent(final int i) {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        return TOPICS[i % TOPICS.length] + " #" + i + " "
            + TOPICS[rnd.nextInt(TOPICS.length)] + " "
            + TOPICS[rnd.nextInt(TOPICS.length)];
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
                        // best-effort cleanup
                    }
                });
        }
    }

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
            .include(VectorSearchBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
