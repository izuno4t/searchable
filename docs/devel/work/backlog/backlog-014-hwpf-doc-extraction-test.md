# BACKLOG-014: レガシー `.doc`（HWPF）パーサーの実抽出テスト整備

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | - |

## 概要

実 `.doc` バイナリをフィクスチャとして HWPF 抽出経路を直接検証する。

## 背景

M1 TASK-177 で `.doc` は POI が新規書き出し未サポートのため、現状
`OfficeDocumentParserTest` では登録・MIME・拡張子解決のみ検証し、実抽出
は同じ `ExtractorFactory` 経路の `.xls` / `.ppt` で間接カバーに留めている。

## 注意

- フィクスチャは **ライセンスがクリア（自作 or 再配布可能）** かつ **小さい（数 KB）** ものを用意する。
- `.docx` をリネームしただけでは `ExtractorFactory` がファイルマジックで OOXML
  と判定するため不可（本物の Word 97-2003 / OLE2 バイナリが必要）。

## 備考

POI 単体では生成不可。LibreOffice headless（`soffice --convert-to doc`）等で
生成するか、自作の最小 `.doc` を同梱する。

---

**Last Updated**: 2026-06-10
