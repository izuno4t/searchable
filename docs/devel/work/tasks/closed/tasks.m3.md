# TASKS - M3 (AI 統合)

Milestone: M3
Goal: AI 統合機能（LLM プロバイダー連携・検索結果要約・管理画面）を実装し、Searchable から AI 経由の利用シナリオを成立させる
Status: 完了（2026-06-10）
Note: 後続マイルストーン（M4）は `docs/devel/work/tasks/task.md` に集約。
  M3 時点で残置していたバックログは `docs/devel/work/backlog/` 配下の
  個別ファイル（`backlog-NNN-<slug>.md`）へ移送した

## 前提

- M1（M-v3.3）完了済（2026-06-01 チェックポイント）。
  基盤（`SearchableLibrary` / `AiProvider` SPI / `searchable-admin` / CLI / examples 一式）は M1 で確立。
  詳細は `docs/devel/work/tasks/closed/tasks.m1.md` を参照
- M3 着手時点で `AiProvider` SPI（M1 TASK-084）は定義済み。M3 ではプロバイダー実装・要約サービス・設定・UI・テストを追加する

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
| TASK-001 | ✅ | OpenAI プロバイダー実装（`AiProvider` SPI 実装、Chat Completions API 連携） | - |
| TASK-002 | ✅ | Anthropic プロバイダー実装（`AiProvider` SPI 実装、Messages API 連携） | - |
| TASK-003 | ✅ | Ollama プロバイダー実装（`AiProvider` SPI 実装、ローカル LLM 連携） | - |
| TASK-004 | ✅ | 検索結果要約・統合サービス実装（検索ヒットを LLM に渡して要約/回答生成） | TASK-001,TASK-002,TASK-003 |
| TASK-005 | ✅ | AI 統合タイムアウト・フォールバック制御実装（LLM 障害時の degrade 戦略） | TASK-004 |
| TASK-006 | ✅ | AI 統合設定モデルと application.properties 取込（プロバイダー選択・API キー・モデル・タイムアウト） | TASK-004 |
| TASK-007 | ✅ | AI 統合ユニットテスト（スタブプロバイダーによる API 非依存テスト） | TASK-004,TASK-005 |
| TASK-008 | ✅ | searchable-admin に AI 統合設定画面追加（プロバイダー・API キー・モデル選択 UI） | TASK-006 |
| TASK-009 | ✅ | 設定パスの解決基準を `data-directory` ベースに改修（現状は JVM CWD 基準で CLI/webapp の起動ディレクトリ差で別 index を見にいく footgun）。ADR-0002 を併設（M1 残務） | - |
| TASK-010 | ✅ | `searchable ingest` で未対応拡張子（`.DS_Store` 等）に当たると `IllegalArgumentException` で全体中断する問題を WARN ログ + skip カウンタで継続するように修正（M1 残務） | - |
| TASK-011 | ✅ | `searchable ingest --namespace X` で `X` 未登録時に `NoSuchElementException` でスタックトレース終了する UX を改善。TTY なら対話プロンプト、`--create-namespace` フラグで非対話自動作成、非 TTY + フラグ無しなら案内付きエラーで `exit 1`（M1 残務） | - |
| TASK-012 | ✅ | `examples/ai-ollama/` サンプル設定一式追加（専用 `OllamaProvider` 利用ガイド、OpenAI 互換ルート併記） | TASK-003,TASK-006,TASK-008 |

## タスク詳細

### TASK-001 / TASK-002 / TASK-003

- 補足: いずれも M1 で定義した `AiProvider` SPI（旧 TASK-084）の実装。プロバイダー間で API 差異（認証ヘッダー・リクエスト/レスポンス形式・ストリーミング有無）を吸収するアダプターとして実装する
- 注意: HTTP クライアントは Java 標準 `HttpClient` を基本とし、外部依存は最小化。SDK 依存は避ける（将来のバージョン差異・脆弱性対応コスト削減のため）
- 結果（2026-06-07）:
  - **TASK-001 OpenAI**: `io.searchable.ai.openai.OpenAiProvider` を実装。
    Chat Completions API（`POST /v1/chat/completions`）、`Authorization: Bearer` ヘッダー。
    JSON で `model` / `messages[]` / `max_tokens` / `temperature` 送信。
    既定モデル `gpt-4o-mini`。API キーは `OPENAI_API_KEY` / `searchable.ai.openai.api-key` から解決。
  - **TASK-002 Anthropic**: `io.searchable.ai.anthropic.AnthropicProvider` を実装。
    Messages API（`POST /v1/messages`）、`x-api-key` + `anthropic-version` ヘッダー。
    `system` プロンプトはトップレベル、複数の `content` ブロックを連結。
    既定モデル `claude-sonnet-4-6`、既定 API バージョン `2023-06-01`。
  - **TASK-003 Ollama**: `io.searchable.ai.ollama.OllamaProvider` を実装。
    Generate API（`POST /api/generate`）、認証なし、`stream=false`、
    `options.num_predict` / `options.temperature`。
    既定モデル `llama3.2`、既定ベース URL `http://localhost:11434`。
  - **共通基盤**: `io.searchable.ai.internal.HttpProviderSupport` を新設。
    HTTP ステータス → `AiException.Kind` の対応は
    401/403→AUTH、400/404/422/429→REQUEST、5xx→UPSTREAM、HttpTimeoutException→TIMEOUT で共通化。
    `META-INF/services/io.searchable.ai.AiProvider` に 3 プロバイダーを登録。
  - **テスト**: `FakeHttpServer`（com.sun.net.httpserver.HttpServer 利用）を test fixture として導入。
    各プロバイダーの正常系・認証エラー・5xx 障害・タイムアウトを検証（TASK-007 で詳述）。

