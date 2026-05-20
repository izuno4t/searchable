# Searchable - API仕様書

## 1. REST API仕様

### 1.1 基本情報

- **Base URL**: `/api/v1`
- **認証**: API Key, Bearer Token（Phase 2以降）
- **Content-Type**: `application/json`
- **文字エンコーディング**: UTF-8

### 1.2 検索API

#### 1.2.1 検索実行

**エンドポイント**: `POST /api/search`

**リクエスト**:

```json
{
  "query": "検索クエリ",
  "namespaceIds": ["namespace1", "namespace2"],
  "searchType": "FULL_TEXT|VECTOR|HYBRID",
  "options": {
    "highlightEnabled": true,
    "maxResults": 10,
    "offset": 0
  },
  "filters": {
    "field": "value"
  }
}
```

**レスポンス**:

```json
{
  "hits": [
    {
      "id": "doc123",
      "namespaceId": "namespace1",
      "title": "ドキュメントタイトル",
      "content": "本文...",
      "score": 0.95,
      "highlight": {
        "content": ["...検索<em>クエリ</em>..."]
      },
      "metadata": {
        "source": "file.txt",
        "indexedAt": "2026-01-15T10:00:00Z"
      }
    }
  ],
  "totalHits": 100,
  "maxScore": 0.95,
  "took": 45
}
```

**ステータスコード**:

- `200 OK`: 検索成功
- `400 Bad Request`: リクエスト不正
- `404 Not Found`: Namespace未存在
- `500 Internal Server Error`: サーバーエラー

#### 1.2.2 検索結果取得

**エンドポイント**: `GET /api/search/{searchId}`

**レスポンス**:

```json
{
  "searchId": "search123",
  "query": "検索クエリ",
  "status": "COMPLETED",
  "result": {
    "hits": [],
    "totalHits": 100
  }
}
```

### 1.3 Namespace管理API

#### 1.3.1 Namespace一覧取得

**エンドポイント**: `GET /api/namespaces`

**レスポンス**:

```json
{
  "namespaces": [
    {
      "id": "namespace1",
      "name": "プロジェクトA",
      "config": {
        "architecture": "HYBRID",
        "searchStrategy": "PARALLEL"
      },
      "indexMetadata": {
        "documentCount": 5000,
        "indexSize": 1024000,
        "lastUpdated": "2026-01-15T10:00:00Z"
      },
      "createdAt": "2026-01-10T10:00:00Z",
      "updatedAt": "2026-01-15T10:00:00Z"
    }
  ],
  "total": 5
}
```

#### 1.3.2 Namespace作成

**エンドポイント**: `POST /api/namespaces`

**リクエスト**:

```json
{
  "id": "namespace1",
  "name": "プロジェクトA",
  "config": {
    "architecture": "HYBRID",
    "searchStrategy": "PARALLEL",
    "searchOrder": "FULL_TEXT_FIRST"
  }
}
```

**レスポンス**:

```json
{
  "id": "namespace1",
  "name": "プロジェクトA",
  "createdAt": "2026-01-15T10:00:00Z"
}
```

**ステータスコード**:

- `201 Created`: 作成成功
- `400 Bad Request`: リクエスト不正
- `409 Conflict`: ID重複

#### 1.3.3 Namespace取得

**エンドポイント**: `GET /api/namespaces/{namespaceId}`

**レスポンス**:

```json
{
  "id": "namespace1",
  "name": "プロジェクトA",
  "config": {},
  "indexMetadata": {},
  "createdAt": "2026-01-10T10:00:00Z",
  "updatedAt": "2026-01-15T10:00:00Z"
}
```

#### 1.3.4 Namespace更新

**エンドポイント**: `PUT /api/namespaces/{namespaceId}`

**リクエスト**:

```json
{
  "name": "プロジェクトA（更新）",
  "config": {
    "architecture": "FULL_TEXT"
  }
}
```

**レスポンス**:

```json
{
  "id": "namespace1",
  "name": "プロジェクトA（更新）",
  "updatedAt": "2026-01-15T11:00:00Z"
}
```

