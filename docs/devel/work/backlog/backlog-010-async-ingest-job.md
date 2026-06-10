# BACKLOG-010: 取込ジョブの非同期実行とステータス取得 API

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | - |

## 概要

長時間かかる取込処理を非同期ジョブとして実行できるようにし、ステータス取得
API（progress / result / error）を提供する。

## 補足

- 想定 API: `POST /jobs` でジョブ起票 →
  `GET /jobs/{jobId}` で状態取得。
- ジョブストアの永続化先（admin DB / 別 schema）は設計時に判断する。
- BACKLOG-009（bulk）/ BACKLOG-011（cron）/ BACKLOG-012（resume）/
  BACKLOG-013（parallel）と密接に絡む。

---

**Last Updated**: 2026-06-10
