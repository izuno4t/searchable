package io.searchable.example.plugin.s3;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchable.plugin.DataSourcePlugin;
import io.searchable.plugin.PluginContext;
import io.searchable.plugin.PluginDocument;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Reference {@link DataSourcePlugin} that ingests objects from an
 * S3-compatible bucket.
 *
 * <p>Configuration keys (read from {@link PluginContext#config()}):
 * <ul>
 *   <li>{@code bucket} (required): bucket name</li>
 *   <li>{@code region} (optional, default {@code us-east-1}): AWS region</li>
 *   <li>{@code prefix} (optional, default empty): key prefix to scan</li>
 *   <li>{@code endpointOverride} (optional): URI for S3-compatible endpoints
 *       such as MinIO or LocalStack</li>
 * </ul>
 *
 * <p>Authentication uses the AWS SDK default credentials provider chain.
 *
 * <p>This implementation is a reference: object content is decoded as
 * UTF-8 text. Binary formats (PDF, Office, ...) require additional
 * parsing logic that is intentionally out of scope here.
 */
public final class S3DataSourcePlugin implements DataSourcePlugin {

    private static final Logger log = LoggerFactory.getLogger(S3DataSourcePlugin.class);

    private final Supplier<S3Client> clientFactory;

    /** Production constructor — builds an {@link S3Client} per {@link #fetch(PluginContext)} call. */
    public S3DataSourcePlugin() {
        this(null);
    }

    /**
     * Constructor for tests and advanced wiring.
     *
     * @param clientFactory factory returning an {@link S3Client}; if
     *                      {@code null}, the plugin builds one from the
     *                      context configuration
     */
    public S3DataSourcePlugin(final Supplier<S3Client> clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public String name() {
        return "s3";
    }

    @Override
    public Stream<PluginDocument> fetch(final PluginContext context) {
        final S3Config s3 = S3Config.from(context.config());
        final S3Client client = clientFactory != null ? clientFactory.get() : buildClient(s3);
        try {
            final var paginator = client.listObjectsV2Paginator(
                ListObjectsV2Request.builder()
                    .bucket(s3.bucket())
                    .prefix(s3.prefix())
                    .build());

            return paginator.contents().stream()
                .filter(obj -> obj.size() != null && obj.size() > 0L)
                .map(obj -> toDocument(client, s3.bucket(), obj))
                .onClose(client::close);
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
    }

    private static S3Client buildClient(final S3Config s3) {
        final var builder = S3Client.builder().region(Region.of(s3.region()));
        if (s3.endpointOverride() != null) {
            builder.endpointOverride(URI.create(s3.endpointOverride()));
            // Path-style works with most S3-compatible stores (MinIO, LocalStack).
            builder.forcePathStyle(true);
        }
        return builder.build();
    }

    private static PluginDocument toDocument(final S3Client client,
                                             final String bucket,
                                             final S3Object obj) {
        final String key = obj.key();
        final ResponseBytes<GetObjectResponse> response = client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(key).build());

        final byte[] bytes = response.asByteArray();
        final String content = new String(bytes, StandardCharsets.UTF_8);
        final String hash = sha256(bytes);
        final String title = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        final String sourceLocation = "s3://" + bucket + "/" + key;

        final Map<String, Object> metadata = new LinkedHashMap<>();
        // `url` is the reserved metadata key for the document origin
        // (see docs/architecture.md §5.7). For S3-sourced documents the
        // canonical URI is `s3://bucket/key`. Presigned HTTP URLs are
        // intentionally NOT placed here because they are short-lived.
        metadata.put("url", sourceLocation);
        metadata.put("bucket", bucket);
        metadata.put("key", key);
        if (obj.eTag() != null) {
            metadata.put("etag", obj.eTag());
        }
        if (response.response().contentType() != null) {
            metadata.put("contentType", response.response().contentType());
        }

        log.debug("S3 object loaded: {} ({} bytes)", sourceLocation, bytes.length);

        return new PluginDocument(
            key,
            title.isBlank() ? key : title,
            content,
            "s3",
            sourceLocation,
            hash,
            obj.lastModified(),
            metadata);
    }

    private static String sha256(final byte[] bytes) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** View of the S3-related configuration keys parsed from {@link PluginContext#config()}. */
    record S3Config(String bucket, String region, String prefix, String endpointOverride) {

        static S3Config from(final Map<String, Object> config) {
            final Object bucket = config.get("bucket");
            if (bucket == null || bucket.toString().isBlank()) {
                throw new IllegalArgumentException("Missing required 'bucket' config for s3 plugin");
            }
            final String region = Objects.toString(config.getOrDefault("region", "us-east-1"));
            final String prefix = Objects.toString(config.getOrDefault("prefix", ""));
            final Object endpoint = config.get("endpointOverride");
            return new S3Config(
                bucket.toString(),
                region,
                prefix,
                endpoint == null ? null : endpoint.toString());
        }
    }
}