#### 1.3.5 Namespace削除

**エンドポイント**: `DELETE /api/namespaces/{namespaceId}`

**ステータスコード**:

- `204 No Content`: 削除成功
- `404 Not Found`: Namespace未存在

#### 1.3.6 Namespace設定取得

**エンドポイント**: `GET /api/namespaces/{namespaceId}/config`

**レスポンス**:

```json
{
  "architecture": "HYBRID",
  "searchStrategy": "PARALLEL",
  "searchOrder": "FULL_TEXT_FIRST",
  "embeddingConfig": {
    "model": "all-MiniLM-L6-v2",
    "dimension": 384
  },
  "customParams": {}
}
```

#### 1.3.7 Namespace設定更新

**エンドポイント**: `PUT /api/namespaces/{namespaceId}/config`

**リクエスト**:

```json
{
  "architecture": "FULL_TEXT",
  "searchStrategy": "SEQUENTIAL"
}
```

### 1.4 インデックス管理API

#### 1.4.1 ドキュメント登録

**エンドポイント**: `POST /api/index/documents`

**リクエスト**:

```json
{
  "namespaceId": "namespace1",
  "document": {
    "id": "doc123",
    "title": "ドキュメントタイトル",
    "content": "本文...",
    "metadata": {
      "url": "https://docs.example.com/doc123",
      "category": "guide",
      "lang": "ja",
      "tags": ["beginner", "tutorial"],
      "author": "user1"
    }
  }
}
```

`metadata` の予約キー(詳細は `docs/architecture.md` §5.7):

| キー | 値 | 用途 |
| --- | --- | --- |
| `url` | RFC 3986 形式の **URI(スキーム必須)**。`file:///abs/path`, `https://...`, `s3://bucket/key` 等。生パス(`/abs/path`)は不可。 | 検索結果から元ドキュメントへの直リンク。セクション単位ヒット (`SubResult.anchorUrl`) の base としても利用 |
| `category` / `lang` / `tags` | string / string array | facet 用 |

これら以外の任意キーはそのまま保存・返却される(`author` 等)。

**レスポンス**:

```json
{
  "id": "doc123",
  "namespaceId": "namespace1",
  "indexedAt": "2026-01-15T10:00:00Z",
  "status": "INDEXED"
}
```

**ステータスコード**:

- `201 Created`: 登録成功
- `400 Bad Request`: リクエスト不正
- `404 Not Found`: Namespace未存在

#### 1.4.2 ドキュメント更新

**エンドポイント**: `PUT /api/index/documents/{documentId}`

**リクエスト**:

```json
{
  "namespaceId": "namespace1",
  "title": "更新後タイトル",
  "content": "更新後本文..."
}
```

#### 1.4.3 ドキュメント削除

**エンドポイント**: `DELETE /api/index/documents/{documentId}`

**クエリパラメータ**:

- `namespaceId`: Namespace ID（必須）

**ステータスコード**:

- `204 No Content`: 削除成功
- `404 Not Found`: ドキュメント未存在

#### 1.4.4 バッチ登録

**エンドポイント**: `POST /api/index/batch`

**リクエスト**:

```json
{
  "namespaceId": "namespace1",
  "documents": [
    {
      "id": "doc1",
      "title": "タイトル1",
      "content": "本文1..."
    },
    {
      "id": "doc2",
      "title": "タイトル2",
      "content": "本文2..."
    }
  ]
}
```

**レスポンス**:

```json
{
  "total": 2,
  "succeeded": 2,
  "failed": 0,
  "results": [
    {
      "id": "doc1",
      "status": "INDEXED"
    },
    {
      "id": "doc2",
      "status": "INDEXED"
    }
  ]
}
```

#### 1.4.5 インデックス再構築

**エンドポイント**: `POST /api/index/rebuild`

**リクエスト**:

```json
{
  "namespaceId": "namespace1"
}
```

**レスポンス**:

```json
{
  "jobId": "rebuild-job-123",
  "status": "STARTED",
  "estimatedTime": 300
}
```

### 1.5 管理API