### TASK-004

- 補足: 検索結果（`SearchHit` のコンテンツ・メタデータ）を context として LLM に渡し、ユーザーの自然言語問い合わせに対する要約・回答を生成する。RAG パターンの単純実装
- 注意: context サイズ管理（トークン上限）とプロバイダー間でのトークン換算差異を吸収する仕組みが必要
- 結果（2026-06-07）:
  - `AiProviderRegistry` を新設。
    ServiceLoader 経由で provider をロード、name 重複は最初のものを採用、
    `AutoCloseable` で provider の `close()` を伝播。
  - `SummaryConfig` レコードと `SummaryService` を新設。フィールドは
    provider/model/timeout/maxTokens/temperature/maxContextItems/maxContextChars/fallbackOnError。
  - `SearchResult` → `List<AiContextItem>` への変換は `SummaryService.toContextItems()` で実施。
    namespace と score をメタデータに含める。
  - コンテキスト制限は「件数上限優先 → 残りで文字数」の単純な打ち切りで実装。トークン換算の精密化は将来課題。
  - `SummaryService` は 2 エントリポイントを提供:
    `summarize(String, SearchResult)` / `summarize(String, List<AiContextItem>)`。

### TASK-005

- 補足: LLM 呼出のタイムアウト・リトライ・サーキットブレーカー。失敗時は AI 抜きの検索結果のみ返すフォールバック動作
- 注意: タイムアウト値は設定可能とし、デフォルトは応答性重視（例: 10s）
- 結果（2026-06-07）:
  - タイムアウトは `AiRequest.timeout()` を経由して各 provider の `HttpRequest.Builder.timeout()` に渡る。既定 15 秒。
  - `SummaryConfig.fallbackOnError` が true（既定）の場合、
    `AiException.Kind` が `TIMEOUT` / `UPSTREAM` / `UNKNOWN` の例外は
    `SummaryService.fallbackResponse()` に変換。
    変換後は空 text、`model="ai-fallback"`、`usage` に `error.kind` と `error.message` を格納。
  - `AUTH` / `REQUEST` は常に再 throw して設定ミスを silent fail させない。
  - リトライ・サーキットブレーカー本体は導入せず、上位レイヤー（admin UI / アプリ側）の責務とする（KISS）。

### TASK-006

- 補足: API Key・モデル名・タイムアウト・最大トークン等を設定可能にする
- 注意: シークレットを application.properties に直書きしない運用ガイドを別途用意する（環境変数 / 外部シークレットストア参照を推奨）
- 結果（2026-06-07）:
  - `SearchableProperties.Ai` 内部クラスを新設し、`searchable.ai.*` プレフィックスで Spring Boot バインド。
    フィールド: enabled / provider / model / timeout / maxTokens / temperature /
    maxContextItems / maxContextChars / fallbackOnError。
  - `SearchableConfiguration` に `aiProviderRegistry` / `summaryConfig` /
    `summaryConfigProvider` / `summaryService` の 4 ビーンを追加。
    `destroyMethod="close"` で provider の HTTP クライアントを shutdown 時に解放。
  - **API キーは Properties にバインドしない**。各 provider が
    `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` /
    `searchable.ai.<provider>.api-key` 等を独自に解決する設計に統一。
    admin DB や HTTP traffic に平文キーが流れない構造を担保。
  - `searchable-admin/application.properties` に既定値（enabled=false、`provider=`、timeout=15s 等）を追記。
  - `searchable-admin` の `pom.xml` に `searchable-ai` 依存を追加。

### TASK-007

