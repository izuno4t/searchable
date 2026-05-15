package poc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TASK-002 PoC: H2 Database で10万件規模のメタデータ操作性能を計測。
 */
public final class H2Benchmark {

    private static final int RECORD_COUNT = 100_000;
    private static final int QUERY_COUNT = 1_000;
    private static final int UPDATE_COUNT = 1_000;
    private static final int BATCH_SIZE = 1_000;

    public static void main(final String[] args) throws Exception {
        final Path tempDir = Files.createTempDirectory("h2-bench-");
        final String url = "jdbc:h2:" + tempDir.resolve("metadata") + ";MODE=PostgreSQL";

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            conn.setAutoCommit(false);

            createSchema(conn);
            final long insertMs = bulkInsert(conn);
            final long selectAllMs = selectAll(conn);
            final long selectByIdMs = selectById(conn);
            final long updateMs = updateRandom(conn);

            System.out.println();
            System.out.println("=== H2 Benchmark Result ===");
            System.out.printf("INSERT %,d records (batch=%d)   : %,d ms%n",
                RECORD_COUNT, BATCH_SIZE, insertMs);
            System.out.printf("SELECT COUNT(*) %,d records      : %,d ms%n",
                RECORD_COUNT, selectAllMs);
            System.out.printf("SELECT BY PK x %,d                : %,d ms (avg %.3f ms/q)%n",
                QUERY_COUNT, selectByIdMs, (double) selectByIdMs / QUERY_COUNT);
            System.out.printf("UPDATE BY PK x %,d                : %,d ms (avg %.3f ms/q)%n",
                UPDATE_COUNT, updateMs, (double) updateMs / UPDATE_COUNT);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void createSchema(final Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE namespaces (
                    id VARCHAR(64) PRIMARY KEY,
                    name VARCHAR(256) NOT NULL,
                    architecture VARCHAR(32) NOT NULL,
                    search_strategy VARCHAR(32) NOT NULL,
                    config_json CLOB,
                    document_count BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        conn.commit();
    }

    private static long bulkInsert(final Connection conn) throws Exception {
        final String sql = """
            INSERT INTO namespaces
                (id, name, architecture, search_strategy, config_json, document_count)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        final long start = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < RECORD_COUNT; i++) {
                ps.setString(1, "ns-" + i);
                ps.setString(2, "Namespace " + i);
                ps.setString(3, i % 3 == 0 ? "FULL_TEXT" : (i % 3 == 1 ? "VECTOR" : "HYBRID"));
                ps.setString(4, i % 2 == 0 ? "SEQUENTIAL" : "PARALLEL");
                ps.setString(5, "{\"langs\":[\"ja\"]}");
                ps.setLong(6, ThreadLocalRandom.current().nextLong(0, 100_000));
                ps.addBatch();
                if ((i + 1) % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long selectAll(final Connection conn) throws Exception {
        final long start = System.nanoTime();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM namespaces")) {
            rs.next();
            final long count = rs.getLong(1);
            if (count != RECORD_COUNT) {
                throw new IllegalStateException("Expected " + RECORD_COUNT + " but " + count);
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long selectById(final Connection conn) throws Exception {
        final String sql = "SELECT name, architecture FROM namespaces WHERE id = ?";
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final long start = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < QUERY_COUNT; i++) {
                ps.setString(1, "ns-" + rnd.nextInt(RECORD_COUNT));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    rs.getString(1);
                }
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long updateRandom(final Connection conn) throws Exception {
        final String sql = "UPDATE namespaces SET document_count = ?, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final long start = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < UPDATE_COUNT; i++) {
                ps.setLong(1, rnd.nextLong(0, 1_000_000));
                ps.setString(2, "ns-" + rnd.nextInt(RECORD_COUNT));
                ps.executeUpdate();
            }
            conn.commit();
        }
        return (System.nanoTime() - start) / 1_000_000;
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