#### 1.5.1 システム状態取得

**エンドポイント**: `GET /api/admin/status`

**レスポンス**:

```json
{
  "version": "1.0.0",
  "uptime": 86400,
  "status": "HEALTHY",
  "namespaceCount": 5,
  "totalDocuments": 50000,
  "totalIndexSize": 10240000
}
```

#### 1.5.2 バックアップ実行

**エンドポイント**: `POST /api/admin/backup`

**リクエスト**:

```json
{
  "namespaceId": "namespace1",
  "backupPath": "/backup/namespace1-20260115.bak"
}
```

**レスポンス**:

```json
{
  "jobId": "backup-job-123",
  "status": "STARTED"
}
```

#### 1.5.3 リストア実行

**エンドポイント**: `POST /api/admin/restore`

**リクエスト**:

```json
{
  "namespaceId": "namespace1",
  "backupPath": "/backup/namespace1-20260115.bak"
}
```

#### 1.5.4 メトリクス取得

**エンドポイント**: `GET /api/admin/metrics`

**レスポンス**:

```json
{
  "searchMetrics": {
    "totalSearches": 10000,
    "averageResponseTime": 120,
    "p95ResponseTime": 450
  },
  "indexMetrics": {
    "totalDocuments": 50000,
    "averageIndexingTime": 50
  },
  "systemMetrics": {
    "memoryUsage": 2048000,
    "diskUsage": 10240000
  }
}
```

---

## 2. Java API仕様

### 2.1 主要インターフェース

#### 2.1.1 SearchService

```java
public interface SearchService {
    /** 検索実行（同期） */
    SearchResult search(SearchRequest request);

    /** 検索実行（非同期） */
    CompletableFuture<SearchResult> searchAsync(SearchRequest request);

    /** 検索履歴取得 */
    List<SearchHistory> getSearchHistory(String namespaceId);
}
```

**使用例**:

```java
SearchService searchService = SearchableLibrary.getSearchService();

SearchRequest request = SearchRequest.builder()
    .query("検索クエリ")
    .namespaceIds(Arrays.asList("namespace1"))
    .searchType(SearchType.HYBRID)
    .maxResults(10)
    .build();

SearchResult result = searchService.search(request);
```

#### 2.1.2 NamespaceService

```java
public interface NamespaceService {
    /** Namespace作成 */
    Namespace createNamespace(NamespaceCreateRequest request);

    /** Namespace削除 */
    void deleteNamespace(String namespaceId);

    /** Namespace取得 */
    Namespace getNamespace(String namespaceId);

    /** Namespace一覧取得 */
    List<Namespace> listNamespaces();

    /** 設定更新 */
    void updateConfig(String namespaceId, NamespaceConfig config);
}
```

**使用例**:

```java
NamespaceService namespaceService = SearchableLibrary.getNamespaceService();

NamespaceCreateRequest request = NamespaceCreateRequest.builder()
    .id("namespace1")
    .name("プロジェクトA")
    .config(NamespaceConfig.builder()
        .architecture(SearchArchitecture.HYBRID)
        .searchStrategy(SearchStrategy.PARALLEL)
        .build())
    .build();

Namespace namespace = namespaceService.createNamespace(request);
```

#### 2.1.3 IndexService

```java
public interface IndexService {
    /** ドキュメント登録 */
    void indexDocument(String namespaceId, Document document);

    /** ドキュメント一括登録 */
    BatchIndexResult indexDocuments(String namespaceId, List<Document> documents);

    /** ドキュメント削除 */
    void deleteDocument(String namespaceId, String documentId);

    /** インデックス再構築 */
    void rebuildIndex(String namespaceId);

    /** インデックス状態取得 */
    IndexMetadata getIndexMetadata(String namespaceId);
}
```

**使用例**:

```java
IndexService indexService = SearchableLibrary.getIndexService();

Document document = Document.builder()
    .id("doc123")
    .title("ドキュメントタイトル")
    .content("本文...")
    .metadata(Map.of("source", "file.txt"))
    .build();

indexService.indexDocument("namespace1", document);
```

