package io.searchable.example.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the REST API meets the search-latency target (&lt;500 ms) for
 * a moderately sized corpus.
 *
 * <p>Uses 5,000 synthetic documents to keep the unit-test wall-clock short.
 * The underlying Lucene engine was independently verified at 100,000 docs
 * in TASK-003; the REST overhead adds only the HTTP marshalling cost.
 */
@SearchableSpringBootTest
@TestPropertySource(properties = {
    "searchable.data-directory=./build/perf-test",
    "searchable.persistence.url=jdbc:h2:mem:perf-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/perf-test/indexes"
})
class SearchPerformanceIntegrationTest {

    private static final int DOC_COUNT = 5_000;
    private static final int BATCH_SIZE = 500;
    private static final int QUERY_COUNT = 50;

    private static final String[] TOPICS = {
        "形態素解析", "全文検索", "ベクトル検索", "Lucene", "Kuromoji",
        "Namespace", "プラグイン", "REST API", "Spring Boot", "Java 21"
    };

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void setUp() throws Exception {
        final Map<String, Object> ns = Map.of("id", "perf-ns", "name", "Perf");
        mvc.perform(post("/api/v1/namespaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ns)))
            .andExpect(status().isCreated());

        for (int batch = 0; batch < DOC_COUNT / BATCH_SIZE; batch++) {
            final List<Map<String, Object>> docs = new ArrayList<>(BATCH_SIZE);
            for (int i = 0; i < BATCH_SIZE; i++) {
                final int seq = batch * BATCH_SIZE + i;
                docs.add(Map.of(
                    "id", "doc-" + seq,
                    "title", TOPICS[seq % TOPICS.length] + " 解説 #" + seq,
                    "content", buildContent(seq),
                    "metadata", Map.of("seq", seq)
                ));
            }
            final Map<String, Object> body = Map.of("namespaceId", "perf-ns", "documents", docs);
            mvc.perform(post("/api/v1/index/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
        }
    }

    private String buildContent(final int seq) {
        final ThreadLocalRandom r = ThreadLocalRandom.current();
        final StringBuilder sb = new StringBuilder();
        sb.append("本文 #").append(seq).append("。");
        for (int j = 0; j < 5; j++) {
            sb.append(TOPICS[r.nextInt(TOPICS.length)]).append(" を Java 21 で実装する。");
        }
        return sb.toString();
    }

    @Test
    void searchLatencyWellUnder500ms() throws Exception {
        final long[] latencies = new long[QUERY_COUNT];
        for (int i = 0; i < QUERY_COUNT; i++) {
            final String query = TOPICS[i % TOPICS.length];
            final long start = System.nanoTime();
            final MvcResult result = mvc.perform(post("/api/v1/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of(
                        "query", query,
                        "namespaceIds", List.of("perf-ns")))))
                .andExpect(status().isOk())
                .andReturn();
            latencies[i] = (System.nanoTime() - start) / 1_000_000;
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
        }

        Arrays.sort(latencies);
        final long p50 = latencies[latencies.length / 2];
        final long p95 = latencies[(int) (latencies.length * 0.95)];
        final long max = latencies[latencies.length - 1];

        System.out.printf(Locale.ROOT,
            "REST search latency over %,d queries (%,d docs): p50=%dms, p95=%dms, max=%dms%n",
            QUERY_COUNT, DOC_COUNT, p50, p95, max);

        assertThat(p95).as("p95 latency should remain under 500ms target").isLessThan(500L);
        assertThat(max).as("max latency should remain under 1000ms").isLessThan(1000L);
    }
}
