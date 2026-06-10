# BACKLOG-016: Web クローラー取込 DataSourcePlugin 実装

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | - |

## 結論

Web ページ取込用の `DataSourcePlugin` を新設する。
クローラー本体（HTTP fetch / robots.txt 解釈 / URL 正規化 / frontier 管理 / リトライ等）
は OSS の Java クローラーライブラリに委譲し、本プラグインは設定の受け渡し、
`PluginDocument` への整形、`metadata.url` 等の予約キー付与のアダプターに徹する。
**フル自前実装は避ける**（再発明コストが過大なため）。

## 採用ライブラリ

本タスクの第一ステップで選定する（crawler4j / norconex-crawler / StormCrawler
等を、ライセンス・保守状況・依存サイズ・Java 21 互換性で比較。**一次情報で要確認**）。
MVP（URL リスト取込）のみで足りる場合は `jsoup` + Java 標準 `HttpClient` の
軽量構成でも可、と段階別に判断する。

## 背景

現状の同梱データソースは `FilesystemDataSourcePlugin` と
`examples/plugin-datasource-s3` のみで、Web 上のページを取込する経路がない。
`HtmlParser` はローカル HTML のパースのみを担い、HTTP fetch は持たない。

## スコープ案（段階導入）

- **フェーズ 1（MVP）**: URL リスト取込。`urls: [...]` または sitemap.xml を
  config で受け、各 URL を逐次 fetch → `HtmlParser` でパース → `PluginDocument` 化。
  `metadata.url` はそのまま元 URL を採用。
- **フェーズ 2**: 再帰クロール。`seedUrls` + `maxDepth` + `sameOriginOnly` 等の
  制約付きでリンク追跡。`robots.txt` 尊重、`Crawl-Delay` 解釈、同時実行数上限、
  重複 URL の正規化（クエリ並び替え・末尾スラッシュ等）を含む。

## 注意

- User-Agent は識別可能な文字列を既定とし、設定で上書き可能にする。
- 動的レンダリング（JS 実行）は対象外。必要なら別プラグインで切り出す。
- 文字コード判定は HTTP `Content-Type` ヘッダー → HTML meta → UTF-8 フォール
  バックの順（採用ライブラリの機能を優先利用）。
- 認証付きサイトは将来課題（Basic / Bearer / Cookie はフェーズ 2 以降）。
- 採用ライブラリが内部で独自 HTTP クライアントを持つ場合は、M3 TASK-001 〜
  TASK-003 の「Java 標準 `HttpClient` 優先」方針よりライブラリ選定を優先する。

## ADR 化判断

ライブラリ選定が決まった時点で、その採用理由を独立 ADR として記録する
（本エントリは「やる / やらない」のスコープ管理にとどめる）。

## 関連

- `DataSourcePlugin` SPI
- `HtmlParser`
- `metadata.url` 予約キー
- 詳細は `docs/devel/design/architecture/overview.md` §5.7 を参照

---

**Last Updated**: 2026-06-10
