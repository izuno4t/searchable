# TASKS

Milestone: M-v3.3
Goal: 要件書 v3.3 を充足する Searchable 一式(ライブラリ・運用 Web・サンプル群)を実装する

## ワークフロールール

- タスク開始時にステータスを 🚧 に更新する
- タスク完了時にステータスを ✅ に更新する
- DependsOn のタスクが全て ✅ になるまで開始しない
- タスク着手時にまず実装済みかを確認し、既存実装で要件を満たす場合は内容を確認して ✅ または 🚫 にする

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
| TASK-001 | ✅ | ドメインモデル(Document/Chunk/Namespace/SearchRequest/SearchResult)定義 | - |
| TASK-002 | ✅ | Java API インターフェース(SearchService/IndexService/NamespaceService/AdminService)定義 | TASK-001 |
| TASK-003 | ✅ | 設定モデル(GlobalConfig/NamespaceConfig/EmbeddingConfig/AIConfig)定義 | TASK-001 |
| TASK-004 | ✅ | SearchableLibrary ファサードビルダ実装 | TASK-002,TASK-003 |
| TASK-005 | 🚫 | Lucene Directory バックエンドの抽象は不要(Lucene 既存の `Directory` で十分、SPI 化のメリットなしと判断) | TASK-001 |
| TASK-006 | ✅ | ローカルファイルシステム Directory バックエンド実装(`MMapDirectory` 直接使用、`LuceneIndexProvider` 内で生成) | - |
| TASK-007 | ✅ | インメモリ Directory バックエンド実装(`ByteBuffersDirectory` 直接使用、`LuceneIndexProvider` 内で設定により切替) | TASK-006 |
| TASK-008 | 🚫 | S3 互換 Directory バックエンドは実装しない(性能要件 500ms p95 と整合せず、永続性は既存バックアップ機能で代替可能) | - |
| TASK-009 | ✅ | メタデータ DB JDBC 接続抽象(URL ベース)定義 | TASK-001 |
| TASK-010 | ✅ | H2 (ファイル/インメモリ)バックエンド統合 | TASK-009 |
| TASK-011 | ✅ | PostgreSQL / TCP RDB バックエンド統合 | TASK-009 |
| TASK-012 | ✅ | ストレージ設定スキーマと application.properties 取込 | TASK-006,TASK-007,TASK-008,TASK-010,TASK-011 |
| TASK-013 | ✅ | ストレージバックエンドのユニット・統合テスト(Testcontainers) | TASK-012 |
| TASK-014 | ✅ | Namespace リポジトリ(JDBC) 実装 | TASK-010 |
| TASK-015 | ✅ | NamespaceService CRUD 実装 | TASK-014 |
| TASK-016 | ✅ | Namespace 設定マージロジック(グローバル+個別)実装 | TASK-003,TASK-015 |
| TASK-017 | ✅ | Namespace 単位インデックスディレクトリ分離実装 | TASK-006,TASK-015 |
| TASK-018 | ✅ | indexWeight 適用ロジック実装 | TASK-016 |
| TASK-019 | ✅ | Namespace 管理機能ユニットテスト | TASK-015,TASK-016,TASK-017,TASK-018 |
| TASK-020 | ✅ | Lucene IndexWriter ライフサイクル管理(Namespace 単位)実装 | TASK-017 |
| TASK-021 | ✅ | Lucene IndexReader / Searcher プロバイダ実装 | TASK-017 |
| TASK-022 | ✅ | Kuromoji ベース JapaneseAnalyzer 実装 | TASK-020 |
| TASK-023 | ✅ | Sudachi ベース JapaneseAnalyzer 実装(設定で切替) | TASK-020 |
| TASK-024 | ✅ | 全文検索クエリビルダ(フィールド指定/ファジー/ワイルドカード)実装 | TASK-021,TASK-022 |
| TASK-025 | ✅ | ハイライタ実装(日本語対応・`<mark>` 出力) | TASK-024 |
| TASK-026 | ✅ | 全文検索サービスのユニットテスト | TASK-024,TASK-025 |
| TASK-027 | ✅ | 見出し自動ブースト(h1=7.0..h6=2.0)実装 | TASK-024 |
| TASK-028 | ✅ | カスタムセクション重み(0.0〜10.0)設定 API 実装 | TASK-027 |
| TASK-029 | ✅ | 二次関数スケーリング(weight 2.0→約4倍)実装 | TASK-027 |
| TASK-030 | ✅ | コンテンツ重み付けのユニットテスト | TASK-027,TASK-028,TASK-029 |
| TASK-031 | ✅ | Kuromoji UserDictionary 形式パーサ実装 | TASK-022 |
| TASK-032 | ✅ | ユーザー辞書ファイルストレージ実装 | TASK-031 |
| TASK-033 | ✅ | ユーザー辞書 DB ストレージ実装 | TASK-031,TASK-014 |
| TASK-034 | ✅ | グローバル+Namespace 個別辞書マージロジック実装 | TASK-032,TASK-033 |
| TASK-035 | ✅ | 辞書変更の新規・再構築時反映処理実装 | TASK-034 |
| TASK-036 | ✅ | ユーザー辞書管理機能のユニットテスト | TASK-034,TASK-035 |
| TASK-037 | ✅ | ONNX Runtime 組込とモデルローダ実装 | TASK-020 |
| TASK-038 | ✅ | 埋め込みモデル選択設定(multilingual-e5 等)実装 | TASK-037 |
| TASK-039 | ✅ | テキストベクトル化サービス実装 | TASK-038 |
| TASK-040 | ✅ | Lucene HNSW ベクトルインデックス管理実装 | TASK-021,TASK-039 |
| TASK-041 | ✅ | ベクトル検索クエリ実行・類似度計算実装 | TASK-040 |
| TASK-042 | ✅ | ベクトル検索のユニットテスト | TASK-041 |
| TASK-043 | ✅ | ベクトル検索の性能ベンチマーク(500ms 目標) | TASK-041 |
| TASK-044 | ✅ | シーケンシャル検索戦略実装(順序設定対応) | TASK-026,TASK-041 |
| TASK-045 | ✅ | パラレル検索戦略実装(同時実行マージ) | TASK-026,TASK-041 |
| TASK-046 | ✅ | ハイブリッド検索結果マージ・スコア統合実装 | TASK-044,TASK-045 |
| TASK-047 | ✅ | ハイブリッド検索のユニットテスト | TASK-044,TASK-045,TASK-046 |
| TASK-048 | ✅ | ページネーション実装 | TASK-026 |
| TASK-049 | ✅ | Sub-results(セクション単位)データモデル定義 | TASK-001 |
| TASK-050 | ✅ | Sub-results 検索・スコアリング実装 | TASK-049,TASK-024 |
| TASK-051 | ✅ | Sub-results アンカー付き URL 生成実装 | TASK-050 |
| TASK-052 | ✅ | スニペット自動生成実装(HTML/プレーン両形式) | TASK-025 |
| TASK-053 | ✅ | スニペット長設定とマークアップエンコード実装 | TASK-052 |
| TASK-054 | ✅ | ファセット集計(値・件数)実装 | TASK-024 |
| TASK-055 | ✅ | ファセット複数値フィルタ(AND/OR)と予約キー対応 | TASK-054 |
| TASK-056 | ✅ | ファセット指定3方式(インライン/属性値/要素内容)実装 | TASK-055 |
| TASK-057 | ✅ | BM25 パラメータ Namespace/リクエスト単位上書き実装 | TASK-024 |
| TASK-058 | ✅ | metaWeights 適用ロジック実装 | TASK-057 |
| TASK-059 | ✅ | 遅延ロード結果(ID+スコア+URL のみ)対応実装 | TASK-048 |
| TASK-060 | ✅ | 検索結果整形機能のユニットテスト | TASK-050,TASK-053,TASK-056,TASK-058,TASK-059 |
| TASK-061 | ✅ | プレーンテキストパーサ実装 | TASK-001 |
| TASK-062 | ✅ | Markdown パーサ実装 | TASK-001 |
| TASK-063 | ✅ | AsciiDoc パーサ実装 | TASK-001 |
| TASK-064 | ✅ | PDF パーサ(PDFBox)実装 | TASK-001 |
| TASK-065 | ✅ | HTML パーサ(Jsoup)実装 | TASK-001 |
| TASK-066 | ✅ | チャンキング戦略(文/段落/セクション/全体/固定長)実装 | TASK-061,TASK-062,TASK-063,TASK-064,TASK-065 |
| TASK-067 | ✅ | コンテンツハッシュベース変更検知実装 | TASK-066 |
| TASK-068 | ✅ | 非同期インデックス更新キュー実装 | TASK-020 |
| TASK-069 | ✅ | バッチ更新 API 実装 | TASK-068,TASK-066 |
| TASK-070 | ✅ | 差分更新(再投入による全置換)動作確認とテスト | TASK-069 |
| TASK-071 | ✅ | バックアップサービス(スナップショット)実装 | TASK-020,TASK-014 |
| TASK-072 | ✅ | リストアサービス実装 | TASK-071 |
| TASK-073 | ✅ | 自動バックアップスケジューラ実装 | TASK-071 |
| TASK-074 | ✅ | Namespace 単位 write.lock 動作確認テスト | TASK-020 |
| TASK-075 | ✅ | 異なる Namespace 並列書込統合テスト | TASK-074 |
| TASK-076 | ✅ | SearchableLibrary 読込専用モード(readOnly=true)実装 | TASK-004,TASK-021 |
| TASK-077 | ✅ | メタデータ DB 読込専用接続モード対応 | TASK-076 |
| TASK-078 | ✅ | インデックス管理機能のユニットテスト | TASK-069,TASK-071,TASK-072,TASK-076 |
| TASK-079 | ✅ | DataSourcePlugin SPI 定義 | TASK-001 |
| TASK-080 | ✅ | プラグインローダ(JAR スキャン・動的クラスロード)実装 | TASK-079 |
| TASK-081 | ✅ | プラグインライフサイクル管理実装 | TASK-080 |
| TASK-082 | ✅ | プラグイン機構のユニットテスト | TASK-080,TASK-081 |
| TASK-083 | ✅ | searchable-core 内蔵 filesystem DataSource プラグイン実装 | TASK-079 |
| TASK-084 | ✅ | AiProvider SPI 設計と定義 | TASK-001 |
| TASK-085 | ⏳ | OpenAI プロバイダ実装 | TASK-084 |
| TASK-086 | ⏳ | Anthropic プロバイダ実装 | TASK-084 |
| TASK-087 | ⏳ | Ollama プロバイダ実装 | TASK-084 |
| TASK-088 | ⏳ | 検索結果要約・統合サービス実装 | TASK-085,TASK-086,TASK-087 |
| TASK-089 | ⏳ | AI 統合タイムアウト・フォールバック制御実装 | TASK-088 |
| TASK-090 | ⏳ | AI 統合設定モデルと application.properties 取込 | TASK-088 |
| TASK-091 | ⏳ | AI 統合ユニットテスト(スタブプロバイダ) | TASK-088,TASK-089 |
| TASK-092 | ✅ | picocli ベースの CLI エントリポイント実装 | TASK-004 |
| TASK-093 | ✅ | CLI 起動時 DI 結線実装 | TASK-092 |
| TASK-094 | ✅ | ingest サブコマンド(単一・バッチ・プラグイン経由)実装 | TASK-093,TASK-069,TASK-080 |
| TASK-095 | ✅ | delete サブコマンド実装 | TASK-093 |
| TASK-096 | ✅ | rebuild サブコマンド実装 | TASK-093 |
| TASK-097 | ✅ | status サブコマンド実装 | TASK-093 |
| TASK-098 | ✅ | backup / restore サブコマンド実装 | TASK-093,TASK-072 |
| TASK-099 | ✅ | list-plugins サブコマンド実装 | TASK-093,TASK-080 |
| TASK-100 | ✅ | 設定検証(ドライラン)サブコマンド実装 | TASK-093 |
| TASK-101 | ✅ | CLI 起動シェルスクリプトとヘルプ整備 | TASK-094,TASK-095,TASK-096,TASK-097,TASK-098,TASK-099,TASK-100 |
| TASK-102 | ✅ | CLI のユニット・統合テスト | TASK-101 |
| TASK-103 | ✅ | Spring Boot + Thymeleaf アプリケーションエントリ(searchable-admin) | TASK-004 |
| TASK-104 | ✅ | Namespace 管理画面(一覧/作成/編集/削除)実装 | TASK-103,TASK-015 |
| TASK-105 | ✅ | ドキュメントパス/インデックスパス設定画面実装 | TASK-104 |
| TASK-106 | ✅ | インデックス管理画面(状態・更新・再構築・部分削除)実装 | TASK-103,TASK-069,TASK-021 |
| TASK-107 | ✅ | ユーザー辞書管理画面実装 | TASK-103,TASK-034 |
| TASK-108 | ✅ | ランキング設定画面(BM25/metaWeights/indexWeight)実装 | TASK-103,TASK-057,TASK-058,TASK-018 |
| TASK-109 | ⏳ | AI 統合設定画面(プロバイダ・API キー・モデル)実装 | TASK-103,TASK-090 |
| TASK-110 | ✅ | バックアップ設定画面実装 | TASK-103,TASK-073 |
| TASK-111 | ✅ | モニタリングダッシュボード実装 | TASK-103,TASK-021 |
| TASK-112 | ✅ | searchable-admin の権限管理設計ドキュメント作成 | TASK-103 |
| TASK-113 | ✅ | searchable-admin の統合テスト | TASK-104,TASK-105,TASK-106,TASK-107,TASK-108,TASK-109,TASK-110,TASK-111 |
| TASK-114 | ✅ | examples/webapp の Spring Boot エントリ実装 | TASK-004 |
| TASK-115 | ✅ | examples/webapp 起動時バッチ取込実装 | TASK-114,TASK-094 |
| TASK-116 | ✅ | examples/webapp 検索ページ(Thymeleaf)実装 | TASK-114,TASK-046 |
| TASK-117 | ✅ | examples/webapp ドキュメント詳細ページ実装 | TASK-116,TASK-051 |
| TASK-118 | ✅ | examples/webapp の統合テスト | TASK-115,TASK-117 |
| TASK-119 | ✅ | examples/webapp 利用ガイド整備(README.md) | TASK-118 |
| TASK-120 | ✅ | examples/api の Spring Boot エントリ実装 | TASK-004 |
| TASK-121 | ✅ | examples/api 検索 API(POST /api/v1/search)実装 | TASK-120,TASK-046,TASK-060 |
| TASK-122 | ✅ | examples/api ドキュメント参照 API(GET /documents/{id})実装 | TASK-120,TASK-059 |
| TASK-123 | ✅ | examples/api インデックス管理 API 実装 | TASK-120,TASK-069 |
| TASK-124 | ✅ | examples/api Namespace 管理 API 実装 | TASK-120,TASK-015 |
| TASK-125 | ✅ | examples/api 管理 API(status/metrics/backup/restore)実装 | TASK-120,TASK-072,TASK-073 |
| TASK-126 | ✅ | API Key 認証フィルタ実装(設定で有効化) | TASK-120 |
| TASK-127 | ✅ | examples/api CORS 設定実装(許可オリジン設定可) | TASK-120 |
| TASK-128 | ✅ | OpenAPI 仕様生成と Swagger UI 同梱 | TASK-121,TASK-122,TASK-123,TASK-124,TASK-125 |
| TASK-129 | ✅ | examples/api の統合テスト | TASK-121,TASK-122,TASK-123,TASK-124,TASK-125,TASK-126,TASK-127 |
| TASK-130 | ✅ | examples/api 利用ガイド整備(README.md) | TASK-128,TASK-129 |
| TASK-131 | ✅ | examples/search-ui index.html と検索ボックス実装 | TASK-121 |
| TASK-132 | ✅ | examples/search-ui 検索 JS(デバウンス・AbortController)実装 | TASK-131 |
| TASK-133 | ✅ | examples/search-ui ファセット UI 実装 | TASK-132,TASK-056 |
| TASK-134 | ✅ | examples/search-ui ハイライト・スニペット安全描画実装 | TASK-132,TASK-053 |
| TASK-135 | ✅ | examples/search-ui ページネーション実装 | TASK-132,TASK-048 |
| TASK-136 | ✅ | examples/search-ui スタイル整備(CSS) | TASK-131 |
| TASK-137 | ✅ | examples/search-ui 利用ガイド整備(README.md) | TASK-133,TASK-134,TASK-135,TASK-136 |
| TASK-138 | ✅ | examples/mcp サーバーアプリケーションエントリ実装 | TASK-004 |
| TASK-139 | ✅ | examples/mcp search_documents ツール実装 | TASK-138,TASK-046 |
| TASK-140 | ✅ | examples/mcp get_document ツール実装 | TASK-138,TASK-059 |
| TASK-141 | ✅ | examples/mcp stdio モード対応実装 | TASK-139,TASK-140 |
| TASK-142 | ✅ | examples/mcp SSE モード対応実装 | TASK-139,TASK-140 |
| TASK-143 | ✅ | examples/mcp API Key 認証(SSE)実装 | TASK-142 |
| TASK-144 | ✅ | examples/mcp Claude Desktop 設定例とガイド作成 | TASK-141 |
| TASK-145 | ✅ | examples/mcp の統合テスト | TASK-141,TASK-142,TASK-143 |
| TASK-146 | ✅ | SLF4J + Logback 構造化ログ整備(全モジュール) | TASK-004 |
| TASK-147 | ✅ | 検索性能テスト整備(500ms 目標、100k 件、単一 Namespace) | TASK-046 |
| TASK-148 | ✅ | プロジェクト README 整備 | TASK-119,TASK-130,TASK-137,TASK-145 |
| TASK-149 | ✅ | docs/getting-started.ja.md 整備 | TASK-148 |
| TASK-150 | ✅ | docs/usage.ja.md 整備 | TASK-148 |
| TASK-151 | ✅ | docs/setup-guide.ja.md 整備 | TASK-148 |
| TASK-152 | ✅ | docs/architecture.md 整備 | TASK-148 |
| TASK-153 | ✅ | examples/api/api-specification.ja.md と examples/api/openapi.yaml 整備 | TASK-128 |
| TASK-154 | ✅ | docs/cli-guide.ja.md 整備 | TASK-101 |
| TASK-155 | ✅ | docs/admin-guide.ja.md 整備 | TASK-113 |
| TASK-156 | ✅ | `examples/plugin-datasource-s3` として S3 互換ストレージ取込のリファレンスプラグイン実装を追加(本体ビルド対象外の独立プロジェクト、`DataSourcePlugin` SPI 実装) | TASK-079,TASK-083 |
| TASK-157 | ✅ | 文書レベル metadata を Lucene stored field から専用の **metadata DB** に移管する仕様策定。検索結果は post-search で enrich する。`metadata.url` を URI 必須の予約キーとして `docs/architecture.md` / `docs/usage.ja.md` に明文化 | TASK-150,TASK-152 |
| TASK-158 | ✅ | `searchable-cli` `IngestCommand` で `metadata.url = path.toUri().toString()` を自動設定 | TASK-157,TASK-168 |
| TASK-159 | ✅ | `examples/webapp` `StartupIngestRunner` で `metadata.url` を自動設定し詳細ページに元ファイルリンクを表示 | TASK-157,TASK-168 |
| TASK-160 | ✅ | `examples/api` OpenAPI と `api-specification.ja.md` に `metadata.url` 規約を反映(reserved key 説明追加、生パス禁止) | TASK-153,TASK-157 |
| TASK-161 | ✅ | `examples/plugin-datasource-s3` の取込で `metadata.url = s3://bucket/key` を設定 | TASK-157,TASK-168 |
| TASK-162 | ✅ | `examples/search-ui` の `renderHit` を `hit.metadata.url` でリンク化 | TASK-157,TASK-169 |
| TASK-163 | ✅ | `ResultMerger.withScore` / `intersect` バグ修正: ハイブリッド経由で `SearchHit.subResults` が取りこぼされる問題を解消 | TASK-051 |
| TASK-164 | ✅ | ベクトル検索/ハイブリッド検索でのセクション anchor 方針を確定(`LuceneVectorSearcher` で SubResult を返すか、明示的に full-text 限定と明文化するか) — full-text 限定とすることを `docs/architecture.md` §5.7 と `docs/usage.ja.md` に明記 | TASK-051,TASK-157 |
| TASK-165 | ✅ | `examples/webapp` と `examples/api` の `pom.xml` から不要な `<classifier>boot</classifier>` を除去し、二重 repackage による起動時 StackOverflowError(`Start-Class: JarLauncher` の無限再帰)を修正 | TASK-119,TASK-130 |
| TASK-166 | ✅ | `examples/*` の README に Quick start(インデックス→検索)節を追加し、`examples/` 配下が独立 Maven プロジェクトであることを明記。あわせて searchable-cli 連携セクションで「ソースディレクトリと index ディレクトリは別物」を明示 | TASK-119,TASK-130,TASK-137,TASK-145 |
| TASK-167 | ✅ | `DocumentMetadataRepository` 実装(JDBC、H2/PostgreSQL 両対応)。PK は自然キー `(namespace_id, document_id)`(surrogate key は採らない)。スキーマ: `title` + `metadata_json` + `indexed_at` を保持する文書レジストリ。`DocumentSourceRepository`(change-detection 用)とは責務分離 | TASK-010,TASK-011,TASK-157 |
| TASK-168 | ✅ | `LuceneDocumentMapper` から `METADATA_JSON` および冗長な `NAMESPACE_ID` stored field を除去(Lucene index は既に Directory 単位で namespace 分割されているため)し、`IndexService` で `DocumentMetadataRepository` に書込むよう変更。`PARENT_ID` / `CHUNK_METADATA_JSON` / `INDEXED_AT_EPOCH` は残す | TASK-167 |
| TASK-169 | ✅ | `SearchService`(または新規 `SearchResultEnricher`)に metadata enrich 処理を実装。Lucene 検索後にバッチ `IN` クエリで metadata を一括取得し `SearchHit.metadata` に注入。`LuceneFullTextSearcher.toSubResult` の `anchorUrl` 生成ロジックを enricher 側に移管 | TASK-167,TASK-168 |
| TASK-170 | ✅ | `DocumentBrowser` を `DocumentMetadataRepository` ベースに書き換え。Lucene `MatchAllDocsQuery` 由来のチャンク重複・`totalHits` 不正・ソート/フィルタ不足を解消。`webapp/SearchController.detail` の `filter` workaround も同時に解消 | TASK-167 |
| TASK-171 | ✅ | 既存 Lucene index との互換性方針確定(rebuild 推奨、metadata は新仕様で再取込必須)+ マイグレーションノートを `docs/setup-guide.md` §8 に記載 | TASK-168,TASK-169,TASK-170 |
| TASK-172 | ⏳ | `DOCUMENT_SOURCE` テーブルを廃止し `DOCUMENT_METADATA` に source 系列(`SOURCE_TYPE` / `SOURCE_LOCATION` / `CONTENT_HASH` / `SOURCE_UPDATED`)を統合。`DocumentSourceRepository` は `DocumentMetadataRepository` に責務を吸収し、`IndexService.delete()` / `rebuild()` の source 行残置(変更検知で全件スキップされる潜在バグ)を同時解消 | TASK-167,TASK-168,TASK-169,TASK-170 |
| TASK-173 | ⏳ | インデックスの命名規約ベースのバージョニング導入。`<root>/<namespaceId>/<timestamp>/` を確定版、`<timestamp>.tmp/` をビルド中とし、完了時に atomic rename。読み手は `.tmp` を除外して最新 timestamp を採用。`IndexLayout` を「読み手用: 最新完成版を返す」「書き手用: 新規 timestamp dir を生成する」の2系統に分割、`LuceneIndexProvider#contexts` のキーを `(namespaceId, version)` に拡張、`BackupService` / `RestoreService` も追従。GC は `SearcherManager` 寿命に応じた ref-count + grace period で旧版を遅延削除。timestamp は wall clock の巻き戻り対策として「直前 timestamp + 1ms ≤ 新 timestamp」へ monotonic クランプ。これにより rebuild 中も旧インデックスで検索を継続でき、ゼロダウンタイム再構築が可能になる | TASK-017,TASK-020,TASK-021,TASK-071,TASK-072 |

