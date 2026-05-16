# Plugin Verification Procedure

The simplest end-to-end check that the plugin can load via `ServiceLoader`
and actually talk to an S3 endpoint. Uses MinIO so no AWS account is
required.

Prerequisites: Docker, Maven, Java 21.

## 1. Start MinIO (≈ 20s)

```bash
docker run -d --name searchable-minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

## 2. Create a bucket and upload sample documents

The MinIO image ships with the `mc` client.

```bash
docker exec searchable-minio sh -c '
  mc alias set local http://localhost:9000 minioadmin minioadmin >/dev/null &&
  mc mb -p local/test-bucket &&
  echo "# Document 1\nHello searchable." | mc pipe local/test-bucket/docs/one.md &&
  echo "# Document 2\nSecond entry."     | mc pipe local/test-bucket/docs/two.md
'
```

## 3. Build the plugin

```bash
mvn -f examples/plugin-datasource-s3/pom.xml -DskipTests package
```

## 4. Run the verifier

```bash
AWS_ACCESS_KEY_ID=minioadmin \
AWS_SECRET_ACCESS_KEY=minioadmin \
S3_ENDPOINT=http://localhost:9000 \
S3_BUCKET=test-bucket \
S3_PREFIX=docs/ \
S3_REGION=us-east-1 \
mvn -B -f examples/plugin-datasource-s3/pom.xml exec:java
```

Expected output (key fields only):

```text
plugin: s3
config: {bucket=test-bucket, region=us-east-1, prefix=docs/, endpointOverride=http://localhost:9000}
---
[1] s3://test-bucket/docs/one.md
  title=one.md  bytes=29  hash=...
[2] s3://test-bucket/docs/two.md
  title=two.md  bytes=24  hash=...
---
fetched 2 document(s)
```

What this proves:

- `META-INF/services/io.searchable.plugin.DataSourcePlugin` is on the
  classpath and `ServiceLoader` discovers `S3DataSourcePlugin`
- Configuration from `PluginContext.config()` reaches the plugin
- The AWS SDK actually talks to a live S3-compatible endpoint
- `Stream<PluginDocument>` is fully consumable and the underlying
  `S3Client` is closed (no resource leak warning)

## 5. Cleanup

```bash
docker rm -f searchable-minio
```

## Troubleshooting

| 症状 | 原因 / 対処 |
| --- | --- |
| `S3 plugin not found via ServiceLoader` | `META-INF/services/io.searchable.plugin.DataSourcePlugin` が JAR に含まれていない。`mvn package` をやり直す |
| `AccessDeniedException` / `SignatureDoesNotMatch` | `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` が未設定または不一致 |
| `Unable to execute HTTP request` | MinIO コンテナが未起動、または `S3_ENDPOINT` のポート不一致 |
| `NoSuchBucket` | バケット未作成。手順 2 を確認 |
