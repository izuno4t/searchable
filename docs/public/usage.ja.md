# Searchable - Usage Guide

Searchable の日常的な使い方をまとめたリファレンス。
初回セットアップは [getting-started.ja.md](getting-started.ja.md) を、
全 API の網羅的な仕様は [examples/api/api-specification.ja.md](../examples/api/api-specification.ja.md) を参照する。

## 1. 利用形態の選び方

Searchable は同じコア機能を 3 つの形態で提供する。

| 形態 | こんなときに |
| --- | --- |
| **Java ライブラリ** | 既存の Java アプリに埋め込みたい / ネットワーク越しのオーバーヘッドを避けたい |
| **REST API サーバー** | 言語を問わず複数クライアントから使いたい / マイクロサービスとして切り出したい |
| **MCP サーバー** | Claude Desktop など AI クライアントからツールとして呼びたい |

複数形態の同時利用も可能。例えば検索サービスは REST、AI クライアントは MCP、と使い分ける構成が一般的。

## 2. Java ライブラリとして使う

### 2.1 初期化

```java
SearchableLibrary library = SearchableLibrary.builder()
    .configPath("/path/to/config.yaml")    // ファイルから設定
    .build();

// もしくはプログラムから直接
SearchableLibrary library = SearchableLibrary.builder()
    .dataDirectory("/path/to/data")
    .persistenceType(PersistenceType.H2)
    .globalConfig(GlobalConfig.builder()
        .defaultSearchArchitecture(SearchArchitecture.HYBRID)
        .build())
    .build();

library.start();
```

利用後は `library.shutdown()` でリソースを解放する。

### 2.2 Namespace 操作

```java
NamespaceService namespaceService = library.getNamespaceService();

Namespace namespace = namespaceService.createNamespace(
    NamespaceCreateRequest.builder()
        .id("project-a")
        .name("Project A")
        .config(NamespaceConfig.builder()
            .architecture(SearchArchitecture.HYBRID)
            .searchStrategy(SearchStrategy.PARALLEL)
            .build())
        .build());
```

### 2.3 インデックス登録

```java
IndexService indexService = library.getIndexService();

Document document = Document.builder()
    .id("doc-1")
    .title("製品マニュアル")
    .content("Searchable は日本語形態素解析に対応した全文検索ライブラリです。")
    .metadata(Map.of(
        "url", Path.of("/srv/docs/manual.md").toUri().toString(),  // 推奨: URI で origin を記録
        "category", "product",
        "lang", "ja"))
    .build();

indexService.indexDocument("project-a", document);
```

バッチ登録には `indexDocuments(namespaceId, List<Document>)` を使う。

#### `Document.metadata` の予約キー

`metadata` は自由項目だが、いくつかのキーはライブラリ・サンプル UI 側で
特別な意味を持つ。

| キー | 値 | 用途 |
| --- | --- | --- |
| `url` | **URI**(RFC 3986)、スキーム必須 | 文書の origin 参照。`file:///abs/path`、`http(s)://...`、`ftp://...`、`s3://bucket/key` 等。生パス(`/abs/path`)は禁止 |
| `contentType` | **MIME type** | 文書の元フォーマット。`text/plain` / `text/markdown` / `text/html` / `text/asciidoc` / `application/pdf` 等。Office 系 MIME も使用可。詳細は [architecture.md §5.7](architecture.md) |
| `category` | string | facet 用 |
| `lang` | string | facet 用 |
| `tags` | string or string[] | facet 用 |

`metadata.url` を入れておくと、検索結果(`SearchHit.metadata.url`)から
元文書への直リンクを生成でき、セクション単位ヒット(`SubResult`)では
`anchorUrl = url + "#heading-slug"` が自動で組み立てられる。

なお `SubResult` は **full-text 検索でのみ生成** される。ベクトル検索
単体および「ベクトル経由でヒットした文書」では `subResults` は空配列、
`anchorUrl` は無し。UI は `subResults` が空でも動くように作ること
(`SearchHit.metadata.url` だけで元文書への直リンクは作れる)。

#### metadata の保管場所

文書レベル metadata(`Document.metadata`)は **専用の metadata DB** に
1 文書 1 行で保存される(`DocumentMetadataRepository`)。Lucene のチャンク
stored field には保存されないため、チャンク数が増えても metadata 量は
線形に増えない。詳細は [architecture.md §5.7](architecture.md) を参照。

### 2.4 検索