## タスク詳細

### TASK-001

- 補足: 要件書 5 章のデータモデルに準拠。Document/Chunk は要件 2.1.4 のチャンク化前提
- 注意: 後続の Java API シグネチャに影響するため、変更は早期に確定する

### TASK-005

- 経緯: 当初は core 内の `DirectoryProvider` 抽象として設計、その後プラグイン SPI(`IndexStorageBackend`)として実装したが、最終的に **不採用**(SPI は本プロジェクトではデータソース層(`DataSourcePlugin`)の拡張ポイントとして使う方針で、Lucene インデックスの I/O 層には適用しない)
- 判断理由: 提供する Directory バックエンドは FS とメモリの 2 種類のみで、Lucene 既存の `org.apache.lucene.store.Directory` がすでに抽象として機能しているため、追加の SPI 層を載せる実利が薄い
- 代替方針: `LuceneIndexProvider` 内で設定値(例 `searchable.storage.backend=filesystem|memory`)に応じて `MMapDirectory` / `ByteBuffersDirectory` を直接切替

### TASK-007

- 補足: `LuceneIndexProvider` の Directory 生成箇所(現在 `new MMapDirectory(path)` でハードコード)に、設定で `memory` を選択した場合に `new ByteBuffersDirectory()` を返す分岐を追加する
- 想定設定: `searchable.storage.backend=memory`(既定値は `filesystem`)
- 用途: 単体テスト / 統合テストでの使い捨てインデックス、開発時のクイック確認
- 注意: メモリ Directory はプロセス終了で消失するため、本番用途では使用しない

