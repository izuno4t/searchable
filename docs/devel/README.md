# docs/devel/

設計・開発・テスト・運用・保守に必要な開発内部文書を配置する。

## 構成

| パス | 役割 |
| --- | --- |
| [`requirements.md`](requirements.md) | 要求・制約・前提 |
| [`design/`](design/) | 現在有効な設計内容（アーキテクチャ・アプリケーション） |
| [`adr/`](adr/) | 重要な設計判断・技術選定の記録 |
| [`standards/`](standards/) | 実装・運用に関する規約 |
| [`testing/`](testing/) | テスト方針・検証手順 |
| [`operation/`](operation/) | リリース後・稼働中システムの扱い |
| [`work/`](work/) | 個別作業に紐づく一時的・準一時的文書 |

## work/ の分類

| パス | 内容 |
| --- | --- |
| [`work/plans/`](work/plans/) | 複数作業を束ねる計画・対応方針 |
| [`work/tasks/`](work/tasks/) | 実行することが決まった作業指示（進行中のものを置く） |
| [`work/investigations/`](work/investigations/) | 調査・分析の記録 |
| [`work/archive/`](work/archive/) | 完了・失効した作業文書 |

## 運用ルール

- `work/tasks/` に置かれているファイルは「進行中のタスク」と扱う
- `work/tasks/task.md` は常設の最優先タスク集約ファイルとし、現在優先で進めている軽量タスクを記載する
- マイルストーン固有のタスクは `work/tasks/{milestone}.md` で別管理する (例: `m3.md`, `review-202606.md`)
- 完了したタスクは `work/archive/` へ移動する（または不要なら削除）
- `work/` で得た恒久化すべき知見は、該当する恒久文書（`requirements/`・`design/`・`adr/` など）へ反映する
- ADR は設計判断の履歴であり、`design/` 配下には配置しない

詳細はガイドライン v1.0 §6・§7・§11 を参照。
