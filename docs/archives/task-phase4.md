# TASKS - Phase 4

Milestone: M4 - テスト基盤・CI整備
Goal: テスト基盤（ハーネス）を整理し、CI パイプラインを構築することで、品質ゲートを自動化する

## ワークフロールール

- タスク開始時にステータスを 🚧 に更新する
- タスク完了時にステータスを ✅ に更新する
- DependsOn のタスクが全て ✅ になるまで開始しない

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
| TASK-301 | ✅ | 既存テスト資産棚卸とテスト基盤整備方針レポート作成 | - |
| TASK-302 | ✅ | 共通テスト基盤モジュール searchable-testkit 新規作成 | TASK-301 |
| TASK-303 | ✅ | テスト用組込みDB初期化ヘルパー実装 | TASK-302 |
| TASK-304 | ✅ | テスト用Luceneインデックス初期化ヘルパー実装 | TASK-302 |
| TASK-305 | ✅ | Namespace・Document・SearchRequestのテストデータビルダー実装 | TASK-302 |
| TASK-306 | ✅ | ONNX埋め込みモデルのテスト用スタブ実装 | TASK-302 |
| TASK-307 | ✅ | REST APIテスト用Spring Boot Test共通設定整備 | TASK-302 |
| TASK-308 | ✅ | MCPサーバ統合テスト用クライアントヘルパー整備 | TASK-302 |
| TASK-309 | ✅ | 既存ユニットテストのsearchable-testkit利用への移行 | TASK-303,TASK-304,TASK-305,TASK-306 |
| TASK-310 | ✅ | 既存統合テストのsearchable-testkit利用への移行 | TASK-307,TASK-308 |
| TASK-311 | ✅ | JaCoCoカバレッジ集計設定とレポート出力設定 | TASK-309,TASK-310 |
| TASK-312 | ✅ | Checkstyle設定ファイル作成とMavenプラグイン組込み | TASK-301 |
| TASK-313 | ✅ | SpotBugs設定ファイル作成とMavenプラグイン組込み | TASK-301 |
| TASK-314 | ✅ | OWASP Dependency-CheckのMavenプラグイン組込み | TASK-301 |
| TASK-315 | ✅ | markdownlint-cli2設定ファイル整備 | TASK-301 |
| TASK-316 | ✅ | OpenAPI Spec Lint(Spectral等)設定追加 | TASK-301 |
| TASK-317 | ✅ | GitHub Actionsワークフロー基盤ファイル(ci.yml)作成 | TASK-301 |
| TASK-318 | ✅ | ビルド・ユニットテスト用CIジョブ実装 | TASK-309,TASK-311,TASK-317 |
| TASK-319 | ✅ | 統合テスト用CIジョブ実装 | TASK-310,TASK-317 |
| TASK-320 | ✅ | 静的解析(Checkstyle/SpotBugs)用CIジョブ実装 | TASK-312,TASK-313,TASK-317 |
| TASK-321 | ✅ | 依存脆弱性スキャン用CIジョブ実装 | TASK-314,TASK-317 |
| TASK-322 | ✅ | ドキュメントLint(Markdown/OpenAPI/cspell)用CIジョブ実装 | TASK-315,TASK-316,TASK-317,TASK-327 |
| TASK-323 | ✅ | Mavenローカルリポジトリキャッシュとジョブ並列実行設定 | TASK-318,TASK-319,TASK-320,TASK-321,TASK-322 |
| TASK-324 | ✅ | カバレッジレポートのCIアーティファクト保存設定 | TASK-311,TASK-318 |
| TASK-325 | ✅ | PR必須チェック・ブランチ保護設定手順書作成 | TASK-323 |
| TASK-326 | ✅ | テスト基盤・CI利用ガイド作成 | TASK-323,TASK-325 |
| TASK-327 | ✅ | cspell設定ファイル整備とプロジェクト辞書定義 | TASK-301 |

## タスク詳細

### TASK-301

- 補足: 既存テスト(TASK-029〜033, TASK-119〜122, TASK-224〜226)で共通化可能な処理を洗い出し、基盤化方針をレポート化
- 注意: 本タスクでは実装は行わない

### TASK-302

- 補足: 既存マルチモジュール構成にtestkitモジュールを追加、scope=testで他モジュールから参照可能にする
- 注意: 本番モジュールから依存させない

### TASK-306

- 補足: 推論を伴わない固定ベクトルを返すフェイク実装、ONNX Runtimeのロード回避が目的
- 注意: 本番コードのEmbeddingModelインターフェースに準拠

### TASK-309

- 補足: 既存ユニットテストを順次testkit利用に置き換える、テスト挙動は変えない
- 注意: テストロジックの変更や追加は本タスクでは行わない

### TASK-310

- 補足: 既存統合テストを順次testkit利用に置き換える、テスト挙動は変えない
- 注意: テストロジックの変更や追加は本タスクでは行わない

### TASK-317

- 補足: トリガはpush(main)とpull_request、Java 21・Maven前提のセットアップステップを共通化
- 注意: 個別ジョブの実装は後続タスクで行う

### TASK-323

- 補足: actions/cacheによる~/.m2キャッシュ、独立ジョブの並列化、necessaryな依存関係(needs)整理
- 注意: ジョブ間で重複するセットアップを共通化

### TASK-325

- 補足: GitHub上のブランチ保護はコード化不可のため、設定手順をMarkdownで明文化
- 注意: CI側の必須チェック名はTASK-318〜322の実装に合わせる

### TASK-326

- 補足: ローカルでのテスト実行方法、testkit利用方法、CIジョブ一覧と失敗時対応を記載
- 注意: 他フェーズのドキュメントと重複する内容は参照リンクに留める

### TASK-327

- 補足: cspell.jsonとプロジェクト用辞書(技術用語・日本語ローマ字表記等)を整備、対象は本リポジトリ配下のテキスト系ファイル
- 注意: 実行はCI(TASK-322)で行うため、本タスクではMaven連携は行わない

## バックログ一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| BACKLOG-001 | ⏳ | リリースタグ駆動の成果物公開ワークフロー実装 | - |
| BACKLOG-002 | ⏳ | 複数Javaバージョンでのマトリックスビルド実装 | - |
| BACKLOG-003 | ⏳ | ミューテーションテスト(PIT)導入 | - |
| BACKLOG-004 | ⏳ | 性能テスト自動実行のCI組込み | - |
| BACKLOG-005 | ⏳ | ブラウザE2Eテスト(Playwright等)自動化導入 | - |
| BACKLOG-006 | ⏳ | SBOM(CycloneDX)生成とアーティファクト公開 | - |

## バックログ詳細

### BACKLOG-004

- 補足: 既存性能テスト(TASK-034, TASK-123)をCIで定期実行し、回帰検知する
- 注意: CI実行時間が大きく増えるため、別ワークフロー化を想定

### BACKLOG-005

- 補足: TASK-227のブラウザ互換性テストを自動化する後続タスク
- 注意: Phase 3完了後に実施判断
