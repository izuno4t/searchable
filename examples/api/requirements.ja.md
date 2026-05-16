# REST API サンプル - 要件

`examples/api/` は Searchable を利用した **REST WebAPI のサンプル実装** で
ある。位置付けはサンプルだが、設定された API Key 認証を有効化することで
軽度の本番利用にも耐える品質を目指す。

ライブラリ本体の要件は [docs/requirements.md](../../docs/requirements.md)
を参照。

## 1. 位置付け

- Searchable コアライブラリの利用例
- `io.searchable.example.api` パッケージで提供
- 成果物: `api-example-1.0.0-SNAPSHOT.jar` (Spring Boot fat jar)

## 2. 機能要件

### 2.1 検索 API

- `POST /api/v1/search` 検索実行
- 検索オプション(`maxResults`, `offset`, `highlightEnabled`, `filters`)対応
- ファセット集計(`facets`)対応
- ハイブリッド/全文/ベクトル検索切替

### 2.2 ドキュメント参照 API

- `GET /api/v1/documents/{id}` ドキュメント本文取得
- 遅延ロード結果(2.1.4)対応: 検索時は ID+スコア+URL のみ返却、
  本文はこの API でオンデマンド取得

### 2.3 インデックス管理 API

- `POST /api/v1/index/documents` ドキュメント登録
- `POST /api/v1/index/batch` バッチ登録
- `DELETE /api/v1/index/documents/{id}` 削除
- `POST /api/v1/index/rebuild` 再構築
- `GET /api/v1/index/{namespaceId}/metadata` 状態取得

### 2.4 Namespace 管理 API

- `GET/POST/PUT/DELETE /api/v1/namespaces` CRUD
- `GET/PUT /api/v1/namespaces/{id}/config` 設定

### 2.5 管理 API

- `GET /api/v1/admin/status` システム状態
- `GET /api/v1/admin/metrics` メトリクス
- `POST /api/v1/admin/backup` `POST /api/v1/admin/restore`

### 2.6 認証

- API Key 認証(オプション)
  - 環境変数 `SEARCHABLE_API_KEY` または設定 `searchable.api.key` で指定したとき有効化
  - 未設定時は認証なし(開発・組み込み利用向け)
  - リクエストヘッダ `X-API-Key` で送信

### 2.7 仕様書

- OpenAPI 3.x 仕様書を `/v3/api-docs` で公開
- Swagger UI を `/swagger-ui.html` で提供

## 3. 非機能要件

- Spring Boot 3.x ベース、Java 21
- 検索レスポンス目標: 500ms 以内(単一 Namespace、100k 件)
- インデックス更新と同居動作する場合は本サンプルが「書込側プロセス」となる
  ため、同 Namespace に対して他の書込プロセスを起動しないこと
  (詳細は library 要件書 2.3.5)

## 4. 範囲外

- 認可・ロール管理(API Key 単一方式のみ)
- 監査ログ
- マルチテナント認証(Namespace と API Key の紐付け)
- 永続セッション

これらが必要な場合は本サンプルをベースに独自実装すること。

---

**Document Version**: 1.0
**Last Updated**: 2026-05-16
**Status**: Phase 1
