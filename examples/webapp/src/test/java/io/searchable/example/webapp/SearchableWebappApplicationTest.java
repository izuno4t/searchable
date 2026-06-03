package io.searchable.example.webapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke test for the embedded Searchable webapp (TASK-118).
 *
 * <p>Confirms that the application context boots with the in-memory H2
 * backend, the search page renders, and the query parameter is round-tripped.
 */
@SpringBootTest(
    classes = SearchableWebappApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
    "searchable.data-directory=${java.io.tmpdir}/searchable-webapp-test-${random.uuid}",
    "searchable.persistence.url=jdbc:h2:mem:webapp-${random.uuid};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.ingest.enabled=false"
})
class SearchableWebappApplicationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void homePageRendersSearchForm() {
        final ResponseEntity<String> r = rest.getForEntity(url("/"), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("Searchable Webapp").contains("Search");
    }

    @Test
    void queryParameterIsEchoedBack() {
        final ResponseEntity<String> r = rest.getForEntity(url("/?q=hello"), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("hello");
    }

    private String url(final String path) {
        // Distinct token to keep tests from sharing per-test counters.
        COUNTER.incrementAndGet();
        return "http://localhost:" + port + path;
    }
}
