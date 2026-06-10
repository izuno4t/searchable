# BACKLOG-009: REST 一括取込エンドポイント実装

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | - |

## 概要

`examples/api` の REST API に、複数ドキュメントをまとめて投入できる bulk
エンドポイントを追加する。

## 補足

- 想定 API: `POST /api/v1/namespaces/{ns}/documents/bulk` で
  NDJSON / JSON array を受け付ける。
- 部分失敗時のレスポンス契約（all-or-nothing か per-item か）を設計時に決める。
- 大量取込との関係で BACKLOG-010（非同期実行）と接続する。

---

**Last Updated**: 2026-06-10
