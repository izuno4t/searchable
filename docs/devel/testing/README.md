# Testing Guide (TASK-326)

Searchable のテスト基盤と CI ジョブの利用方法をまとめる。

## テスト階層

Searchable のテストは「**何を保証するか / どこに走らせるか**」で 3 層に分ける。
各層は別の故障モードを捕まえる役割があり、上位層が下位層を完全に代替することはない。

| レベル | ツール | プロセス境界 | 主な対象 | 動くタイミング |
| --- | --- | --- | --- | --- |
| **単体** | `maven-surefire-plugin` (`*Test.java`) | テスト JVM 同一プロセス | 関数・クラス単位の挙動、分岐網羅 | PR / main push (CI) |
| **結合** | `maven-failsafe-plugin` (`*IT.java`) + Testcontainers | テスト JVM + DB / 外部サービス用コンテナ | DB / Lucene / SPI / 多モジュール連携 | PR / main push (CI) |
| **e2e** | `spring-boot-maven-plugin:start/stop` + Failsafe + 外部 HTTP クライアント | **packaged JAR を別プロセスで起動**、テスト JVM から HTTP 越しに叩く | パッケージング、起動シーケンス、OS 境界、`Main-Class` 等の Manifest、実 ランタイム挙動 | **release タグ push 時** (release pipeline のゲート) |

### 各層が捕まえる故障モード

- **単体で漏れて結合で出る**: クラス間の契約違反、トランザクション境界、SQL の dialect 差。
- **結合で漏れて e2e で出る**: fat JAR の shading 失敗、`spring.factories` の欠落、
  JDK バージョン差での起動失敗、配布形態固有の classpath 問題。
- **e2e で漏れる**: 性能・スケール・実ユーザー操作シナリオ（手動 QA 領域）。

### e2e と「Spring Boot Test」の違い

`@SpringBootTest(webEnvironment = RANDOM_PORT)` はテスト JVM 内で Spring コンテキストを
起動するため、**結合レベル**であって e2e ではない。e2e で検証すべき以下の項目は
すべて取りこぼす:

- packaged JAR の `Main-Class` / shaded manifest / `spring-boot-loader` 起動
- 別 JVM プロセスでの起動失敗（環境変数欠落、port bind 競合 等）
- 実 JDK 21 ランタイム上での classpath 解決
- ホスト OS のシグナル / シャットダウン

e2e の実装はかならず **packaged JAR を別プロセスで起動して外部から叩く** こと。
具体的なステップは [verify.ja.md](verify.ja.md) を参照。

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

- [docs/test-infrastructure-plan.md](test-infrastructure-plan.md) — 設計レポート
- [docs/devel/standards/branch-protection.md](branch-protection.md) — ブランチ保護設定手順
- [docs/devel/design/architecture/overview.md](architecture.md) — モジュール構成
