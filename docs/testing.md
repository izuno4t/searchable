# Testing Guide (TASK-326)

Searchable のテスト基盤と CI ジョブの利用方法をまとめる。

## ローカルでのテスト実行

```bash
# 全モジュールのユニット + 統合テスト
mvn -B verify

# ユニットテストのみ（統合テストは Failsafe にバインドされるためスキップ）
mvn -B -DskipITs verify

# 特定モジュール + 依存を解決して実行
mvn -B -pl searchable-ui -am test

# 1 メソッドだけ実行
mvn -B test -Dtest=DictionaryViewControllerTest#listShowsGlobalRow
```

JaCoCo カバレッジレポートは `*/target/site/jacoco/index.html` に
自動生成される。集計レポートは:

```bash
mvn -B -Pcoverage-aggregate verify
# 出力: target/site/jacoco-aggregate/index.html
```

## 品質ゲート

```bash
# Checkstyle + SpotBugs
mvn -B -Pquality -DskipTests verify

# OWASP Dependency-Check（NVD ダウンロードで初回は数分かかる）
mvn -B -Psecurity -DskipTests verify
```

## ドキュメント Lint

```bash
# Markdown
npx --yes markdownlint-cli2 "**/*.md" "#node_modules" "#**/target/**"

# Spelling
npx --yes cspell --no-progress --no-summary \
  "**/*.{md,java,xml,yml,yaml,json,properties}"

# OpenAPI
npx --yes @stoplight/spectral-cli lint \
  examples/api/openapi.yaml --ruleset .spectral.yaml
```

## searchable-testkit の利用

`searchable-testkit` はテスト用ハーネスを提供する共通モジュール。
test scope で依存する:

```xml
<dependency>
  <groupId>com.searchable</groupId>
  <artifactId>searchable-testkit</artifactId>
  <scope>test</scope>
</dependency>
```

主な提供 API:

| パッケージ | 用途 |
| --- | --- |
| `com.searchable.testkit.db` | `H2DatabaseFixture` / `PostgresDatabaseFixture` |
| `com.searchable.testkit.lucene` | `LuceneIndexFixture` |
| `com.searchable.testkit.builder` | `NamespaceFixtures` / `DocumentFixtures` / `SearchRequestFixtures` |
| `com.searchable.testkit.embedding` | `FakeEmbeddingProvider`（ONNX 不要） |
| `com.searchable.testkit.spring` | `@SearchableSpringBootTest` メタアノテーション |
| `com.searchable.testkit.mcp` | `McpTestClient` |

### H2 と PostgreSQL の両方で同じテストを走らせる

```java
@ParameterizedTest(name = "{0}")
@MethodSource("com.searchable.testkit.db.DatabaseFixtures#h2AndPostgresIfDockerAvailable")
void repositoryWorksOnBothDialects(
        final String label, final Supplier<DatabaseFixture> open) {
    try (DatabaseFixture db = open.get()) {
        final JdbcNamespaceRepository repo =
            new JdbcNamespaceRepository(db.dataSource());
        // ... assertions
    }
}
```

Docker が無いホストでは PostgreSQL 行が自動的にスキップされる。

> NOTE: `searchable-core` 自身は testkit に依存できない（reactor の
> 循環依存になるため）。core 内部用には
> `com.searchable.core.testing.H2TestDatabase` をローカル定義している。

## CI ジョブ一覧

`.github/workflows/ci.yml` で定義。

| ジョブ | 内容 | 失敗時の対応 |
| --- | --- | --- |
| `Build + Unit Tests` | ビルド + ユニット | Surefire レポートを確認 |
| `Integration Tests` | 統合テスト + Testcontainers | Surefire レポート + Docker ログ |
| `Static Analysis` | Checkstyle + SpotBugs | レポートを確認、警告レベルに応じて修正/抑制 |
| `Dependency Vulnerability Scan` | OWASP DC | suppressions に追記（必ず理由記載） |
| `Docs Lint` | markdownlint / cspell / Spectral | 該当ファイルを修正、用語は `.cspell/searchable-terms.txt` に追加 |
| `Aggregate Coverage Report` | JaCoCo 集計 | 目標値（行 70%）を下回ったら対象モジュールにテスト追加 |

## 開発環境（Dev Container）

VS Code で `.devcontainer/devcontainer.json` を使うと、Java 21 + Maven +
Node 20 + Docker-in-Docker が揃った環境を即座に立ち上げられる。
Testcontainers もそのまま動作する。

```bash
# VS Code から: コマンドパレット → "Dev Containers: Reopen in Container"
```

## トラブルシューティング

| 症状 | 原因と対処 |
| --- | --- |
| `SpotBugs: Unsupported class file major version` | JDK が新しすぎる。`pom.xml` で asm を `9.8` 以降に固定（済） |
| `Testcontainers can't reach Docker` | Docker Desktop 起動 or Dev Container 使用 |
| 統合テスト同士が干渉 | `searchable.persistence.url` と `searchable.index.directory` をユニークに |
| `cspell unknown word` | プロジェクト用語なら `.cspell/searchable-terms.txt` に追加 |

## 関連ドキュメント

+ [docs/test-infrastructure-plan.md](test-infrastructure-plan.md) — 設計レポート
+ [docs/branch-protection.md](branch-protection.md) — ブランチ保護設定手順
+ [docs/architecture.md](architecture.md) — モジュール構成
