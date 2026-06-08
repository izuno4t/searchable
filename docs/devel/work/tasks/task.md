# TASKS

Milestone: M3
Goal: AI 統合機能（LLM プロバイダー連携・検索結果要約・管理画面）を実装し、Searchable から AI 経由の利用シナリオを成立させる

## 前提

- M1（M-v3.3）完了済（2026-06-01 チェックポイント）。
  基盤（`SearchableLibrary` / `AiProvider` SPI / `searchable-admin` / CLI / examples 一式）は M1 で確立。
  詳細は `docs/devel/work/archive/m1-tasks.md` を参照
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
  `ApplicationConfig` / `IndexConfig` / `PersistenceConfig` が保持する `Path` は relative のまま
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
  - `ApplicationConfig` の path フィールドは「正規化後の絶対 Path」を契約とする（`normalize(Path base)` factory を導入）
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

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| BACKLOG-002 | ⏳ | Google Docs / Apple Pages 連携（PDF 変換経由） | - |
| BACKLOG-003 | ⏳ | ユーザー/ロール管理（認可）実装 | - |
| BACKLOG-004 | ⏳ | インデックスデータの暗号化保存 | - |
| BACKLOG-005 | ⏳ | カスタム検索フィルタープラグイン SPI | - |
| BACKLOG-006 | ⏳ | 文書パーサープラグイン SPI | - |
| BACKLOG-007 | ⏳ | カスタムスコアリングプラグイン SPI | - |
| BACKLOG-008 | ⏳ | MCP 経由のインデックス更新（要件 2.4.3 将来拡張） | - |
| BACKLOG-009 | ⏳ | REST 一括取込エンドポイント実装 | - |
| BACKLOG-010 | ⏳ | 取込ジョブの非同期実行とステータス取得 API | - |
| BACKLOG-011 | ⏳ | 取込ジョブのスケジューラー（cron）機能 | - |
| BACKLOG-012 | ⏳ | 取込チェックポイント永続化による中断再開機能 | - |
| BACKLOG-013 | ⏳ | 並列ワーカープールによる並列取込 | - |
| BACKLOG-014 | ⏳ | レガシー `.doc`（HWPF）パーサーの実抽出テスト整備 | - |
| BACKLOG-015 | ⏳ | Spring Boot 3.4.1 → 4.0.x メジャーアップグレード（独立マイルストーン候補） | - |
| BACKLOG-016 | ⏳ | Web クローラー取込 DataSourcePlugin 実装（クローラー本体は OSS ライブラリを採用） | - |

## バックログ詳細

### BACKLOG-003

- 補足: 要件 2.2.3「権限管理（設計のみ、実装は将来）」の実装相当
- 注意: API Key 認証（M1 TASK-126/TASK-143）とは別レイヤーの認可

### BACKLOG-005

- 補足: 要件 2.7.2「将来拡張」に該当
- 注意: DataSourcePlugin と同じ SPI 基盤を流用する想定

### BACKLOG-008

- 補足: 要件 2.4.3「将来拡張: インデックス更新」に該当
- 注意: 書込権限の取扱いは API Key 認証では不十分。認可機構と同時設計

### BACKLOG-014

- 背景: M1 TASK-177 で `.doc` は POI が新規書き出し未サポートのため、現状
  `OfficeDocumentParserTest` では登録・MIME・拡張子解決のみ検証し、実抽出
  は同じ `ExtractorFactory` 経路の `.xls`/`.ppt` で間接カバーに留めている。
- 目的: 実 `.doc` バイナリをフィクスチャとして HWPF 抽出経路を直接検証
  する。
- 注意: フィクスチャは **ライセンスがクリア（自作 or 再配布可能）** かつ
  **小さい（数 KB）** ものを用意する。`.docx` をリネームしただけは
  `ExtractorFactory` がファイルマジックで OOXML と判定するため不可（本物の Word 97-2003 / OLE2 バイナリが必要）。
- 備考: POI 単体では生成不可。LibreOffice headless（`soffice --convert-to
  doc`）等で生成するか、自作の最小 `.doc` を同梱する。

