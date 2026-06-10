# BACKLOG-004: インデックスデータの暗号化保存

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | - |

## 概要

Lucene インデックス（segment ファイル群）と永続化 DB の at-rest 暗号化機構を導入する。

## 補足

- Lucene は標準では Directory レベルの暗号化を提供しないため、`MMapDirectory`
  ラッパーまたはファイルシステムレイヤー（LUKS / FileVault 等）での実現を検討。
- 鍵管理（KMS 連携 / ローカルキーストア）は別途方針を決める必要がある。
- M2 でクローズ済みの BACKLOG-002（テナント毎 at-rest 暗号化、`docs/devel/work/tasks/closed/tasks.m2.md`）と
  論点が重なるため、実装時に統合検討する。

---

**Last Updated**: 2026-06-10
