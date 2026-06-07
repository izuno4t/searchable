package poc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

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
 * TASK-003 / TASK-008 PoC: 10万件の合成日本語コーパスで全文検索性能を計測する
 * JMH ベンチマーク。
 *
 * <p>warm (定常状態) と cold (新規 JVM での初回投入) の両モードを単一ハーネスで
 * 計測する。
 *
 * <ul>
 *   <li>{@link #warmQuery} — {@link Mode#SampleTime} で p50/p95/p99/p99.9/max を取得</li>
 *   <li>{@link #coldQuery} — {@link Mode#SingleShotTime} で fresh JVM 初回応答を計測</li>
 * </ul>
 *
 * <p>実行例:
 * <pre>{@code
 *   ./mvnw -q package
 *   java -jar target/benchmarks.jar                  # warm + cold 両方
 *   java -jar target/benchmarks.jar SearchBenchmark.warmQuery
 *   java -jar target/benchmarks.jar SearchBenchmark.coldQuery -f 10
 *   java -jar target/benchmarks.jar -rf json -rff result.json
 * }</pre>
 */
@State(Scope.Benchmark)
public class SearchBenchmark {

    private static final int DOC_COUNT = 100_000;

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

    private Path indexDir;
    private Directory directory;
    private Analyzer analyzer;
    private DirectoryReader reader;
    private IndexSearcher searcher;
    private QueryParser parser;
    private int queryCursor;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        indexDir = Files.createTempDirectory("lucene-search-bench-");
        directory = new MMapDirectory(indexDir);
        analyzer = new JapaneseAnalyzer();

        final IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setRAMBufferSizeMB(256);
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

        reader = DirectoryReader.open(directory);
        searcher = new IndexSearcher(reader);
        parser = new QueryParser("content", analyzer);
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
    @Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
    public TopDocs warmQuery() throws Exception {
        final int idx = (queryCursor++ & 0x7FFFFFFF) % QUERY_TERMS.length;
        final Query query = parser.parse(QueryParser.escape(QUERY_TERMS[idx]));
        return searcher.search(query, 10);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1, batchSize = 1)
    @Fork(value = 5, jvmArgs = {"-Xms2g", "-Xmx2g"})
    public TopDocs coldQuery() throws Exception {
        final Query query = parser.parse(QueryParser.escape(QUERY_TERMS[0]));
        return searcher.search(query, 10);
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
            .include(SearchBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