### BACKLOG-016

- **結論**: Web ページ取込用の `DataSourcePlugin` を新設する。
  クローラー本体（HTTP fetch / robots.txt 解釈 / URL 正規化 / frontier 管理 / リトライ等）は
  OSS の Java クローラーライブラリに委譲し、本プラグインは設定の受け渡し、
  `PluginDocument` への整形、`metadata.url` 等の予約キー付与のアダプターに徹する。
  **フル自前実装は避ける**（再発明コストが過大なため）。
- **採用ライブラリ**: 本タスクの第一ステップで選定（crawler4j /
  norconex-crawler / StormCrawler 等を、ライセンス・保守状況・依存サイズ・
  Java 21 互換性で比較。**一次情報で要確認**）。MVP（URL リスト取込）のみで
  足りる場合は `jsoup` + Java 標準 `HttpClient` の軽量構成でも可、と段階別に
  判断する。
- 背景: 現状の同梱データソースは `FilesystemDataSourcePlugin` と
  `examples/plugin-datasource-s3` のみで、Web 上のページを取込する経路がない。
  `HtmlParser` はローカル HTML のパースのみを担い、HTTP fetch は持たない。
- スコープ案（段階導入）:
  - フェーズ 1（MVP）: **URL リスト取込**。`urls: [...]` または sitemap.xml を
    config で受け、各 URL を逐次 fetch → `HtmlParser` でパース →
    `PluginDocument` 化。`metadata.url` はそのまま元 URL を採用。
  - フェーズ 2: **再帰クロール**。`seedUrls` + `maxDepth` + `sameOriginOnly`
    等の制約付きでリンク追跡。`robots.txt` 尊重、`Crawl-Delay` 解釈、同時実行
    数上限、重複 URL の正規化（クエリ並び替え・末尾スラッシュ等）を含む。
- 注意:
  - User-Agent は識別可能な文字列を既定とし、設定で上書き可能にする。
  - 動的レンダリング（JS 実行）は対象外。必要なら別プラグインで切り出す。
  - 文字コード判定は HTTP `Content-Type` ヘッダー → HTML meta → UTF-8 フォール
    バックの順（採用ライブラリの機能を優先利用）。
  - 認証付きサイトは将来課題（Basic / Bearer / Cookie はフェーズ 2 以降）。
  - 採用ライブラリが内部で独自 HTTP クライアントを持つ場合は、TASK-001〜003
    の「Java 標準 `HttpClient` 優先」方針よりライブラリ選定を優先する。
- ADR 化判断: ライブラリ選定が決まった時点で、その採用理由を ADR-0002 として
  独立記録する（本エントリは「やる/やらない」のスコープ管理にとどめる）。
- 関連: `DataSourcePlugin` SPI、`HtmlParser`、`metadata.url` 予約キー。
  詳細は `docs/devel/design/architecture/overview.md` §5.7 を参照。

### BACKLOG-015

- 想定マイルストーン: M3（独立した移行プロジェクトとして計画）
- 背景: 依存脆弱性スキャン（Red Hat Dependency Analytics）で Spring Boot
  3.4.1 の各スターターに多数の推移的脆弱性（spring-boot-starter-web で
  critical 6 / high 15 等）。最新 GA は 4.0.6（2026-05 時点、spring.io で
  確認）。リリースは年2回・マイナーは最低12か月 OSS サポートの方針から、
  3.4.x / 3.5.x は OSS サポート終了または終了間際と推定（正確な EOL 日は
  移行計画時に要確認）。
- 影響範囲: `searchable-admin` と `examples/*`（webapp / api / mcp）のみ。
  `searchable-core` は Spring 非依存のため無関係。
- 注意: 3.x → 4.0 はメジャーアップで Spring Framework 7 ベース、破壊的
  変更を伴う。Java baseline・削除/変更 API・`jakarta` 系の差分を移行前に
  一次情報で確認すること。段階移行（まず最新 3.x → 4.0）も検討。
- 進め方: 独立した移行プロジェクトとして brainstorming → plan → 実装 →
  全モジュールのテスト/起動確認のサイクルで実施する。