### TASK-008

- 経緯: S3 互換 Directory バックエンドは **実装しない** と確定。性能要件(500ms p95)と整合せず、永続性・DR の用途は TASK-071 / TASK-072 の既存バックアップ機能で代替可能と判断
- 関連: ドキュメント取込元としての S3(TASK-156)は別系統で引き続き有効

### TASK-156

- 配置: `examples/plugin-datasource-s3/`(新規)。`examples/` 配下の他サンプルと同様、本体マルチモジュールビルドには **含めない独立 Maven プロジェクト**
- 目的: `DataSourcePlugin` SPI 実装のリファレンスとして、プラグイン作者向けの雛形・参考実装を提供する。本番利用はそのまま想定せず、フォーク/コピーや独自実装の起点として使う
- 補足: 既存の `examples/` はアプリケーションのリファレンスのみだったが、ここに **プラグインのリファレンス** という新カテゴリを追加する
- 補足: 依存は example 側 pom に閉じる(`searchable-plugins`, `aws-java-sdk-s3`, テスト用 LocalStack)。`searchable-core` / `searchable-plugins` 本体には AWS SDK の依存を持ち込まない
- 補足: 設定（PluginContext.config 経由）の想定キー
  - `bucket`, `region`, `prefix`
  - 認証情報は AWS SDK 標準の credential provider chain に委譲
