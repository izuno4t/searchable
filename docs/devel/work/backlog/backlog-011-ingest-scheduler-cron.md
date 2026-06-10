# BACKLOG-011: 取込ジョブのスケジューラー（cron）機能

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | BACKLOG-010（非同期実行基盤） |

## 概要

取込ジョブを cron 形式のスケジュールで定期実行できるようにする。

## 補足

- 想定実装: Spring Boot の `@Scheduled` または Quartz を採用候補に検討する。
- スケジュール定義の永続化先（admin DB）と、複数インスタンス時のリーダー選出を設計時に判断する。

---

**Last Updated**: 2026-06-10
