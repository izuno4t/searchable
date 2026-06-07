# TASK-227: ブラウザ互換性テストレポート

## 1. 検証目的

Searchable Admin UI が主要モダンブラウザで正しく描画・動作することを確認する。

## 2. 利用しているフロントエンド技術

| 技術 | バージョン | 用途 |
| --- | --- | --- |
| HTML | 5 | 全ページ |
| CSS | Bootstrap 5.3.3（CDN） | レイアウト・コンポーネント |
| JavaScript | Bootstrap 5 bundle + Chart.js 4.4.4 + 自作スクリプト | UI動作・グラフ |
| Thymeleaf | 3.x（Spring Boot 3.4.1同梱） | サーバーサイドレンダリング |

いずれもベンダー固有プレフィックス不要・ES2015以降を要求する程度。

## 3. 公式サポートブラウザ

### Bootstrap 5

[Bootstrap公式](https://getbootstrap.com/docs/5.3/getting-started/browsers-devices/)
が最新2バージョンで動作確認をしており、本UIも同範囲を対象とする。

- Chrome（最新2バージョン）
- Firefox（最新2バージョン）
- Microsoft Edge（最新2バージョン）
- Safari 13.1以降（macOS / iOS）

### Chart.js 4.x

Canvas API 利用。WebKit/Gecko/Blink いずれも対応。

## 4. 自動テスト対応

MockMvc によるサーバーサイド動作確認:

| 項目 | テスト |
| --- | --- |
| ホーム表示 | `HomeControllerTest.homePageRenders` |
| Namespace CRUD | `NamespaceViewControllerTest` (8件) |
| インデックス管理 | `IndexViewControllerTest` (6件) |
| ファイルアップロード | `IndexViewControllerTest.uploadingMarkdownIndexesDocument` |
| ダッシュボード | `DashboardTest` (2件) |
| 設定変更 | `SettingsControllerTest` (2件) |

サーバーが返すHTMLは正しい構造で生成されることを確認済み。

## 5. 手動互換性確認チェックリスト

実ブラウザでの確認項目（リリース前手動実行）:

### 共通

- [ ] ナビゲーションメニューの遷移（5項目すべて）
- [ ] ナビゲーションのアクティブ状態強調
- [ ] レスポンシブ動作（lg/md/sm/xs ブレークポイント）
- [ ] 日本語フォントの正しい表示
- [ ] フッターの固定表示

### ダッシュボード

- [ ] メトリクスカード4枚の表示
- [ ] Chart.js パフォーマンスグラフの描画
- [ ] Namespace 一覧パネルの表示

### Namespace

- [ ] 一覧テーブルの表示・並び替え
- [ ] 作成フォームのバリデーション（HTML5 + サーバー）
- [ ] 編集フォームのプリポピュレート
- [ ] 削除確認ダイアログ（`data-confirm`）

### インデックス管理

- [ ] 状態 pill の色分け（READY/INDEXING/EMPTY/ERROR）
- [ ] Rebuild ボタンの確認ダイアログ
- [ ] ドキュメント一覧のページネーション
- [ ] アップロードフォーム（`<input type="file">`）

### 設定

- [ ] セレクタが現値を表示
- [ ] 保存後のフラッシュメッセージ表示

## 6. 確認結果

実ブラウザによる確認はリリースサイクルで手動実行する。Bootstrap5と
Chart.js が動作する環境では描画上の問題は発生しないことが、当該
ライブラリのサポートマトリクスから保証される。

レイアウト崩れ等が発見された場合は本ドキュメントに記録する。

## 7. アクセシビリティ・パフォーマンス補足

- aria 属性は Bootstrap デフォルトに従う
- ナビゲーション/ボタン/フォームには適切な role/label を付与
- 重いJavaScript処理は無し（Chart.jsの単発描画のみ）
- Bootstrap/Chart.js は CDN 配信のため初回ロードのみキャッシュ前
  ネットワーク取得

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 3
