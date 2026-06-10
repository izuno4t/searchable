# BACKLOG-005: カスタム検索フィルタープラグイン SPI

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | - |

## 概要

要件 2.7.2「将来拡張」に該当。検索クエリ実行時に外部ロジックでフィルタリング
できるよう、プラグイン SPI を新設する。

## 補足

- DataSourcePlugin と同じ SPI 基盤を流用する想定（ServiceLoader ベース）。
- 想定 API: `SearchFilter#accept(SearchHit) -> boolean` または
  `SearchFilter#rewrite(Query) -> Query` 等、Lucene `Query` への介入点を検討する。

---

**Last Updated**: 2026-06-10