- 使い方: アプリ(`examples/webapp` 等)が S3 取込を有効化したい場合、本 example の JAR を依存追加し、`searchable.namespaces.<id>.plugin` 設定で参照する
- 経緯: 当初は core 直実装 → sibling モジュール案 → 別リポジトリ案 を経て、最終的に「本リポジトリの `examples/` にリファレンス実装を置く」方針に確定
- 関連: Lucene インデックス側の S3 連携(TASK-008)とは別系統(取込元と保存先の違い)

### TASK-011

- 補足: H2 サーバーモード(TCP)/PostgreSQL を想定。コネクションプール設定を含む
- 注意: Testcontainers を使った統合テストは TASK-013 で扱う

### TASK-023

- 補足: 設定 `searchable.analyzer=kuromoji|sudachi` で切替可能とする
- 注意: Sudachi 辞書ファイルの同梱・配置方法を仕様化する

### TASK-029

- 補足: weight 値 w に対し効果は f(w) を二次関数で定義(要件: w=2.0 で約4倍)
- 注意: 関数式と境界値はテストで固定化する

### TASK-035

- 補足: 既存インデックスへのリビルドはバックグラウンドで進行可能にする
- 注意: 辞書変更で既存インデックスを自動破棄しない

### TASK-074

- 補足: Lucene IndexWriter の write.lock 取得失敗時に明示的な例外をスローし、ロックホルダー情報をログ出力する
- 注意: ロック競合のテストは別 JVM プロセスを起動して検証する