- 補足: 実 API を呼ばないスタブプロバイダー（`AiProvider` 実装の test fixture）で要約パイプラインのテストを成立させる。各プロバイダーの実 API 接続テストはオプトイン（環境変数で有効化）
- 注意: ユニットテストは CI で常時実行可能とし、API キーを要求しない
- 結果（2026-06-07）:
  - `searchable-ai/src/test/java/io/searchable/ai/testfixture/FakeHttpServer.java` を新設。
    JDK 標準 `com.sun.net.httpserver.HttpServer` で各 provider の upstream をシミュレート。
    ヘッダー正規化（lowercase）と本文記録に対応。
  - `OpenAiProviderTest` / `AnthropicProviderTest` / `OllamaProviderTest` で正常系・認証ヘッダー・ステータスコードマッピング・タイムアウトを検証。
  - `SummaryServiceTest` で disabled / unknown provider / TIMEOUT fallback /
    UPSTREAM rethrow / AUTH 必ず rethrow / SearchResult マッピング / コンテキスト制限を網羅。
  - `AiProviderRegistryTest` で ServiceLoader 経由の検出と name 重複処理を検証。
  - `searchable-admin/AiSettingsControllerTest` で UI ページの描画（providers が `<select>` に出る）。
    form binding（POST → `SummaryConfigProvider` 更新）を検証。
  - **結果**: searchable-ai 51 件、searchable-admin 97 件（+3 件 AI UI）いずれもパス。全リアクター BUILD SUCCESS。

### TASK-008

- 補足: M1 で実装済の `searchable-admin`（TASK-103）に AI 統合設定画面を追加。プロバイダー選択 / API キー入力 / モデル選択 / タイムアウト設定の UI
- 注意: API キーは画面表示時にマスクし、保存時のみ平文受領。永続化形式は TASK-006 と整合
- 結果（2026-06-07）:
  - `SummaryConfigProvider`（mutable holder）を新設し、`SummaryService` を
    `SummaryConfig` 直接 / Provider 経由の 2 つのコンストラクターに拡張。
    UI 更新は holder の `update()` で次回 summarize から有効化。
  - `AiSettingsController`（`/settings/ai`）と `AiSettingsForm`（バリデーション付き）を新設。
    template は `ai-settings.html`（基本設定 / 生成パラメーター / コンテキスト制限 / 障害時挙動の 4 セクション）。
  - **API キーは UI で扱わない**: 画面は明示的に「環境変数から読み込みます」と案内し、
    `<input type=password>` を置かない。これにより admin DB / HTTP traffic に平文キーが流れない設計を貫徹。
    TASK-006 と整合。
  - `settings.html` 下部に「関連設定 → AI 要約設定」へのリンクを追加（新規 navbar 項目は増やさず既存 Settings 配下に集約）。
  - provider 候補は `AiProviderRegistry.names()` から動的に列挙。プロバイダー未検出時は警告 alert を表示。

### TASK-022 followup (M2 carry-over)

- 結果（2026-06-07）: M2 TASK-022 で導入された Jackson BOM 化の処理に取りこぼしがあり、
  M3 着手時のフルリアクタービルドで初露呈した。
  parent pom dependencyManagement 内に、BOM import の後ろに version 無し
  jackson-databind/jsr310/yaml の個別エントリが残っており、
  Maven 3.9 が `dependencies.dependency.version is missing` でビルド拒否。
  BOM が version を供給するため個別エントリは不要なので削除して修正。
  `-N validate` だけでは検出できなかったため、検証フローを `-N validate` ではなく
  `-pl searchable-ai -am install` 相当の reactor ビルドへ改訂すべきだが、
  M2 はアーカイブ済みのため本記録のみとする。

### TASK-009

- 背景: `ConfigLoader` は YAML をデシリアライズするだけで path 正規化を一切しない。
  `SearchableConfig` / `IndexConfig` / `PersistenceConfig` が保持する `Path` は relative のまま
  `Files.*` / `MMapDirectory` / H2 JDBC URL に渡るため、`user.dir`（JVM CWD）基準で解決される。
  CLI（リポジトリルートから起動）と webapp（任意ディレクトリから起動）を別 CWD で動かすと
  index/DB が分裂する
- 提案する解決順序:
  1. `data-directory` 自身: 絶対なら as-is、相対なら **config ファイルの親ディレクトリ基準** で解決。
     Spring Boot 経由の webapp で config ファイル不在なら CWD を fallback
  2. `index.directory`（未設定時のデフォルト `<data-directory>/indexes`）: 絶対なら as-is、相対なら **`data-directory` 基準**
  3. `plugins.directory`: 同じく `data-directory` 基準
  4. H2 JDBC URL: URL 内のファイルパスが相対なら `data-directory` 基準で絶対化してから H2 に渡す。
     または `persistence.directory` を新設し、デフォルトを `<data-directory>` にしてテンプレート展開する設計を検討
  5. 起動時に正規化後の絶対パスを INFO ログ出力（運用診断）
