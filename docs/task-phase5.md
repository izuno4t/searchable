# TASKS - Phase 5

Milestone: M5 - 大量ファイル一括インデックス取込CLI
Goal: searchable-cli モジュールを新設し、DataSourcePlugin を駆動するファイル群の一括インデックス取込を CLI から実行可能にする

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
| TASK-401 | ⏳ | CLIモジュール設計と一括取込フロー方針レポート作成 | - |
| TASK-402 | ⏳ | searchable-cli Mavenモジュール新規作成と基本依存設定 | TASK-401 |
| TASK-403 | ⏳ | picocli導入とCLIエントリポイント雛形実装 | TASK-402 |
| TASK-404 | ⏳ | CLIアプリケーションブートストラップ実装(DI結線・設定読込) | TASK-402 |
| TASK-405 | ⏳ | importサブコマンドのオプション・引数定義実装 | TASK-403 |
| TASK-406 | ⏳ | PluginContext構築ヘルパー(CLI引数→config map変換)実装 | TASK-404 |
| TASK-407 | ⏳ | 取込実行コア(plugin.fetch→IndexService.indexBatch結線)実装 | TASK-404,TASK-405,TASK-406 |
| TASK-408 | ⏳ | バッチサイズ指定とチャンク分割処理実装 | TASK-407 |
| TASK-409 | ⏳ | 取込進捗・件数・所要時間ログ出力実装 | TASK-407 |
| TASK-410 | ⏳ | エラー時のスキップ/中断モード切替実装 | TASK-407 |
| TASK-411 | ⏳ | list-pluginsサブコマンド実装 | TASK-403,TASK-404 |
| TASK-412 | ⏳ | CLIユニットテスト作成 | TASK-407,TASK-408,TASK-409,TASK-410,TASK-411 |
| TASK-413 | ⏳ | filesystemプラグインを用いたCLI統合テスト作成 | TASK-407,TASK-412 |
| TASK-414 | ⏳ | 実行可能fat-jarビルド設定とMavenプラグイン組込み | TASK-402,TASK-413 |
| TASK-415 | ⏳ | CLI起動シェルスクリプトとヘルプメッセージ整備 | TASK-414 |
| TASK-416 | ⏳ | CLI利用ガイド(docs/cli-guide.md)作成 | TASK-414,TASK-415 |
| TASK-417 | ⏳ | READMEとセットアップガイドへのCLIセクション追記 | TASK-416 |

## タスク詳細

### TASK-401

- 補足: コマンド体系(import/list-plugins等)、設定読込元、ログ出力方針、配布形態(fat-jar/zip)を明文化
- 注意: 本タスクでは実装は行わない

### TASK-402

- 補足: 既存マルチモジュール構成にsearchable-cliを追加、searchable-core/searchable-pluginsに依存
- 注意: searchable-api/searchable-uiには依存させない

### TASK-403

- 補足: picocli採用、サブコマンド構造(main → import/list-plugins)を雛形化
- 注意: 個別サブコマンドのオプション定義は後続タスクで行う

### TASK-404

- 補足: NamespaceRepository・IndexService・LuceneIndexProvider・PluginLoader等の生成・配線をCLI起動時に行う
- 注意: searchable-apiのSpring Boot設定をそのまま流用しない、CLI専用に最小構成で組む

### TASK-405

- 補足: --plugin <name> --namespace <id> --config <key=value> 等のオプション定義
- 注意: 設定はCLI引数とYAMLファイル両対応にする

### TASK-407

- 補足: plugin.fetch(context)のStreamを消費し、batch単位でIndexService.indexBatchへ流す
- 注意: PluginDocumentからDocumentへの変換はPluginContext経由の既存ロジックに合わせる

### TASK-408

- 補足: --batch-size指定でindexBatch呼び出し単位を制御、デフォルト値はガイドに明記
- 注意: 並列実行はBacklogで扱う

### TASK-410

- 補足: --on-error <skip|abort>でドキュメント単位のエラー時挙動を切替
- 注意: skip時は失敗ドキュメントのID一覧を末尾に集約出力

### TASK-413

- 補足: 一時ディレクトリにテキストファイルを生成しCLIをinvokeして検証
- 注意: 既存searchable-testkit(TASK-302)のヘルパーを活用

### TASK-414

- 補足: maven-shade-plugin等でfat-jar生成、`java -jar searchable-cli.jar`で起動可能にする
- 注意: 実行時に外部プラグインjarを-Dsearchable.plugins.dirで参照可能にする

### TASK-415

- 補足: bin/searchableシェル、Windows向けbat、--helpの整形
- 注意: 起動スクリプトはJAVA_HOME/JAVA_OPTSの上書きを許可

### TASK-417

- 補足: 既存README・setup-guide.mdへCLI起動・import例の節を追加
- 注意: 詳細はcli-guide.mdへの参照リンクに留め重複を避ける

## バックログ一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| BACKLOG-001 | ⏳ | filesystemプラグインのParserRegistry連携拡張(PDF/HTML対応) | - |
| BACKLOG-002 | ⏳ | 取込チェックポイント永続化による中断再開機能実装 | - |
| BACKLOG-003 | ⏳ | 並列ワーカープール導入による並列取込実装 | - |
| BACKLOG-004 | ⏳ | REST一括取込エンドポイント(/api/v1/index/import)実装 | - |
| BACKLOG-005 | ⏳ | 取込ジョブの非同期実行とステータス取得API実装 | - |
| BACKLOG-006 | ⏳ | dry-runおよびSHA256差分取込モード実装 | - |
| BACKLOG-007 | ⏳ | 取込ジョブのcronスケジューラ機能実装 | - |

## バックログ詳細

### BACKLOG-001

- 補足: 既存のParserRegistry(PDFParser/HtmlParser/MarkdownParser等)をプラグイン側で利用し、PluginDocument.contentに解析後テキストを格納
- 注意: 影響範囲がsearchable-pluginsとexamplesに跨るため別タスク化

### BACKLOG-004

- 補足: B1案。CLI(Phase5)と同じオーケストレーションをREST経由でも実行可能にする
- 注意: 長時間ジョブ対策のためBACKLOG-005と合わせて検討