### TASK-076

- 補足: SearchableLibrary.builder().readOnly(true) で起動した場合、IndexWriter を生成しない/起動できない仕様
- 注意: 起動後の書込呼び出しは IllegalStateException で拒否する

### TASK-090

- 補足: API Key・モデル名・タイムアウト・最大トークン等を設定可能にする
- 注意: シークレットを application.properties に直書きしない運用ガイドを別途用意する

### TASK-112

- 補足: ユーザー/ロール管理を見据えた論理設計のみ。実装は将来
- 注意: 実装上の権限チェックは MVP では行わない(要件 2.2.3)

### TASK-126

- 補足: 環境変数 `SEARCHABLE_API_KEY` または設定 `searchable.api.key` で有効化、ヘッダ `X-API-Key` 検証
- 注意: 未設定時は認証なしで通過させる(開発・組込み利用向け)

### TASK-147

- 補足: 100k 件投入後の単一 Namespace 検索 p95 を計測。JMH またはカスタムベンチで自動化
- 注意: ベクトル検索・ハイブリッド検索もそれぞれ別ケースで計測する

### TASK-157

- 補足: `LuceneFullTextSearcher` が既に `parentMetadata.get("url")` を `SubResult.anchorUrl` の base として読んでいる(TASK-051 連携)。これまで予約キーが未文書化だったため取り込み側 4 経路(cli / webapp / api / s3 プラグイン)で誰も埋めておらず、`SubResult.anchorUrl` も検索結果の origin 参照も実質機能していなかった。
- 注意: 値は RFC 3986 形式の URI でスキーム必須(`file:///`, `http(s)://`, `ftp://`, `s3://`)。生パスは禁止。`Document.metadata` の他の予約キー(`category` / `lang` / `tags`)も同じ節で整理する。

