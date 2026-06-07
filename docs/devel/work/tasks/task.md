# TASKS

マイルストーン: Doc-Reorganization-202606
ゴール: 2026-06 のドキュメント管理体系再編 (ガイドライン v1.0 準拠) を完遂し、再編に伴う残作業と検証を消化する

## 前提

- 本タスクは [docs-guideline] に基づく `docs/` 配下の全面再編を起点とする
- マイルストーン固有の作業 (M3 / Review-202606) はそれぞれ [`m3.md`](m3.md) / [`review-202606.md`](review-202606.md) で管理する
- 本ファイル `task.md` は「カレントの最優先タスク」を集約する常設ファイルとし、内容が落ち着いたら適宜更新する

## ワークフロールール

- タスク開始時にステータスを 🚧 に更新する
- タスク完了時にステータスを ✅ に更新する
- DependsOn のタスクがすべて ✅ になるまで開始しない
- 完了したタスクが本ファイルから不要になったら、`work/archive/` へ切り出すか削除する

## ステータス表記

| ステータス | 意味 |
| ---- | ---- |
| ⏳ | TODO |
| 🚧 | IN_PROGRESS |
| 🧪 | REVIEW |
| ✅ | DONE |
| 🚫 | CANCELLED |

## タスク一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| TASK-001 | ✅ | ガイドライン v1.0 準拠のディレクトリ構成へ docs/ 配下を全面移行する | - |
| TASK-002 | ✅ | 旧パスへの参照を新パスへ書き換える (README・CLAUDE.md・examples・Java コメントほか) | TASK-001 |
| TASK-003 | ✅ | 入口 README を新設する (`docs/README.md` / `docs/devel/README.md` / `docs/devel/design/README.md`) | TASK-001 |
| TASK-004 | ✅ | CLAUDE.md を実装現状 (Pre-1.0・実モジュール構成・実依存) に最新化する | TASK-002 |
| TASK-005 | ⏳ | 新設した3つの入口 README を `git add` で staging する | TASK-003 |
| TASK-006 | ⏳ | 再編後のファイル全体を markdownlint-cli2 で検証し、警告を解消する | TASK-001,TASK-003 |
| TASK-007 | ⏳ | `examples/filesystem-plugin/` の壊れた残骸 (src なし・README なし・pom なし) の処置を決定し実施する | - |
| TASK-008 | ✅ | カレントタスク識別ルール (「`work/tasks/` 配下 = 進行中」「`task.md` が常設の最優先」) を `docs/devel/README.md` に追記する | TASK-003 |

## タスク詳細

### TASK-005

- 補足: `git status` で Untracked と表示される3ファイルを staging する
- 注意: コミット作成はユーザーが行うため、staging のみで止める

### TASK-006

- 補足: `markdownlint-cli2` の実行は本セッションでは権限ルールによりブロックされている
- 注意: ユーザー手元での実行と、出た警告の追修正を想定する

### TASK-007

- 補足: `examples/filesystem-plugin/` には旧 `searchable-core/` ディレクトリと `target/` のみが残存し、`src/` も `README.md` も `pom.xml` も存在しない
- 注意: レビュー対応 [`review-202606.md`](review-202606.md) の TASK-005 (旧 searchable-api/mcp/ui の処置) と方針を揃える

### TASK-008

- 補足: 「カレント = `work/tasks/` 配下にあるもの」「`task.md` は常設の最優先タスク集約ファイル」を明文化する
- 注意: ガイドライン本文の変更ではなく、本リポジトリ独自の運用ルールとして補足する
