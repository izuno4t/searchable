# Test Infrastructure Plan (Phase 4 / TASK-301)

Phase 4 でテスト基盤を整備するに先立ち、既存テスト資産の棚卸と
共通化方針を整理する。本ドキュメントは設計レポートであり、実装は
TASK-302 以降で行う。

## 既存テスト資産

| モジュール | テストクラス数 | テスト種別 |
| --- | --- | --- |
| searchable-core | 27 | ユニット + 軽い統合 |
| searchable-api | 4 | Spring Boot 統合 |
| searchable-ui | 6 | MockMvc 統合 |
| searchable-mcp | 2 | 軽い統合 |
| searchable-plugins | 1 | ユニット |
| examples/filesystem-plugin | 1 | ユニット |

主要な統合テスト:

- `SearchableApiIntegrationTest` / `HybridSearchIntegrationTest` /
  `SearchPerformanceIntegrationTest` / `DictionaryControllerIntegrationTest`
  （Spring Boot + H2 + Lucene 一式）
- `IndexViewControllerTest` / `NamespaceViewControllerTest` ほか
  （`@SpringBootTest` + `@AutoConfigureMockMvc`、H2 + Lucene 一式）
- `SearchDocumentsToolIntegrationTest`（MCP プロトコル）

## 共通化候補

### 1. H2 インメモリ DB 初期化

`@TestPropertySource` で `searchable.persistence.url=jdbc:h2:mem:...`
を 8 か所以上で重複指定している。テスト終了時のクリーンアップも
手動。

→ `H2DatabaseFixture` / `H2DatabaseExtension` を testkit で提供。

### 2. Lucene 一時インデックス初期化

`searchable.index.directory=./build/...` を統合テストで個別指定し、
クリーンアップは `@DirtiesContext` + `mvn clean` 任せ。

→ `LuceneIndexFixture`（`@TempDir` 連携）を testkit で提供。

### 3. テストデータビルダー

`Namespace`, `Document`, `SearchRequest`, `UserDictionaryEntry`
の生成は各テストでアドホックに `new`。typical name/id 衝突あり。

→ `NamespaceBuilder` / `DocumentBuilder` / `SearchRequestBuilder` を
testkit で提供。デフォルト値 + with-er パターン。

### 4. ONNX 埋め込みスタブ

実 ONNX モデルロードは Phase 1 以降テスト不要にするため
`HashEmbeddingProvider`（固定ベクトル）を本番コードに実装済み。
ただし「これがテスト用フェイク」という意図表明が不明確。

→ testkit に `FakeEmbeddingModel` を再エクスポートし、用途を明示。

### 5. Spring Boot Test 共通設定

`@SpringBootTest` 系で重複する `@TestPropertySource` プロパティ群
（`searchable.data-directory`, `searchable.index.directory`,
`searchable.persistence.url`, `searchable.dictionary.storage`）を
共通化したい。

→ `@SearchableSpringBootTest` メタアノテーション + テスト用
`application-test.yml` を testkit で提供。

### 6. MCP クライアントヘルパー

`SearchDocumentsToolIntegrationTest` で stdin/stdout JSON-RPC を
手動で組み立てている。

→ `McpTestClient` を testkit で提供。

## DB 戦略の方針

H2 と PostgreSQL の両方を本番DBとしてサポートする方針
（プロジェクトメモリ参照）。これに応じて:

- **ユニットテスト**: H2 インメモリのみ（高速性最優先）
- **リポジトリ層テスト**: H2 + PostgreSQL Testcontainer 両方で
  パラメータ化実行（SQL 方言差を検知）
- **統合テスト**: デフォルト H2、CI/明示時に PostgreSQL も走らせる
- testkit に `H2DatabaseFixture` と `PostgresContainerFixture` を
  並列に用意し、`@ParameterizedTest` + `@MethodSource` で両 DB
  に対する同一テスト実行を容易化

## 新規導入要素

| 要素 | 用途 | 配置 |
| --- | --- | --- |
| `searchable-testkit` モジュール | 共通テスト基盤 | ルート pom 配下 |
| Testcontainers (PostgreSQL) | PostgreSQL 互換確認 | testkit |
| JaCoCo | カバレッジ集計 | parent pom |
| Checkstyle | コードスタイル | parent pom |
| SpotBugs | バグパターン検出 | parent pom |
| OWASP Dependency-Check | 依存脆弱性スキャン | parent pom |
| markdownlint-cli2 | ドキュメント Lint | リポジトリルート |
| Spectral | OpenAPI Lint | リポジトリルート |
| GitHub Actions | CI ワークフロー | `.github/workflows/` |
| Dev Container | 開発環境統一 | `.devcontainer/` |

## モジュール構成

```text
searchable-parent (pom)
├── searchable-plugins
├── searchable-core
├── searchable-api
├── searchable-mcp
├── searchable-ui
└── searchable-testkit   ← 新規。scope=test 提供
```

`searchable-testkit` は本番モジュールから依存させない（test scope のみ）。
testkit 自身は core/api への compile 依存を持ち、ドメイン型に対する
ビルダーを提供する。

## 品質ゲート目標値

| 指標 | 目標 | 失敗閾値 |
| --- | --- | --- |
| 行カバレッジ | 70% 以上 | 60% 未満で失敗 |
| 分岐カバレッジ | 60% 以上 | 50% 未満で失敗 |
| Checkstyle | error 0 | error 1 以上で失敗 |
| SpotBugs | High 0 | High 1 以上で失敗 |
| OWASP DC | CVSS 7.0 以上 0 | CVSS 7.0 以上 1 で失敗 |

初期導入時は閾値を緩めに置き、CI 安定後に段階的に厳格化。

## CI ジョブ構成

```text
ci.yml
├── build-and-test              (ユニット + ビルド)
├── integration-test            (H2 + PostgreSQL Testcontainer)
├── static-analysis             (Checkstyle + SpotBugs)
├── dependency-check            (OWASP DC)
├── docs-lint                   (markdownlint + Spectral)
└── coverage-report             (JaCoCo aggregate)
```

Maven ローカルリポジトリは `actions/cache` でジョブ間共有。

## ドキュメント方針

- `docs/testing.md`: testkit 利用方法、テスト実行手順、CI ジョブ説明
- `docs/branch-protection.md`: PR 必須チェック設定手順
- `.devcontainer/README.md`: 開発環境セットアップ（任意）

## スコープ外（Phase 4 では実装しない）

- PIT ミューテーションテスト → BACKLOG-003
- Playwright E2E → BACKLOG-005
- SBOM(CycloneDX) 生成 → BACKLOG-006
- リリースタグ駆動ワークフロー → BACKLOG-001
