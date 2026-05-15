# TASKS - Phase 1

## タスク一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| TASK-001 | ✅ | 全文検索エンジン選定調査とPoC実施 | - |
| TASK-002 | ✅ | 軽量DB選定調査とベンチマーク実施 | - |
| TASK-003 | ✅ | 性能目標検証用テストデータ作成と測定 | - |
| TASK-004 | ✅ | Mavenプロジェクト構造作成と依存関係設定 | TASK-001,TASK-002 |
| TASK-005 | ✅ | ドメインモデル実装（Namespace, Document等） | TASK-004 |
| TASK-006 | ✅ | SLF4J+Logback設定とログ基盤実装 | TASK-004 |
| TASK-007 | ✅ | DB接続・初期化処理作成 | TASK-004,TASK-005 |
| TASK-008 | ✅ | Namespace永続化処理実装 | TASK-007 |
| TASK-009 | ✅ | インデックスメタデータ永続化処理実装 | TASK-007 |
| TASK-010 | ✅ | Lucene初期化とインデックス管理基盤作成 | TASK-005,TASK-009 |
| TASK-011 | ✅ | 全文検索インデックス作成・更新処理実装 | TASK-010 |
| TASK-012 | ✅ | 全文検索クエリ処理実装 | TASK-010 |
| TASK-013 | ✅ | PlainTextパーサー実装 | TASK-005 |
| TASK-014 | ✅ | Markdownパーサー実装 | TASK-005 |
| TASK-015 | ✅ | AsciiDocパーサー実装 | TASK-005 |
| TASK-016 | ✅ | プラグインインターフェース定義 | TASK-004 |
| TASK-017 | ✅ | プラグインローダー実装 | TASK-016 |
| TASK-018 | ✅ | サンプルデータソースプラグイン作成 | TASK-017 |
| TASK-019 | ✅ | 設定ファイル読み込み処理実装 | TASK-005 |
| TASK-020 | ✅ | Namespace CRUD操作実装 | TASK-008,TASK-019 |
| TASK-021 | ✅ | Namespace設定管理処理実装 | TASK-020 |
| TASK-022 | ✅ | SearchService実装 | TASK-012,TASK-013,TASK-014,TASK-015 |
| TASK-023 | ✅ | IndexService実装 | TASK-011,TASK-013,TASK-014,TASK-015 |
| TASK-024 | ✅ | NamespaceService実装 | TASK-021 |
| TASK-025 | ✅ | REST APIサーバー基盤作成（Spring Boot） | TASK-004 |
| TASK-026 | ✅ | 検索エンドポイント実装 | TASK-022,TASK-025 |
| TASK-027 | ✅ | インデックス管理エンドポイント実装 | TASK-023,TASK-025 |
| TASK-028 | ✅ | Namespace管理エンドポイント実装 | TASK-024,TASK-025 |
| TASK-029 | ✅ | ドメイン層ユニットテスト作成 | TASK-005 |
| TASK-030 | ✅ | 永続化層ユニットテスト作成 | TASK-008,TASK-009 |
| TASK-031 | ✅ | 検索処理ユニットテスト作成 | TASK-012 |
| TASK-032 | ✅ | Java API統合テスト作成 | TASK-022,TASK-023,TASK-024 |
| TASK-033 | ✅ | REST API統合テスト作成 | TASK-026,TASK-027,TASK-028 |
| TASK-034 | ✅ | 性能テスト実施と目標達成確認 | TASK-033 |
| TASK-035 | ✅ | README作成 | TASK-004 |
| TASK-036 | ✅ | セットアップガイド作成 | TASK-028 |
| TASK-037 | ✅ | REST API仕様書作成（OpenAPI） | TASK-028 |
| TASK-038 | ✅ | UserDictionary ドメインモデル定義（Entry/Scope/Repository） | TASK-005 |
| TASK-039 | ✅ | UserDictionaryResolver（global+namespace マージ） | TASK-038 |
| TASK-040 | ✅ | FileUserDictionaryRepository 実装 | TASK-038 |
| TASK-041 | ✅ | UserDictionaryAnalyzerFactory（Kuromoji 連携） | TASK-039 |
| TASK-042 | ⏳ | JdbcUserDictionaryRepository + スキーマ追加 | TASK-038,TASK-007 |
| TASK-043 | ⏳ | ストレージ切替プロパティ（searchable.dictionary.storage） | TASK-040,TASK-042 |
| TASK-044 | ⏳ | SearchableConfiguration 配線 + AnalyzerFactory 差し替え | TASK-041,TASK-043 |
| TASK-045 | ⏳ | 辞書 REST エンドポイント実装（GET/PUT/DELETE by scope） | TASK-044 |
| TASK-046 | ⏳ | OpenAPI 仕様書更新（辞書エンドポイント） | TASK-045 |
| TASK-047 | ⏳ | AnalyzerFactory 統合テスト（カスタム単語が分かち書きされる） | TASK-044 |
| TASK-048 | ⏳ | 辞書 REST 統合テスト | TASK-045 |
| TASK-049 | ⏳ | 既存検索パイプラインへの性能・互換影響評価 | TASK-044 |
| TASK-050 | ⏳ | ユーザー辞書利用ガイド作成 | TASK-044 |
| TASK-051 | ✅ | ChunkingStrategy ドメイン抽象 + Chunk 型定義 | TASK-005 |
| TASK-052 | ✅ | WholeDocument / FixedSize チャンキング戦略実装 | TASK-051 |
| TASK-053 | ⏳ | Sentence / Paragraph チャンキング戦略実装 | TASK-051 |
| TASK-054 | ⏳ | ParsedDocument 拡張（セクション情報の付与） | TASK-013-015 |
| TASK-055 | ⏳ | SectionChunkingStrategy（見出し単位） | TASK-051,TASK-054 |
| TASK-056 | ⏳ | LuceneIndexer をチャンク前提に変更（sub-doc + parentId） | TASK-052 |
| TASK-057 | ⏳ | 検索: 親集約オプション + sub-doc 直接ヒット | TASK-056 |
| TASK-058 | ⏳ | チャンキング設定切替（global + Namespace 上書き） | TASK-056 |
| TASK-059 | ⏳ | チャンキング統合テスト + 性能影響評価 | TASK-056-058 |
| TASK-060 | ⏳ | チャンキング利用ガイド作成 | TASK-058 |

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