- 実装範囲:
  - `searchable-core/.../application/config/ConfigLoader.java` で post-deserialize 正規化
  - `SearchableConfig` の path フィールドは「正規化後の絶対 Path」を契約とする（`normalize(Path base)` factory を導入）
  - `examples/webapp/SearchableWebappApplication` の Spring Boot binding でも同じ resolver を通すよう変更
  - 起動ログに正規化済み絶対パスを INFO 出力
  - 起動済 path が直接 `MMapDirectory` / `Files.newOutputStream` に渡る前提を満たすよう、
    `LuceneIndexProvider` / `JdbcDocumentMetadataRepository` 側で
    `toAbsolutePath()` 呼び出しに依存していたら整理
- 後方互換:
  - 既存 config（`./data/webapp` 系）は意味が変わる（CWD 基準 → config 親ディレクトリ基準）。
    `docs/public/getting-started.ja.md` で導入した `$HOME/searchable-data` の workaround は
    本タスク完了後に「自然な相対パス」へ書き換え可能
  - 移行注記を `docs/public/setup-guide.md` に追加
- 関連 ADR: 新規 ADR-0002 を作成（`data-directory` を path 解決の anchor とする方針、H2 URL 書換、ログ出力義務などの設計判断を記録）
- 関連 docs:
  - `docs/public/getting-started.ja.md`
  - `examples/webapp/README.md`
  - `docs/public/setup-guide.md`
  - `docs/public/cli-guide.ja.md`

## バックログ

M3 時点で残置していたバックログ（BACKLOG-002 〜 BACKLOG-016）は、M3 完了に
合わせて `docs/devel/work/backlog/` 配下の個別ファイルへ移送した。最新の
ステータス・詳細・参照は移送先を正本とする。

| ID | 移送先ファイル | 概要 |
| --- | --- | --- |
| BACKLOG-002 | `docs/devel/work/backlog/backlog-002-google-docs-apple-pages.md` | Google Docs / Apple Pages 連携（PDF 変換経由） |
| BACKLOG-003 | `docs/devel/work/backlog/backlog-003-user-role-authz.md` | ユーザー / ロール管理（認可）実装 |
| BACKLOG-004 | `docs/devel/work/backlog/backlog-004-index-encryption.md` | インデックスデータの暗号化保存 |
| BACKLOG-005 | `docs/devel/work/backlog/backlog-005-search-filter-plugin-spi.md` | カスタム検索フィルタープラグイン SPI |
| BACKLOG-006 | `docs/devel/work/backlog/backlog-006-document-parser-plugin-spi.md` | 文書パーサープラグイン SPI |
| BACKLOG-007 | `docs/devel/work/backlog/backlog-007-scoring-plugin-spi.md` | カスタムスコアリングプラグイン SPI |
| BACKLOG-008 | `docs/devel/work/backlog/backlog-008-mcp-index-update.md` | MCP 経由のインデックス更新 |
| BACKLOG-009 | `docs/devel/work/backlog/backlog-009-rest-bulk-ingest.md` | REST 一括取込エンドポイント実装 |
| BACKLOG-010 | `docs/devel/work/backlog/backlog-010-async-ingest-job.md` | 取込ジョブの非同期実行とステータス取得 API |
| BACKLOG-011 | `docs/devel/work/backlog/backlog-011-ingest-scheduler-cron.md` | 取込ジョブのスケジューラー（cron）機能 |
| BACKLOG-012 | `docs/devel/work/backlog/backlog-012-ingest-checkpoint-resume.md` | 取込チェックポイント永続化による中断再開機能 |
| BACKLOG-013 | `docs/devel/work/backlog/backlog-013-parallel-ingest-workers.md` | 並列ワーカープールによる並列取込 |
| BACKLOG-014 | `docs/devel/work/backlog/backlog-014-hwpf-doc-extraction-test.md` | レガシー `.doc`（HWPF）パーサーの実抽出テスト整備 |
| BACKLOG-015 | `docs/devel/work/backlog/backlog-015-spring-boot-4-upgrade.md` | Spring Boot 3.4.1 → 4.0.x メジャーアップグレード |
| BACKLOG-016 | `docs/devel/work/backlog/backlog-016-web-crawler-datasource.md` | Web クローラー取込 DataSourcePlugin 実装 |

---

**Last Updated**: 2026-06-10
