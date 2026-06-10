# BACKLOG-002: Google Docs / Apple Pages 連携（PDF 変換経由）

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | - |

## 概要

Google Docs / Apple Pages 形式のドキュメントを Searchable で取込可能にする。
直接パースは行わず、PDF 変換を経由して既存の `PdfParser` で処理する想定。

## 補足

- 変換手段は外部ツール（`gdrive` CLI / AppleScript / `soffice --convert-to pdf` 等）に依存する想定。
- 変換ステップを `DataSourcePlugin` 内で吸収するか、利用者側の前処理に任せるかは設計時に判断する。

---

**Last Updated**: 2026-06-10
