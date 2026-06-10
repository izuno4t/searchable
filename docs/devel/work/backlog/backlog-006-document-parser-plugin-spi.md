# BACKLOG-006: 文書パーサープラグイン SPI

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | - |

## 概要

現状はリポジトリ内蔵の `DocumentParser` 群（PDF / HTML / Office / Markdown 等）のみ。
外部プラグインで新フォーマットの抽出器を追加できるよう SPI を切り出す。

## 補足

- DataSourcePlugin / AiProvider と同じく `META-INF/services` 登録方式を想定。
- 拡張子 / MIME タイプの解決経路（`ExtractorFactory`）にプラグイン抽出器を
  挿入するためのレジストリ設計が必要。

---

**Last Updated**: 2026-06-10
