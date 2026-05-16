# 検索 UI サンプル - 要件

`examples/search-ui/` は Searchable の検索機能を呼び出す **React 製の検索
UI サンプル** である。エンドユーザー向けの検索画面のリファレンス実装。

ライブラリ本体の要件は [docs/requirements.md](../../docs/requirements.md)
を参照。

## 1. 位置付け

- ライブラリ利用のリファレンス実装
- バックエンドは `examples/api/` (REST WebAPI サンプル) を想定
- フロントエンドのみ(Maven モジュールではなく独立した React プロジェクト)
- 配置: `examples/search-ui/src/main/frontend/`

## 2. 機能要件

### 2.1 検索インターフェース

- シンプルな検索ボックス
- 検索結果一覧表示
- ページネーション
- ハイライト表示(`<mark>` タグでヒット箇所を強調)

### 2.2 デバウンス検索

- インクリメンタル検索時の重複リクエスト抑制
- 設定可能なデバウンス間隔(既定 300ms)
- 先行リクエストの自動キャンセル(AbortController)

### 2.3 ファセット UI

- フィルタ値と件数の動的表示
- フィルタの選択/解除による絞り込み
- 複数値フィルタ (AND/OR) のサポート

### 2.4 検索結果のドキュメント参照

- 結果クリック時に該当ドキュメントを取得(REST API の `GET /documents/{id}` 経由)
- スニペットからのアンカー付き URL に対応
- セクション単位の Sub-results 表示

## 3. 非機能要件

- 軽量(初回ロード目標 < 500KB gzip)
- アクセシビリティ: WCAG 2.1 AA 準拠
- 多言語対応(まずは日本語/英語)

## 4. 技術スタック(想定)

- React 18+
- Vite または Next.js
- Tailwind CSS 等の軽量 CSS フレームワーク
- バックエンド: `examples/api/` の REST API

## 5. 範囲外

- 認証 UI(API Key は環境変数または埋め込みで設定する想定)
- インデックス管理 UI (それは `searchable-admin` の責務)
- ユーザー管理

---

**Document Version**: 1.0
**Last Updated**: 2026-05-16
**Status**: Phase 1 (skeleton only)
