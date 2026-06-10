package io.searchable.example.api.e2e;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification against the packaged Spring Boot JAR running in
 * a separate JVM. This is the project's e2e test layer as defined in
 * docs/devel/testing/README.md: it validates packaging, startup, and the
 * REST surface as a real user would consume them — NOT the Spring
 * application context in-process.
 *
 * <p>The fixture is wired by Maven, not the test:
 * <ul>
 *   <li>build-helper-maven-plugin reserves a free TCP port and exports
 *       it as the {@code searchable.test.port} system property.</li>
 *   <li>spring-boot-maven-plugin's {@code start} goal forks the
 *       packaged JAR with {@code -Dserver.port=${searchable.test.port}}
 *       in the {@code pre-integration-test} phase.</li>
 *   <li>This class talks to it via {@link HttpClient}.</li>
 *   <li>The {@code stop} goal halts the forked JVM after
 *       {@code post-integration-test}.</li>
 * </ul>
 *
 * <p>Sequence mirrors the steps documented in
 * {@code docs/devel/testing/verify.ja.md}: 3) create namespace, 4) index
 * documents, 5) full-text query with Japanese morphology, 7) cleanup.
 * Step 6 (vector / hybrid) is intentionally skipped here because the
 * api example does not currently depend on {@code searchable-ai}; add it
 * when that wiring lands.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndVerificationIT {

    private static final int PORT = Integer.parseInt(
        System.getProperty("searchable.test.port"));
    private static final String BASE = "http://localhost:" + PORT;
    private static final String NS = "e2e-verify";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    @Test
    @Order(1)
    void step3_createsNamespace() throws Exception {
        final String body = """
            {"id":"%s","name":"e2e","config":{"architecture":"FULL_TEXT"}}
            """.formatted(NS);
        final HttpResponse<String> res = post("/api/v1/namespaces", body);
        assertThat(res.statusCode())
            .as("create namespace: %s", res.body())
            .isIn(200, 201);

        final HttpResponse<String> dup = post("/api/v1/namespaces", body);
        assertThat(dup.statusCode())
            .as("duplicate create must return 409: %s", dup.body())
            .isEqualTo(409);
    }

    @Test
    @Order(2)
    void step4_indexesJapaneseDocuments() throws Exception {
        final String body = """
            {"namespaceId":"%s","documents":[
              {"id":"doc-1","title":"ベクトル検索の概要","content":"ベクトル検索は文書とクエリを高次元のベクトル空間に埋め込み、コサイン類似度などの距離尺度で関連性の高い文書を返す手法である。"},
              {"id":"doc-2","title":"日本語形態素解析と全文検索","content":"Kuromoji や Sudachi といった形態素解析器は、日本語の文を語に切り分け、活用形を基本形に戻すことで全文検索エンジンが適切にトークンを扱えるようにする。これにより形態素解析しているという活用形を含む文に対しても、基本形のクエリでヒットできる。"},
              {"id":"doc-3","title":"ハイブリッド検索の設計","content":"ハイブリッド検索は、語彙的一致を扱う全文検索エンジンと、意味的な類似性を扱うベクトル検索エンジンを組み合わせた仕組みである。"}
            ]}
            """.formatted(NS);
        final HttpResponse<String> res = post("/api/v1/index/batch", body);
        assertThat(res.statusCode())
            .as("batch index: %s", res.body())
            .isEqualTo(200);
        assertThat(res.body())
            .as("batch index body")
            .contains("\"succeeded\":3")
            .contains("\"failed\":0");
    }

    @Test
    @Order(3)
    void step5_fullTextSearchHonoursJapaneseMorphology() throws Exception {
        // "形態素解析" appears in doc-2 only via the inflected form
        // "形態素解析しているという". A Kuromoji-backed full-text engine must
        // normalise the inflection so the base-form query still matches.
        final String body = """
            {"query":"形態素解析","namespaceIds":["%s"],"searchType":"FULL_TEXT"}
            """.formatted(NS);
        final HttpResponse<String> res = post("/api/v1/search", body);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body())
            .as("full-text hits for 形態素解析: %s", res.body())
            .contains("\"id\":\"doc-2\"");
    }

    @Test
    @Order(4)
    void step7_deleteRemovesNamespaceAndReturns404Afterwards() throws Exception {
        final HttpResponse<String> del = send("DELETE", "/api/v1/namespaces/" + NS, null);
        assertThat(del.statusCode())
            .as("delete namespace: %s", del.body())
            .isIn(200, 204);

        final HttpResponse<String> gone = send("GET", "/api/v1/namespaces/" + NS, null);
        assertThat(gone.statusCode())
            .as("get after delete must be 404: %s", gone.body())
            .isEqualTo(404);
    }

    private static HttpResponse<String> post(final String path, final String json) throws Exception {
        return send("POST", path, json);
    }

    private static HttpResponse<String> send(final String method, final String path,
                                             final String json) throws Exception {
        final HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(BASE + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json");
        switch (method) {
            case "POST"   -> b.POST(HttpRequest.BodyPublishers.ofString(json));
            case "DELETE" -> b.DELETE();
            case "GET"    -> b.GET();
            default       -> throw new IllegalArgumentException(method);
        }
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