### 2.2 主要データクラス

#### 2.2.1 SearchRequest

```java
@Builder
public class SearchRequest {
    private String query;
    private List<String> namespaceIds;
    private SearchType searchType;
    private SearchOptions options;
    private PaginationParams pagination;
    private Map<String, Object> filters;
}
```

#### 2.2.2 SearchResult

```java
public class SearchResult {
    private List<SearchHit> hits;
    private long totalHits;
    private double maxScore;
    private Map<String, Object> aggregations;
    private long took;  // ms
}
```

#### 2.2.3 Document

```java
@Builder
public class Document {
    private String id;
    private String namespaceId;
    private String title;
    private String content;
    private Map<String, Object> metadata;
    private DocumentSource source;
    private LocalDateTime indexedAt;
}
```

### 2.3 初期化と設定

```java
// 設定ファイルから初期化
SearchableLibrary library = SearchableLibrary.builder()
    .configPath("/path/to/config.yaml")
    .build();

// プログラマティックに初期化
SearchableLibrary library = SearchableLibrary.builder()
    .dataDirectory("/path/to/data")
    .persistenceType(PersistenceType.H2)
    .globalConfig(GlobalConfig.builder()
        .defaultSearchArchitecture(SearchArchitecture.HYBRID)
        .build())
    .build();

// ライブラリの起動
library.start();

// サービス取得
SearchService searchService = library.getSearchService();
NamespaceService namespaceService = library.getNamespaceService();
IndexService indexService = library.getIndexService();

// ライブラリの停止
library.shutdown();
```

---

## 3. MCP API仕様

### 3.1 ツール定義

#### 3.1.1 search_documents

**説明**: ドキュメント検索

**パラメータ**:

```json
{
  "query": {
    "type": "string",
    "description": "検索クエリ",
    "required": true
  },
  "namespace_ids": {
    "type": "array",
    "description": "検索対象Namespace IDリスト",
    "required": false
  },
  "max_results": {
    "type": "integer",
    "description": "最大結果数",
    "required": false,
    "default": 10
  }
}
```

**レスポンス**:

```json
{
  "content": [
    {
      "type": "text",
      "text": "検索結果: 5件ヒット\n\n1. [タイトル1]\n本文の抜粋...\nスコア: 0.95\n\n2. [タイトル2]\n..."
    }
  ]
}
```

### 3.2 MCPサーバー起動

```bash
# SSEモード
java -jar searchable-mcp.jar --mode sse --port 8080

# stdioモード
java -jar searchable-mcp.jar --mode stdio
```

### 3.3 クライアント設定例（Claude Desktop）

```json
{
  "mcpServers": {
    "searchable": {
      "command": "java",
      "args": ["-jar", "/path/to/searchable-mcp.jar", "--mode", "stdio"],
      "env": {
        "SEARCHABLE_CONFIG": "/path/to/config.yaml"
      }
    }
  }
}
```

---

## 4. エラーレスポンス

### 4.1 エラーフォーマット

```json
{
  "error": {
    "code": "NAMESPACE_NOT_FOUND",
    "message": "Namespace 'namespace1' not found",
    "details": {
      "namespaceId": "namespace1"
    },
    "timestamp": "2026-01-15T10:00:00Z"
  }
}
```

### 4.2 エラーコード一覧

| Code | HTTPステータス | 説明 |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | リクエスト不正 |
| `NAMESPACE_NOT_FOUND` | 404 | Namespace未存在 |
| `DOCUMENT_NOT_FOUND` | 404 | ドキュメント未存在 |
| `NAMESPACE_ALREADY_EXISTS` | 409 | Namespace ID重複 |
| `INDEX_ERROR` | 500 | インデックスエラー |
| `SEARCH_ERROR` | 500 | 検索実行エラー |
| `INTERNAL_ERROR` | 500 | 内部エラー |

---

**Document Version**: 1.0
**Last Updated**: 2026-01-15
**Status**: Draft

## 改訂履歴

- v1.0 (2026-01-15): 初版作成（requirements.mdから分離）
