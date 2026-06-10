# MCP サーバーサンプル - 要件

`examples/mcp/` は Searchable を利用した **MCP (Model Context Protocol)
サーバーのサンプル実装** である。位置付けはサンプルだが、API Key 認証を
有効化することで軽度の本番利用にも耐える品質を目指す。

ライブラリ本体の要件は [docs/devel/requirements.md](../../docs/devel/requirements.md)
を参照。

## 1. 位置付け

- AI クライアント (Claude Desktop 等) から Searchable の検索機能を呼び出す
  ためのリファレンス実装
- `io.searchable.example.mcp` パッケージで提供
- 成果物: `mcp-example-1.0.0.jar` + 依存 JAR 群

## 2. 機能要件

### 2.1 MCP ツール

- `search_documents`
  - ドキュメント検索
  - パラメータ: `query`, `namespace_ids`, `max_results`
  - 戻り値: ヒットリスト(ID・タイトル・抜粋・スコア)
- `get_document`
  - 検索結果のドキュメント参照(遅延ロード結果からの本文取得)
  - パラメータ: `document_id`, `namespace_id`

### 2.2 動作モード

- **stdio モード**: プロセス起動型クライアント (Claude Desktop 等) 向け
- **SSE モード**: HTTP 経由

### 2.3 認証

- API Key 認証(オプション、SSE モード時のみ)
  - 環境変数 `SEARCHABLE_API_KEY` または設定で指定
  - リクエストヘッダ `X-API-Key`
- stdio モードはプロセス起動者の権限に従う(認証なし)

### 2.4 クライアント設定例

- Claude Desktop の `claude_desktop_config.json` 設定例を提供
- stdio / SSE 両モードのサンプル

## 3. 非機能要件

- Java 21
- stdio モードはレイテンシ目標 1s 以内(検索 500ms + シリアライズ)
- 参照専用プロセスとして稼働可能(インデックス更新は別系統で実施)

## 4. 範囲外

- インデックス更新(MCP 経由)。将来拡張として library 要件書 2.4.3 に
  記載
- 認可・ロール管理
- 監査ログ

---

**Document Version**: 1.0
**Last Updated**: 2026-05-16
**Status**: Phase 1
