# Web アプリ サンプル - 要件

`examples/webapp/` は Searchable を **ライブラリとして組み込んだ単一プロセス
の Web アプリケーション** のサンプル実装。エンドユーザー向けの検索 UI
を Thymeleaf で提供し、同じ JVM 内にインデックスを保持する。

「外部 API サーバーを立てたくない / 単一デプロイ単位で完結させたい」
というユースケースの参考実装。

ライブラリ本体の要件は [docs/devel/requirements.md](../../docs/devel/requirements.md)
を参照。

## 1. 位置付け

- **利用パターン**: Searchable をライブラリとして直接組み込み
- 1 プロセスで **書込(インデクシング)+ 検索 + UI 表示** をすべて担当
- `io.searchable.example.webapp` パッケージで提供
- 成果物: `webapp-example-1.0.0.jar` (Spring Boot fat jar)

## 2. 機能要件

### 2.1 検索ページ (Thymeleaf)

- 検索ボックスとフォーム送信
- 検索結果一覧表示(タイトル・スニペット・スコア)
- ページネーション
- ハイライト表示

### 2.2 ドキュメント詳細ページ

- 検索結果クリックでドキュメント本文を表示
- セクション (Sub-results) へのアンカーリンク

### 2.3 簡易インデクシング

- 起動時にローカルディレクトリからドキュメントを読み込んで Namespace に登録
  (起動時バッチ)
- ファイル変更時の再インデックスは範囲外(必要なら `searchable-cli` を使う)

### 2.4 設定

- `application.properties` で:
  - 対象ディレクトリ
  - Namespace ID
  - インデックス保存先
  - 検索オプション

## 3. 非機能要件

- Spring Boot 3.x、Java 21
- 起動時間: 数秒以内(空インデックスの場合)
- 検索レスポンス: 500ms 以内(単一 Namespace、100k 件)

## 4. 範囲外

- 認証(社内ネットワーク前提の単一プロセスサンプル)
- マルチ Namespace UI(設定で 1 Namespace 固定)
- 管理画面(それは `searchable-admin` の責務)
- REST API の公開(それは `examples/api` の責務)
- 複数プロセスでの読込共有(本サンプルは embedded 単一プロセス前提)

---

**Document Version**: 1.0
**Last Updated**: 2026-05-16
**Status**: Phase 1 (skeleton only)