```java
SearchService searchService = library.getSearchService();

SearchRequest request = SearchRequest.builder()
    .query("形態素解析")
    .namespaceIds(List.of("project-a"))
    .searchType(SearchType.HYBRID)
    .maxResults(10)
    .build();

SearchResult result = searchService.search(request);
result.getHits().forEach(hit ->
    System.out.printf("%s (score=%.2f)%n", hit.getTitle(), hit.getScore()));
```

非同期版は `searchAsync(request)` を使う。

## 3. REST API として使う

REST API サーバーは [`examples/api/`](../examples/api/) にリファレンス実装が
ある。詳細な起動手順と設定は [examples/api/README.md](../examples/api/README.md)
を参照。

### 3.1 基本情報

- Base URL: `http://<host>:8080/api/v1`
- Content-Type: `application/json`
- 認証: `searchable.api.key` または `SEARCHABLE_API_KEY` を設定すると
  `X-API-Key` ヘッダーが必須になる。未設定の場合は認証なしで動作。

### 3.2 Namespace を作る

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "project-a",
    "name": "Project A",
    "config": {
      "architecture": "HYBRID",
      "searchStrategy": "PARALLEL"
    }
  }'
```

一覧取得は `GET /api/v1/namespaces`、削除は `DELETE /api/v1/namespaces/{id}`。

### 3.3 ドキュメントを登録する

単件:

```bash
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "project-a",
    "document": {
      "id": "doc-1",
      "title": "...",
      "content": "..."
    }
  }'
```

バッチ:

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

### 3.4 検索する

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "形態素解析",
    "namespaceIds": ["project-a"],
    "searchType": "HYBRID",
    "options": {
      "maxResults": 10,
      "highlightEnabled": true
    }
  }'
```

レスポンスは `hits`, `totalHits`, `maxScore`, `took` を含む。
個別フィールドの詳細は [examples/api/api-specification.ja.md](../examples/api/api-specification.ja.md) 参照。

### 3.5 主なエンドポイント

| メソッド | パス | 用途 |
| --- | --- | --- |
| `POST` | `/api/v1/search` | 検索実行 |
| `GET` / `POST` / `DELETE` | `/api/v1/namespaces[/{id}]` | Namespace CRUD |
| `GET` / `PUT` | `/api/v1/namespaces/{id}/config` | Namespace 設定 |
| `POST` / `PUT` / `DELETE` | `/api/v1/index/documents[/{id}]` | ドキュメント CRUD |
| `POST` | `/api/v1/index/batch` | バッチ登録 |
| `POST` | `/api/v1/index/rebuild` | インデックス再構築 |
| `GET` | `/api/v1/admin/status` | システム状態 |
| `GET` | `/api/v1/admin/metrics` | メトリクス |

## 4. MCP サーバーとして使う

MCP サーバーは [`examples/mcp/`](../examples/mcp/) にリファレンス実装がある。
完全な手順は [examples/mcp/guide.ja.md](../examples/mcp/guide.ja.md) を参照。

### 4.1 起動

stdio モード (Claude Desktop など、プロセス起動型クライアント):

```bash
java -jar examples/mcp/target/mcp-example-1.0.0-SNAPSHOT.jar --mode stdio
```

SSE モード (HTTP 経由):

```bash
java -jar examples/mcp/target/mcp-example-1.0.0-SNAPSHOT.jar --mode sse --port 8080
```

### 4.2 Claude Desktop からの利用

`claude_desktop_config.json` に追記:

```json
{
  "mcpServers": {
    "searchable": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/mcp-example-1.0.0-SNAPSHOT.jar",
        "--mode",
        "stdio"
      ],
      "env": {
        "SEARCHABLE_CONFIG": "/path/to/config.yaml"
      }
    }
  }
}
```

提供ツール (`search_documents` など) の詳細は
[examples/mcp/guide.ja.md](../examples/mcp/guide.ja.md) を参照。

## 5. 検索モードの使い分け

`searchType` で 3 つのモードを切り替える。
Namespace のデフォルトを使う場合は省略可。

| モード | 概要 | 向いている用途 |
| --- | --- | --- |
| `FULL_TEXT` | 形態素解析ベースの全文検索 | キーワード一致を重視 / ログ・コード検索 |
| `VECTOR` | 埋め込みベクトルによる類似検索 | 意味的に近いものを探す / 言い換えを吸収したい |
| `HYBRID` | 両方を実行して結合 | バランス重視 / 一般的な検索ユースケース |

ハイブリッド時の実行方式は Namespace 設定の `searchStrategy` で指定する。

