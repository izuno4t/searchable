package io.searchable.example.plugin.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.searchable.plugin.DataSourcePlugin;
import io.searchable.plugin.PluginContext;
import io.searchable.plugin.PluginDocument;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

@ExtendWith(MockitoExtension.class)
class S3DataSourcePluginTest {

    @Test
    void serviceLoaderDiscoversPlugin() {
        final List<DataSourcePlugin> plugins = ServiceLoader.load(DataSourcePlugin.class)
            .stream()
            .map(ServiceLoader.Provider::get)
            .toList();

        assertThat(plugins)
            .extracting(DataSourcePlugin::name)
            .contains("s3");
    }

    @Test
    void fetchYieldsDocumentsForListedObjectsAndClosesClient() {
        final S3Client client = mock(S3Client.class);
        final S3Object obj = S3Object.builder()
            .key("docs/manual.md")
            .size(42L)
            .lastModified(Instant.parse("2026-05-01T10:00:00Z"))
            .eTag("\"abc123\"")
            .build();

        final ListObjectsV2Iterable paginator = mock(ListObjectsV2Iterable.class);
        when(client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(paginator);
        when(paginator.contents()).thenReturn((SdkIterable<S3Object>) () -> List.of(obj).iterator());

        final byte[] body = "# Title\nHello".getBytes(StandardCharsets.UTF_8);
        final GetObjectResponse response = GetObjectResponse.builder()
            .contentType("text/markdown")
            .build();
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenReturn(ResponseBytes.fromByteArray(response, body));

        final S3DataSourcePlugin plugin = new S3DataSourcePlugin(() -> client);
        final PluginContext context = new PluginContext("ns-1", Map.of(
            "bucket", "my-bucket",
            "region", "ap-northeast-1",
            "prefix", "docs/"
        ));

        try (Stream<PluginDocument> stream = plugin.fetch(context)) {
            final List<PluginDocument> docs = stream.toList();

            assertThat(docs).hasSize(1);
            final PluginDocument doc = docs.get(0);
            assertThat(doc.id()).isEqualTo("docs/manual.md");
            assertThat(doc.title()).isEqualTo("manual.md");
            assertThat(doc.content()).isEqualTo("# Title\nHello");
            assertThat(doc.sourceType()).isEqualTo("s3");
            assertThat(doc.sourceLocation()).isEqualTo("s3://my-bucket/docs/manual.md");
            assertThat(doc.contentHash()).hasSize(64);
            assertThat(doc.metadata())
                .containsEntry("bucket", "my-bucket")
                .containsEntry("key", "docs/manual.md")
                .containsEntry("etag", "\"abc123\"")
                .containsEntry("contentType", "text/markdown");
        }

        verify(client, times(1)).close();
    }

    @Test
    void emptyObjectsAreSkipped() {
        final S3Client client = mock(S3Client.class);
        final S3Object empty = S3Object.builder().key("docs/empty.md").size(0L).build();

        final ListObjectsV2Iterable paginator = mock(ListObjectsV2Iterable.class);
        when(client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(paginator);
        when(paginator.contents()).thenReturn((SdkIterable<S3Object>) () -> List.of(empty).iterator());

        final S3DataSourcePlugin plugin = new S3DataSourcePlugin(() -> client);
        try (Stream<PluginDocument> stream = plugin.fetch(
                new PluginContext("ns-1", Map.of("bucket", "b")))) {
            assertThat(stream).isEmpty();
        }
    }

    @Test
    void missingBucketIsRejected() {
        final S3DataSourcePlugin plugin = new S3DataSourcePlugin(() -> mock(S3Client.class));
        assertThatThrownBy(() -> plugin.fetch(new PluginContext("ns-1", Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bucket");
    }
}
