# 検索 UI サンプル - 要件

`examples/search-ui/` は `examples/api/` (REST WebAPI サンプル) の
**クライアント側サンプル**。Searchable を「API 経由」で利用する統合パターン
のリファレンス実装で、**Vanilla HTML + JavaScript** により実装する。
ビルドツールやフレームワーク (React / Vue 等) は使用しない。

ライブラリ本体の要件は [docs/requirements.md](../../docs/requirements.md)
を参照。サーバー側の要件は
[examples/api/requirements.ja.md](../api/requirements.ja.md) を参照。

## 1. 位置付け

- **利用パターン**: API 経由(REST API サーバーを介した統合)
- バックエンド: `examples/api/` の REST API
- フレームワーク非依存(ブラウザネイティブの ES Modules のみ)
- 静的ファイルのみで構成され、任意の Web サーバーで配信可能
  (`python -m http.server`、nginx 等)

## 2. ファイル構成

```text
examples/search-ui/
├── index.html              # メインの検索ページ
├── requirements.ja.md      # 本ドキュメント
└── src/
    ├── js/
    │   └── app.js          # 検索ロジック・デバウンス・ファセット制御
    └── css/
        └── style.css       # スタイル
```

## 3. 機能要件

### 3.1 検索インターフェース

- シンプルな検索ボックス
- 検索結果一覧表示
- ページネーション
- ハイライト表示(`<mark>` タグを安全に描画)

### 3.2 デバウンス検索

- インクリメンタル検索時の重複リクエスト抑制
- デバウンス間隔の既定値: 300ms(設定変更可)
- 先行リクエストの自動キャンセル(`AbortController`)

### 3.3 ファセット UI

- フィルタ値と件数の動的表示
- フィルタの選択/解除による絞り込み
- 複数値フィルタ (AND/OR) のサポート

### 3.4 ドキュメント参照

- 結果クリック時に該当ドキュメントを取得(REST API の `GET /documents/{id}` 経由)
- スニペットからのアンカー付き URL に対応
- セクション単位の Sub-results 表示

## 4. 非機能要件

- ビルド不要: `index.html` を開けば動く(静的サーバーがあれば十分)
- 初回ロードサイズ: 50KB 以下を目標
- 主要ブラウザ最新版で動作 (Chrome / Firefox / Safari / Edge)
- アクセシビリティ: WCAG 2.1 AA 準拠を目指す

## 5. 設定

- API のベース URL を `index.html` または `src/js/app.js` の先頭で
  指定できるようにする(例: `const API_BASE = "http://localhost:8080";`)
- CORS が必要な場合は `examples/api` 側で許可設定する

## 6. 範囲外

- 認証 UI(API Key は環境変数または埋め込みで設定する想定)
- インデックス管理 UI (それは `searchable-admin` の責務)
- ユーザー管理
- フレームワーク採用 (React 等が必要になった場合は別ブランチで)

---

**Document Version**: 1.0
**Last Updated**: 2026-05-16
**Status**: Phase 1 (skeleton only)