- `SEQUENTIAL`: 片方の結果を絞り込みに使う (`searchOrder` で順序を指定)
- `PARALLEL`: 同時実行してスコア結合

## 6. ハイライトとフィルター

検索オプションで結果整形を制御できる。

```json
{
  "query": "形態素解析",
  "namespaceIds": ["project-a"],
  "options": {
    "maxResults": 10,
    "offset": 0,
    "highlightEnabled": true
  },
  "filters": {
    "source": "manual.md"
  }
}
```

`filters` の左辺はドキュメント登録時の `metadata` キーと一致させる。

## 7. インデックスの管理

| やりたいこと | API / 操作 |
| --- | --- |
| 更新 | `PUT /api/v1/index/documents/{id}` |
| 削除 | `DELETE /api/v1/index/documents/{id}?namespaceId=...` |
| 全再構築 | `POST /api/v1/index/rebuild` |
| 状態確認 | `GET /api/v1/namespaces/{id}` の `indexMetadata` |
| バックアップ / リストア | `POST /api/v1/admin/backup` / `/api/v1/admin/restore` |

大量データの初回投入はバッチ登録 (`/index/batch`) を推奨。

## 8. プラグインを追加する

`searchable.plugins.directory` に JAR を配置すると起動時に読み込まれる。

```properties
searchable.plugins.directory=./plugins
```

プラグイン API は `searchable-plugins` モジュールで定義されている。
独自プラグインは `DataSourcePlugin` インターフェースを実装する
（[searchable-plugins/src/main/java/io/searchable/plugin/DataSourcePlugin.java](../searchable-plugins/src/main/java/io/searchable/plugin/DataSourcePlugin.java)）。

```java
public interface DataSourcePlugin {
    String getName();
    List<Document> fetchDocuments(Map<String, Object> config);
}
```

## 9. 設定リファレンス

主な設定キー。完全版は [setup-guide.md](setup-guide.md) を参照。

| キー | 既定値 | 説明 |
| --- | --- | --- |
| `server.port` | `8080` | REST API ポート |
| `searchable.data-directory` | `./data` | データ格納ディレクトリ |
| `searchable.persistence.type` | `H2` | メタデータ DB の種別 |
| `searchable.index.directory` | `./data/indexes` | Lucene インデックス配置先 |
| `searchable.global.default-architecture` | `FULL_TEXT` | 既定の検索方式 |
| `searchable.global.default-search-strategy` | `SEQUENTIAL` | ハイブリッド時の実行戦略 |
| `searchable.plugins.directory` | (未設定) | プラグイン JAR のディレクトリ |

## 10. エラーハンドリング

REST API のエラーは共通フォーマット:

```json
{
  "error": {
    "code": "NAMESPACE_NOT_FOUND",
    "message": "Namespace 'project-a' not found",
    "details": {"namespaceId": "project-a"},
    "timestamp": "2026-05-16T10:00:00Z"
  }
}
```

主なエラーコード:

| Code | HTTP | 想定対応 |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | リクエストボディを見直す |
| `NAMESPACE_NOT_FOUND` | 404 | 事前に Namespace を作成する |
| `DOCUMENT_NOT_FOUND` | 404 | ID を確認する |
| `NAMESPACE_ALREADY_EXISTS` | 409 | 既存 Namespace を再利用するか別 ID にする |
| `INDEX_ERROR` / `SEARCH_ERROR` / `INTERNAL_ERROR` | 500 | サーバーログを確認し再現条件を控える |

## 11. よくあるユースケース

- **社内ドキュメント検索**: 1 Namespace = 1 プロジェクトで運用。検索 UI は `examples/` のサンプルをベースに。
- **AI ツールの参照ソース**: MCP サーバー経由で Claude Desktop から呼び出し。回答生成時の根拠提示に。
- **多言語対応サイト検索**: `VECTOR` モードで言い換え吸収 + `FULL_TEXT` でキーワード厳密一致。

## 12. もっと詳しく

- 全 API の網羅: [examples/api/api-specification.ja.md](../examples/api/api-specification.ja.md)
- 機械可読定義: [examples/api/openapi.yaml](../examples/api/openapi.yaml)
- 内部構造: [architecture.md](architecture.md)
- ベクトル検索の詳細: [vector-search-guide.md](vector-search-guide.md)
- チャンク分割の挙動: [chunking-guide.md](chunking-guide.md)
- ユーザー辞書の管理: [user-dictionary-guide.md](user-dictionary-guide.md)

---

**Document Version**: 1.1
**Last Updated**: 2026-05-16
**Status**: Phases 1–5 complete（モジュール再構成中）
