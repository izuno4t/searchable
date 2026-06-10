# Searchable MCP サーバー利用ガイド

Searchable を MCP (Model Context Protocol) サーバーとして起動し、
Claude Desktop 等の AI クライアントから検索を呼び出す手順。

## 1. 前提

- Java 21 以上
- 既存の Searchable インデックス（H2 メタデータ + Lucene index）

## 2. ビルド

```bash
mvn -B -pl searchable-mcp -am package
```

成果物:

- `searchable-mcp/target/searchable-mcp-1.0.0.jar`
- `searchable-mcp/target/lib/`（依存JAR一式）

## 3. 設定ファイル

REST API と同じ YAML 形式の設定ファイルを利用する
（`docs/public/setup-guide.md` 参照）。MCPサーバー固有の追加項目は不要。

```yaml
data-directory: ./data
persistence:
  type: H2
  url: "jdbc:h2:./data/metadata;MODE=PostgreSQL"
  username: sa
  password: ""
index:
  directory: ./data/indexes
plugins:
  directory: ./plugins
global:
  default-architecture: HYBRID
  default-search-strategy: PARALLEL
  default-search-order: FULL_TEXT_FIRST
```

## 4. 起動

stdio モードでサーバーを起動する。MCPクライアントから子プロセスとして
起動するのが標準だが、デバッグ目的の手動起動も可能。

```bash
java -jar searchable-mcp/target/searchable-mcp-1.0.0.jar \
  --config /path/to/searchable.yaml
```

- 標準入力から JSON-RPC リクエストを読み取り
- 標準出力に JSON-RPC レスポンスを書き出し
- ログはすべて標準エラーに出力

## 5. Claude Desktop からの利用

Claude Desktop の設定ファイル
（macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`）
に以下を追記:

```json
{
  "mcpServers": {
    "searchable": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/searchable-mcp-1.0.0.jar",
        "--config",
        "/absolute/path/to/searchable.yaml"
      ]
    }
  }
}
```

Claude Desktop を再起動すると、ツールパレットに `search_documents`
が表示される。

## 6. 提供ツール

### search_documents

Searchable 上のドキュメントを検索する。

**入力スキーマ:**

```json
{
  "query": "検索したいキーワード",
  "namespace_ids": ["project-a"],
  "search_type": "HYBRID",
  "max_results": 10
}
```

| パラメータ | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| `query` | string | はい | 検索クエリ |
| `namespace_ids` | string[] | いいえ | 対象 Namespace（省略時は全Namespace） |
| `search_type` | string | いいえ | `FULL_TEXT` / `VECTOR` / `HYBRID`（省略時は Namespace 設定） |
| `max_results` | integer | いいえ | 最大ヒット数（デフォルト 10） |

**出力例:**

```text
検索結果: 3件ヒット (12 ms)

1. [project-a/doc-001] Lucene入門
   score: 0.8542
   全文検索エンジンの基本を解説。Apache Lucene は ...

2. [project-a/doc-007] ベクトル検索の仕組み
   score: 0.6213
   意味的類似度に基づく検索手法。HNSW などの ...
```

## 7. プロトコル

MCP プロトコル仕様: <https://modelcontextprotocol.io/>

実装している JSON-RPC メソッド:

- `initialize`: ハンドシェイク（バージョン・機能広告）
- `notifications/initialized`: クライアントからの初期化完了通知
- `tools/list`: 提供ツール一覧
- `tools/call`: ツール実行
- `ping`: 死活確認

## 8. トラブルシューティング

### MCPクライアントが応答しない

- ログ（標準エラー）に起動失敗が出ていないか確認
- 設定ファイルパスが絶対パスになっているか確認
- Java 21 以上で起動しているか確認

### 検索結果が0件

- 対象 Namespace に文書が登録されているか REST API で確認
- 形態素解析の都合上、短すぎる単語はストップワード扱いの場合あり

### 性能が遅い

- 初回検索時は Lucene のインデックスを mmap するため、ウォームアップに
  数秒かかる場合がある
- 性能要件（500ms以内）は事前検証済み（`docs/devel/work/investigations/003-performance.md`）

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 2
