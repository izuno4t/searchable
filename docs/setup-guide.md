# Searchable - セットアップガイド

Phase 1 構成（全文検索コア + REST API）のセットアップ手順。

## 1. 必要環境

| 項目 | バージョン |
| --- | --- |
| Java | 21 以上 |
| Maven | 3.9 以上 |
| OS | macOS / Linux / Windows |
| メモリ | 1GB 以上推奨 |
| ディスク | データ量に応じて確保 |

## 2. ビルド

```bash
git clone <repository-url>
cd searchable
mvn -B clean package
```

主要な生成物:

- `searchable-plugins/target/searchable-plugins-1.0.0-SNAPSHOT.jar`
- `searchable-core/target/searchable-core-1.0.0-SNAPSHOT.jar`
- `searchable-api/target/searchable-api-1.0.0-SNAPSHOT.jar`
  （Spring Boot fat jar、~37MB）

## 3. 設定

REST API サーバーの設定は
`searchable-api/src/main/resources/application.properties` に記述する。
本番運用時は、外部の `application.properties` で上書きする。

```properties
server.port=8080

searchable.data-directory=./data
searchable.persistence.type=H2
searchable.persistence.url=jdbc:h2:./data/metadata;MODE=PostgreSQL
searchable.persistence.username=sa
searchable.persistence.password=
searchable.index.directory=./data/indexes
searchable.global.default-architecture=FULL_TEXT
searchable.global.default-search-strategy=SEQUENTIAL
searchable.global.default-search-order=FULL_TEXT_FIRST
```

### プラグインを利用する場合

プラグインJARを配置するディレクトリを設定する。

```properties
searchable.plugins.directory=./plugins
```

サンプル: `examples/filesystem-plugin/` を参照。

## 4. 起動

### スタンドアロンサーバーモード

```bash
java -jar searchable-api/target/searchable-api-1.0.0-SNAPSHOT.jar
```

外部設定ファイルを指定する場合:

```bash
java -jar searchable-api/target/searchable-api-1.0.0-SNAPSHOT.jar \
  --spring.config.location=/path/to/application.properties
```

### 起動確認

```bash
curl http://localhost:8080/api/v1/namespaces
# → {"namespaces":[],"total":0}
```

## 5. 基本操作

### Namespace 作成

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "project-a",
    "name": "Project A",
    "config": {"architecture": "FULL_TEXT"}
  }'
```

### ドキュメント登録

```bash
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "project-a",
    "document": {
      "id": "doc-1",
      "title": "Searchable について",
      "content": "Searchable は日本語形態素解析に対応した全文検索ライブラリです。"
    }
  }'
```

### バッチ登録

```bash
curl -X POST http://localhost:8080/api/v1/index/batch \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "project-a",
    "documents": [
      {"id": "doc-1", "title": "...", "content": "..."},
      {"id": "doc-2", "title": "...", "content": "..."}
    ]
  }'
```

### 検索

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "形態素解析",
    "namespaceIds": ["project-a"],
    "options": {"highlightEnabled": true, "maxResults": 10}
  }'
```

### Namespace 削除

```bash
curl -X DELETE http://localhost:8080/api/v1/namespaces/project-a
```

## 6. データの構成

```text
./data/
├── metadata.mv.db        # H2 メタデータDB
└── indexes/
    └── <namespace-id>/    # Namespace ごとの Lucene インデックス
        ├── segments_N
        ├── *.cfs
        └── ...
```

## 7. ログ

`logback-spring.xml` を上書きしてカスタマイズ可能。
デフォルトでは標準出力に出力し、ログレベルは `com.searchable=INFO`。

## 8. テスト実行

```bash
# 全モジュール
mvn -B test

# searchable-core のみ
mvn -B -pl searchable-core -am test

# searchable-api のみ
mvn -B -pl searchable-api -am test
```

## 9. トラブルシューティング

### ポートが使用中

```properties
server.port=8081
```

### インデックスを再構築したい

```bash
curl -X POST http://localhost:8080/api/v1/index/rebuild \
  -H 'Content-Type: application/json' \
  -d '{"namespaceId": "project-a"}'
```

### 設定変更後の挙動が反映されない

`application.properties` の場所を確認し、サーバーを再起動する。

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 1
