# TASKS - Phase 5

Milestone: M5 - 大量ファイル一括インデックス取込CLIと将来機能の実装整理
Goal: searchable-cli による一括取込を実装し、MVP外として残っていた検索・運用・UI機能をPhase 5タスクとして着手可能にする

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
| TASK-418 | ⏳ | Phase5拡張機能の仕様差分ADRを作成する | TASK-417 |
| TASK-419 | ⏳ | ファセット検索のAPI仕様とドメインモデルを定義する | TASK-418 |
| TASK-420 | ⏳ | Luceneメタデータ集計によるファセット検索を実装する | TASK-419 |
| TASK-421 | ⏳ | ファセット検索のREST APIと統合テストを追加する | TASK-420 |
| TASK-422 | ⏳ | バックアップ/リストアの保存形式とサービスを実装する | TASK-418 |
| TASK-423 | ⏳ | バックアップ/リストアの操作IFとテストを追加する | TASK-422 |
| TASK-424 | ⏳ | 差分更新用のコンテンツハッシュ管理を実装する | TASK-418 |
| TASK-425 | ⏳ | CLI一括取込に差分更新モードを追加する | TASK-424,TASK-407 |
| TASK-426 | ⏳ | REST APIの認証境界と任意認証方式を設計する | TASK-418 |
| TASK-427 | ⏳ | REST APIの任意認証と運用ドキュメントを追加する | TASK-426 |
| TASK-428 | ⏳ | AI統合のプロバイダSPIと設定モデルを定義する | TASK-418 |
| TASK-429 | ⏳ | 検索結果要約のAI統合サービスを実装する | TASK-428 |
| TASK-430 | ⏳ | React検索UIのビルド構成と画面雛形を作成する | TASK-418 |
| TASK-431 | ⏳ | React検索UIの検索・ページネーションを実装する | TASK-430 |
| TASK-432 | ⏳ | Phase5機能のREADME/OpenAPI/ガイドを更新する | TASK-421,TASK-423,TASK-425,TASK-427,TASK-429,TASK-431 |

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

### TASK-418

- 補足: 仕様書・計画書・レビュー結果を突き合わせ、Phase5で扱うファセット検索、バックアップ/リストア、差分更新、認証、AI統合、React検索UIの範囲をADRとして記録
- 注意: 認証は内部バックエンド利用前提を維持し、外部公開時の任意機能として扱う

### TASK-419

- 補足: フィルタ値、件数集計、AND/OR条件、予約キーの扱いをREST APIとドメインモデルに落とし込む
- 注意: 実装前にOpenAPIと要求仕様の表現を揃える

### TASK-420

- 補足: Luceneに保存済みのメタデータからファセット候補と件数を集計し、SearchResultのaggregationsへ格納する
- 注意: 大量ドキュメント時の全件走査を避ける実装方針を明記する

### TASK-421

- 補足: `/api/v1/search`のレスポンスにファセット集計を含め、正常系・複数値・該当なしの統合テストを追加する
- 注意: 既存検索レスポンスの後方互換性を維持する

### TASK-422

- 補足: Namespaceメタデータ、辞書、Luceneインデックス、設定のバックアップ単位と保存形式を決め、サービス層を実装する
- 注意: 実行中インデックスとの整合性を崩さないスナップショット方式を採用する

### TASK-423

- 補足: 管理UI、REST API、またはCLIのうちPhase5で採用する操作IFを実装し、エクスポート/インポートのテストを追加する
- 注意: 破壊的リストアは明示確認や別名前空間への復元を優先する

### TASK-424

- 補足: DOCUMENT_SOURCEのcontentHashを利用し、同一内容の再取込を判定できるようにする
- 注意: ハッシュ対象は本文、タイトル、メタデータの扱いを仕様化する

### TASK-425

- 補足: `--changed-only`や`--dry-run`相当のオプションを追加し、差分対象件数を出力する
- 注意: 既存BACKLOG-006の内容をPhase5タスクへ昇格する

### TASK-426

- 補足: 内部利用時は認証なし、外部公開時はAPI Key/Bearer Tokenを任意有効化する方針をADR化する
- 注意: デフォルト挙動の変更が必要な場合は影響範囲を明記する

### TASK-427

- 補足: 設定で有効化できる認証フィルタと、リバースプロキシ/API Gateway利用時の運用ガイドを追加する
- 注意: 認証なし運用の前提条件もREADMEとOpenAPIへ明記する

### TASK-428

- 補足: AI要約・統合に必要なプロバイダSPI、タイムアウト、失敗時フォールバック、設定モデルを定義する
- 注意: 外部AIサービス依存は任意設定にし、デフォルトでは無効にする

### TASK-429

- 補足: 検索結果をAIで要約・統合するサービスとAPIレスポンス項目を実装し、スタブプロバイダでテストする
- 注意: 検索自体の成功とAI統合の失敗を分離して扱う

### TASK-430

- 補足: React検索UI用のモジュール構成、ビルド設定、画面ルーティング、APIクライアント雛形を作成する
- 注意: 管理UI(Thymeleaf)とは責務を分け、サンプル実装として独立性を保つ

### TASK-431

- 補足: 検索フォーム、結果一覧、ページネーション、ハイライト表示、基本的なローディング/エラー表示を実装する
- 注意: ファセットUIはTASK-421完了後に同じ画面へ接続できる構成にする

### TASK-432

- 補足: Phase5で追加した機能のREADME、OpenAPI、CLI/UIガイド、セットアップ手順を更新する
- 注意: 実装と異なる将来予定の表現を残さない

## バックログ一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| BACKLOG-001 | ⏳ | filesystemプラグインのParserRegistry連携拡張(PDF/HTML対応) | - |
| BACKLOG-002 | ⏳ | 取込チェックポイント永続化による中断再開機能実装 | - |
| BACKLOG-003 | ⏳ | 並列ワーカープール導入による並列取込実装 | - |
| BACKLOG-004 | ⏳ | REST一括取込エンドポイント(/api/v1/index/import)実装 | - |
| BACKLOG-005 | ⏳ | 取込ジョブの非同期実行とステータス取得API実装 | - |
| BACKLOG-007 | ⏳ | 取込ジョブのcronスケジューラ機能実装 | - |

## バックログ詳細

### BACKLOG-001

- 補足: 既存のParserRegistry(PDFParser/HtmlParser/MarkdownParser等)をプラグイン側で利用し、PluginDocument.contentに解析後テキストを格納
- 注意: 影響範囲がsearchable-pluginsとexamplesに跨るため別タスク化

### BACKLOG-004

- 補足: B1案。CLI(Phase5)と同じオーケストレーションをREST経由でも実行可能にする
- 注意: 長時間ジョブ対策のためBACKLOG-005と合わせて検討