### TASK-158

- 補足: `Path.toUri().toString()` が `file:///...` 形式の URI を生成(空白も自動でパーセントエンコード)。
- 注意: 既存の `metadata.path` キーは後方互換として残し、`metadata.url` を新規追加。

### TASK-159

- 補足: 起動時バッチ ingest で `metadata.url` を埋め、`SearchController#detail` の Thymeleaf テンプレートに「元ファイルを開く」リンク(`hit.metadata.url`)を追加。
- 注意: webapp 内部の詳細ページパス(`/documents/{ns}/{id}`)とは別の、原ファイル直リンク。

### TASK-160

- 補足: OpenAPI の `SearchHit.metadata` / `DocumentInput.metadata` の description に reserved keys 一覧と URI 制約を追記。
- 注意: REST 経由でクライアントが生パスを送ってきた場合の挙動(現状は素通し)を明示し、推奨は URI 化と書く。

### TASK-161

- 補足: `S3DataSourcePlugin` の取込結果に `metadata.url = "s3://" + bucket + "/" + key` を設定。`endpointOverride` が設定されている場合の表記方針も統一する。
- 注意: pre-signed URL は短命なので `metadata.url` には bare な `s3://` URI を入れる。

### TASK-163

- 補足: `ResultMerger.withScore` (L84-94) および `intersect` (L63-82) が `SearchHit` の 7 引数コンストラクタを使い `subResults = List.of()` で再構築するため、ハイブリッド経由でセクション結果が常に消えていた。
- 注意: 8 引数版を使い `subResults` を保持。`ResultMergerTest` で RRF / intersect いずれも subResults 保持を検証するケースを追加。