### TASK-038

- 補足: `UserDictionaryEntry`（surface/segmentation/reading/pos）、
  `DictionaryScope`（sealed: Global / Namespace）、
  `UserDictionary`、`UserDictionaryRepository`
- 成果物: `searchable-core/.../domain/dictionary/`

### TASK-039

- 補足: グローバル → Namespace の順でエントリをマージ、
  表層形が重複した場合は Namespace 側を優先（last-wins）
- 成果物: `UserDictionaryResolver`

### TASK-040

- 補足: CSV ファイルベース。`<root>/global.csv` +
  `<root>/namespaces/{id}.csv` の階層
- 成果物: `FileUserDictionaryRepository`

### TASK-041

- 補足: `JapaneseAnalyzer(UserDictionary, mode, stopWords, stopTags)`
  を経由してカスタム辞書を Kuromoji に渡す
- 成果物: `UserDictionaryAnalyzerFactory`

### TASK-042

- 補足: H2 テーブル `USER_DICTIONARY`（scope_key PK / name / entries_csv /
  updated_at）。`schema.sql` 更新
- 成果物: `JdbcUserDictionaryRepository`

### TASK-043

- 補足: `searchable.dictionary.storage=file|db`（default `file`）
- 成果物: `SearchableProperties.Dictionary` 追加

### TASK-045

- 補足: `GET /api/v1/dictionaries`（一覧）, `GET/PUT/DELETE
  /api/v1/dictionaries/{scopeKey}` （scopeKey: `GLOBAL` または `NAMESPACE:id`）
- 成果物: REST コントローラ + DTO

### TASK-049

- 補足: 既存テスト全件再実行、辞書ありなしでの 10万件検索レイテンシ
  測定
- 成果物: 影響評価メモ

### TASK-050

- 補足: CSV フォーマット、スコープ動作、設定方法、API/UI 利用例
- 成果物: `docs/user-dictionary-guide.md`

### TASK-051

- 補足: `Chunk`（id, parentId, ordinal, text, metadata）と
  `ChunkingStrategy.chunk(Document)` 抽象を定義
- 成果物: `core/domain/chunking/`

### TASK-052

- 補足: `WholeDocumentChunkingStrategy`（既定、後方互換）+
  `FixedSizeChunkingStrategy`（chunkSize, overlap 設定可）
- 成果物: 各 Strategy + テスト

### TASK-053

- 補足: 日本語の句点 (。!?) を尊重する Sentence 分割 +
  空行区切り Paragraph 分割
- 成果物: 各 Strategy + テスト

### TASK-054

- 補足: 既存パーサ（PlainText/Markdown/AsciiDoc/HTML/PDF）が見出し
  位置情報を `Section` レコードとして返すよう拡張
- 成果物: `ParsedDocument.sections` 追加、各パーサ更新

### TASK-055

- 補足: `ParsedDocument.sections` を利用したセクション単位チャンキング
- 成果物: `SectionChunkingStrategy`

### TASK-056

- 補足: 1 ドメイン Document → N 個の Lucene sub-doc に展開。`parentId`
  と `chunkOrdinal` フィールドを追加、削除は parentId による一括削除
- 成果物: `LuceneIndexer` / `LuceneFields` 改修

### TASK-057

- 補足: 検索結果で sub-doc が複数返った場合、`groupBy=parent` オプション
  で親に集約する。既存の単一ベクトル動作も互換維持
- 成果物: `LuceneFullTextSearcher` / `LuceneVectorSearcher` 改修

### TASK-058

- 補足: `searchable.chunking.strategy=whole|fixed|sentence|paragraph|section`
  と、サイズ/overlap パラメータ。Namespace 設定で上書き
- 成果物: `SearchableProperties.Chunking`, `NamespaceConfigPatch` 拡張

### TASK-059

- 補足: 既存テスト互換（WholeDocument 既定）を確認、10万件で
  FixedSize/Section の性能影響測定
- 成果物: 性能評価レポート

### TASK-060

- 補足: 戦略選択ガイド、設定方法、トレードオフ
- 成果物: `docs/chunking-guide.md`
