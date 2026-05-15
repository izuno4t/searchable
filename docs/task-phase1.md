# TASKS - Phase 1

## タスク一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| TASK-001 | ⏳ | 全文検索エンジン選定調査とPoC実施 | - |
| TASK-002 | ⏳ | 軽量DB選定調査とベンチマーク実施 | - |
| TASK-003 | ⏳ | 性能目標検証用テストデータ作成と測定 | - |
| TASK-004 | ⏳ | Mavenプロジェクト構造作成と依存関係設定 | TASK-001,TASK-002 |
| TASK-005 | ⏳ | ドメインモデル実装（Namespace, Document等） | TASK-004 |
| TASK-006 | ⏳ | SLF4J+Logback設定とログ基盤実装 | TASK-004 |
| TASK-007 | ⏳ | DB接続・初期化処理作成 | TASK-004,TASK-005 |
| TASK-008 | ⏳ | Namespace永続化処理実装 | TASK-007 |
| TASK-009 | ⏳ | インデックスメタデータ永続化処理実装 | TASK-007 |
| TASK-010 | ⏳ | Lucene初期化とインデックス管理基盤作成 | TASK-005,TASK-009 |
| TASK-011 | ⏳ | 全文検索インデックス作成・更新処理実装 | TASK-010 |
| TASK-012 | ⏳ | 全文検索クエリ処理実装 | TASK-010 |
| TASK-013 | ⏳ | PlainTextパーサー実装 | TASK-005 |
| TASK-014 | ⏳ | Markdownパーサー実装 | TASK-005 |
| TASK-015 | ⏳ | AsciiDocパーサー実装 | TASK-005 |
| TASK-016 | ⏳ | プラグインインターフェース定義 | TASK-004 |
| TASK-017 | ⏳ | プラグインローダー実装 | TASK-016 |
| TASK-018 | ⏳ | サンプルデータソースプラグイン作成 | TASK-017 |
| TASK-019 | ⏳ | 設定ファイル読み込み処理実装 | TASK-005 |
| TASK-020 | ⏳ | Namespace CRUD操作実装 | TASK-008,TASK-019 |
| TASK-021 | ⏳ | Namespace設定管理処理実装 | TASK-020 |
| TASK-022 | ⏳ | SearchService実装 | TASK-012,TASK-013,TASK-014,TASK-015 |
| TASK-023 | ⏳ | IndexService実装 | TASK-011,TASK-013,TASK-014,TASK-015 |
| TASK-024 | ⏳ | NamespaceService実装 | TASK-021 |
| TASK-025 | ⏳ | REST APIサーバー基盤作成（Spring Boot） | TASK-004 |
| TASK-026 | ⏳ | 検索エンドポイント実装 | TASK-022,TASK-025 |
| TASK-027 | ⏳ | インデックス管理エンドポイント実装 | TASK-023,TASK-025 |
| TASK-028 | ⏳ | Namespace管理エンドポイント実装 | TASK-024,TASK-025 |
| TASK-029 | ⏳ | ドメイン層ユニットテスト作成 | TASK-005 |
| TASK-030 | ⏳ | 永続化層ユニットテスト作成 | TASK-008,TASK-009 |
| TASK-031 | ⏳ | 検索処理ユニットテスト作成 | TASK-012 |
| TASK-032 | ⏳ | Java API統合テスト作成 | TASK-022,TASK-023,TASK-024 |
| TASK-033 | ⏳ | REST API統合テスト作成 | TASK-026,TASK-027,TASK-028 |
| TASK-034 | ⏳ | 性能テスト実施と目標達成確認 | TASK-033 |
| TASK-035 | ⏳ | README作成 | TASK-004 |
| TASK-036 | ⏳ | セットアップガイド作成 | TASK-028 |
| TASK-037 | ⏳ | REST API仕様書作成（OpenAPI） | TASK-028 |

## タスク詳細

### TASK-001

- 補足: Apache Lucene + 日本語形態素解析器（Kuromoji vs Sudachi）の比較検証
- 成果物: 選定レポート、簡易PoCコード（日本語サンプルデータで検証）
- 期限: Phase 1開始前1週間

### TASK-002

- 補足: H2, SQLite, RocksDBの性能・機能比較
- 成果物: 選定レポート、ベンチマーク結果
- 期限: Phase 1開始前3日

### TASK-003

- 補足: 100,000件規模のテストデータで500ms以内を確認
- 成果物: 性能測定レポート、テストデータ
- 期限: Phase 1開始前

### TASK-004

- 補足: マルチモジュール構成（core, api, plugins）
- 成果物: pom.xml、ディレクトリ構造

### TASK-005

- 補足: Namespace, Document, SearchRequest, SearchResult等
- 成果物: ドメインモデルクラス群

### TASK-010

- 補足: LuceneのDirectoryとIndexWriterの初期化
- 成果物: IndexManager基盤クラス

### TASK-016

- 補足: DataSourcePluginインターフェース定義
- 成果物: プラグインAPIインターフェース

### TASK-019

- 補足: YAML形式の設定ファイル対応
- 成果物: ConfigLoader実装

### TASK-025

- 補足: Spring Boot組み込みTomcatで起動
- 成果物: REST APIサーバー基盤

### TASK-034

- 補足: 10万件で500ms以内のレスポンスを確認
- 成果物: 性能測定レポート