### TASK-164

- 補足: `LuceneVectorSearcher.collectHits` が `subResults = List.of()` を返している。ベクトル空間ではセクション境界の意味付けが薄い場合があるため、実装するか、明示的に「セクション anchor は full-text のみ」と明文化するかを判断する。
- 注意: 明文化のみで済ますなら `docs/usage.ja.md` / `docs/architecture.md` のサブ結果節に注記、`docs/vector-search-guide.md` にも一行入れる。

### TASK-165

- 補足: 親 POM(`spring-boot-starter-parent`)が `id=repackage` の execution を継承させているため、子側で `<id>` 省略の execution を追加すると 2 重 repackage が走る。1 度目の出力(`Main-Class: JarLauncher / Start-Class: SearchableApp`)を 2 度目が再 repackage して `Start-Class: JarLauncher` の `-boot.jar` を作っていた → `JarLauncher.main` が自分自身を起動し続けて StackOverflowError。
- 注意: 修正後の成果物は `*-1.0.0-SNAPSHOT.jar` のみ(`-boot` 接尾辞なし)。README 系も追従済み。

### TASK-166

- 補足: 各サンプル README に Run + Quick start(index → search) 一連の手順を追加。mcp は書込 IF が無いため事前 ingest 必須を冒頭で明示。search-ui は API 前提クライアントである旨を明示。
- 注意: `examples/README.md` には共通プレリク(searchable-core install /
  searchable-cli ビルド)と「searchable-cli の searchable.yaml は index の
  場所であり、ドキュメントソースとは別」の概念整理だけ置き、詳細は子
  README に委譲する構成。

### TASK-172

- 補足: TASK-167 で「責務分離」として `DOCUMENT_SOURCE` と
  `DOCUMENT_METADATA` を分けたが、PK が完全一致(`(namespace_id,
  document_id)`)・ライフサイクル同期・`INDEXED_AT` 重複・常時 1:1 で
  あり、分割の実利が無いと判断。`DOCUMENT_METADATA` に source 系列を
  寄せ、テーブル名・FK 名・listing インデックスは流用する。
- 注意: `IndexService.delete()` と `rebuild()` が `DOCUMENT_SOURCE`
  行を片付けておらず、再 ingest 時に `indexIfChanged()` が古い hash と
  比較して全件「変更なし」判定でスキップされる潜在バグがある。統合に
  よりライフサイクルが `DOCUMENT_METADATA` の削除パスに一本化される
  ため自然消滅する。
- 影響範囲: スキーマ(`schema.sql`)、`DocumentMetadataRecord` /
  `DocumentMetadataRepository` 拡張、`DocumentSourceRepository` 廃止、
  `IndexService` / `JdbcDocumentMetadataRepository` / マイグレーション
  ノート(`docs/setup-guide.md`)。TASK-171 と協調して既存 index との
  互換性方針を確定する必要がある。

### TASK-173

- 背景: 現状の `rebuild()` は同一ディレクトリを wipe してから再投入
  する in-place 破壊型で、再構築中は検索結果が欠落する。物理パスは
  `IndexLayout.directoryFor(namespaceId)` が `<root>/<namespaceId>/`
  へ単純 resolve するだけで、バージョンの概念を持たない。
- 方針: DB にバージョン列を増やすのではなく、**ファイルシステムの
  命名規約のみで完成版を表現する**。書き手は `<timestamp>.tmp/` に
  構築し、最終 commit 後 `<timestamp>/` へ atomic rename。読み手は
  `Files.list()` で `.tmp` を除外したディレクトリ集合の最大 timestamp
  を選ぶ。クラッシュした半端な `.tmp` dir は次回起動時に列挙対象外
  になるため自然に GC 候補に落ちる。
- 決め事 3 点:
  - 完成判定 = `.tmp` サフィックス無しのディレクトリ存在
    (Lucene `segments_N` だけだと未 commit 半端 segments も書かれて
    いて代替不可)
  - GC = SearcherManager の ref-count + grace period(例: 最新 N 世代
    かつ X 秒以上 reader 参照されていないもの)で旧版を遅延削除
  - timestamp = wall clock の NTP 巻き戻りに備え、書き出し前に
    `max(直前 timestamp + 1ms, now)` で monotonic クランプ。または
    ULID を採用
- 影響範囲: `IndexLayout` を読み手 / 書き手の 2 系統 API に分割、
  `LuceneIndexProvider#contexts` のキーを `String` から
  `(namespaceId, version)` 相当に拡張(rebuild 中は旧版 reader と新版
  writer を同時に保持)、`IndexService.rebuild()` を「新 version を
  ビルド → atomic rename → SearcherManager 切替 → 旧版を遅延削除」
  に変更、`BackupService` / `RestoreService` も `IndexLayout` 直叩き
  なので追従。
- 想定外: 共有ディスク or ノード × インデックス分離なら命名規約方式で
  そのまま動作する。本格的な分散運用(複数ノードが同一物理インデックス
  を共有書込)はスコープ外。

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| BACKLOG-001 | ⏳ | Office 系(Word/Excel/PowerPoint)パーサ対応 | - |
| BACKLOG-002 | ⏳ | Google Docs / Apple Pages 連携(PDF 変換経由) | - |
| BACKLOG-003 | ⏳ | ユーザー/ロール管理(認可)実装 | - |
| BACKLOG-004 | ⏳ | インデックスデータの暗号化保存 | - |
| BACKLOG-005 | ⏳ | カスタム検索フィルタプラグイン SPI | - |
| BACKLOG-006 | ⏳ | 文書パーサープラグイン SPI | - |
| BACKLOG-007 | ⏳ | カスタムスコアリングプラグイン SPI | - |
| BACKLOG-008 | ⏳ | MCP 経由のインデックス更新(要件 2.4.3 将来拡張) | - |
| BACKLOG-009 | ⏳ | REST 一括取込エンドポイント実装 | - |
| BACKLOG-010 | ⏳ | 取込ジョブの非同期実行とステータス取得 API | - |
| BACKLOG-011 | ⏳ | 取込ジョブのスケジューラ(cron)機能 | - |
| BACKLOG-012 | ⏳ | 取込チェックポイント永続化による中断再開機能 | - |
| BACKLOG-013 | ⏳ | 並列ワーカープールによる並列取込 | - |

## バックログ詳細

### BACKLOG-003

- 補足: 要件 2.2.3 「権限管理(設計のみ、実装は将来)」の実装相当
- 注意: API Key 認証(TASK-126/TASK-143)とは別レイヤーの認可

### BACKLOG-005

- 補足: 要件 2.7.2 「将来拡張」に該当
- 注意: DataSourcePlugin と同じ SPI 基盤を流用する想定

### BACKLOG-008

- 補足: 要件 2.4.3「将来拡張: インデックス更新」に該当
- 注意: 書込権限の取扱いは API Key 認証では不十分。認可機構と同時設計
