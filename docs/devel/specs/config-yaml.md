# searchable.yaml 設定スキーマ仕様

Searchable ライブラリの設定ファイル `searchable.yaml` のスキーマを定義する。

各フィールドの型・必須/任意・デフォルト値・バリデーション規則を明示する。

## 1. 概要

### 1.1 スコープ

本仕様は、`searchable-core` モジュールの `ConfigLoader` が YAML を
デシリアライズして組み立てる `SearchableConfig` の構造を正本として定義する。

- 正本となるソース: `searchable-core/src/main/java/io/searchable/core/application/config/`
- ローダー: `searchable-core/src/main/java/io/searchable/core/application/config/ConfigLoader.java`
- 主な利用箇所: CLI (`searchable-cli/src/main/java/io/searchable/cli/CliRuntime.java:22`)、
  およびそこから組み立てられる `SearchableLibrary`

Spring Boot 製のサンプルアプリ
(`examples/api`、`searchable-admin` など) は同じ YAML ではなく
`application.properties` の `searchable.*` プロパティを介して同等の設定を受け取り、
追加で `embedding` / `chunking` / `dictionary` / `api` / `cors` などの設定セクションを
持つ。それらは「サンプル固有の拡張」であり、本仕様の対象 (ライブラリ本体の YAML 契約) には
含めない (`searchable-admin/src/main/java/io/searchable/admin/config/SearchableProperties.java`、
`examples/api/src/main/java/io/searchable/example/api/config/SearchableProperties.java`)。

### 1.2 命名規約

`ConfigLoader` の Jackson `ObjectMapper` は `KEBAB_CASE` 命名戦略で設定されている
(`searchable-core/src/main/java/io/searchable/core/application/config/ConfigLoader.java:28`)。
YAML のキーはすべて **kebab-case** (例: `data-directory`、`default-architecture`、
`max-pool-size`) で記述する。

### 1.3 未知プロパティの扱い

`FAIL_ON_UNKNOWN_PROPERTIES` は `false` で構成されており、未知のキーは
警告なく無視される (`ConfigLoader.java:29`)。タイプミスは検出されないため、
キー名は本仕様の表に従って正確に記述すること。

### 1.4 トップレベル構造の最小例

```yaml
data-directory: ./data
persistence:
  type: H2
  url: "jdbc:h2:./data/metadata;MODE=PostgreSQL"
  username: sa
  password: ""
```

`index` / `plugins` / `global` を省略した場合は §3 で示すデフォルト値が適用される
(`SearchableConfig.java:29-35`)。

### 1.5 フル構成例

```yaml
data-directory: ./data
persistence:
  type: H2
  url: "jdbc:h2:./data/metadata;MODE=PostgreSQL"
  username: sa
  password: ""
  max-pool-size: 16
index:
  directory: ./data/indexes
  backend: FILESYSTEM
plugins:
  directory: ./plugins
global:
  default-architecture: HYBRID
  default-search-strategy: PARALLEL
  default-search-order: FULL_TEXT_FIRST
  analyzer: KUROMOJI
```

サンプル: `searchable-core/src/test/resources/searchable-test-config.yaml`、
`searchable-core/src/test/resources/searchable-postgres-config.yaml`

## 2. トップレベルフィールド

`SearchableConfig`
(`searchable-core/src/main/java/io/searchable/core/application/config/SearchableConfig.java:21-27`)
に対応する。

| フィールド | 型 | 必須 | デフォルト | 説明 |
| --- | --- | --- | --- | --- |
| `data-directory` | path (string) | 必須 | なし | データ全体の anchor となるディレクトリ。本フィールドが `null` の YAML は `IllegalStateException` で拒否される (`SearchableConfig.java:30`)。 |
| `persistence` | object | 必須 | なし | メタデータ DB 接続設定。`null` の YAML は拒否される (`SearchableConfig.java:31`)。詳細は §3。 |
| `index` | object | 任意 | `IndexConfig.defaults()` | Lucene インデックス保存先と backend。詳細は §4。 |
| `plugins` | object | 任意 | `PluginsConfig.classpathOnly()` (classpath のみ) | プラグイン JAR ディレクトリ。詳細は §5。 |
| `global` | object | 任意 | `SearchableGlobalConfig.defaults()` | namespace 既定値 (検索アーキテクチャー・戦略・順序・アナライザー)。詳細は §6。 |

## 3. `persistence` セクション

`PersistenceConfig`
(`searchable-core/src/main/java/io/searchable/core/infrastructure/persistence/PersistenceConfig.java:17-23`)
に対応する。

| フィールド | 型 | 必須 | デフォルト | 説明 |
| --- | --- | --- | --- | --- |
| `type` | string | 必須 | なし | 永続化バックエンド識別子。`H2` / `POSTGRESQL` / `JDBC` のいずれか (`DataSourceFactory.java:53-61`)。`null` および空白は拒否される (`PersistenceConfig.java:28,32-34`)。 |
| `url` | string | 必須 | なし | JDBC URL。`null` および空白は拒否される (`PersistenceConfig.java:29,35-37`)。 |
| `username` | string | 必須 | なし | DB ユーザー名。`null` は拒否される (`PersistenceConfig.java:30`)。 |
| `password` | string | 任意 | `""` (空文字列) | DB パスワード。`null` は空文字列に正規化される (`PersistenceConfig.java:31`)。 |
| `max-pool-size` | int | 任意 | `16` (`PersistenceConfig.DEFAULT_POOL_SIZE`) | 接続プール最大値。0 以下が指定された場合はデフォルト値に正規化される (`PersistenceConfig.java:38-40`)。 |

### 3.1 `type` の取り得る値

`DataSourceFactory.create` は `type` を大文字化して以下の分岐を行う
(`DataSourceFactory.java:51-62`)。

| 値 | 実装 | 備考 |
| --- | --- | --- |
| `H2` | `org.h2.jdbcx.JdbcConnectionPool` | 埋め込み (file/in-memory) ともに対応。 |
| `POSTGRESQL` | HikariCP + `org.postgresql.Driver` | プール名 `Searchable-PostgreSQL`。 |
| `JDBC` | HikariCP (driver は URL から自動推定) | H2 サーバーモードや他 RDBMS 用の汎用フォールバック。プール名 `Searchable-JDBC`。 |

上記以外の値は `IllegalArgumentException("Unsupported persistence type: ...")` を投げる
(`DataSourceFactory.java:59-60`)。

### 3.2 `url` のフォーマットと書き換え

`ConfigLoader.load(Path)` を経由した場合、H2 埋め込みファイルモードの URL は
`SearchableConfig.normalize` によって以下のように書き換えられる
(`SearchableConfig.java:96-178`、ADR-0002)。

- 対象: `jdbc:h2:` から始まり、かつ次の prefix を含まない URL
  - 非対象 prefix: `mem:` / `tcp:` / `ssl:` / `zip:` / `nio:` / `nioMapped:` /
    `nioMemFS:` / `nioMemLZF:` / `memFS:` / `memLZF:` (`SearchableConfig.java:111-115`)
- 書き換え内容
  1. 相対パスを `data-directory` (絶対化済み) を基準に絶対パスへ正規化
  2. クエリパラメーターに `AUTO_SERVER=TRUE` が含まれていなければ末尾に付加
     (`SearchableConfig.java:174-176`)
- `~` で始まるパスは H2 のチルダ展開に委ね、パス自体は触らないが
  `AUTO_SERVER=TRUE` の付加は行う (`SearchableConfig.java:166-167`)
- 非対象モード (`mem:` / `tcp:` 等) の URL、および非 H2 URL (`jdbc:postgresql:` 等)
  は変更されない (`SearchableConfig.java:138-145`)

H2 をリードオンリーで開く場合は URL 側で `ACCESS_MODE_DATA=r` を指定する必要がある
(`DataSourceFactory.java:65-69`)。

### 3.3 例

H2 埋め込み (デフォルト構成):

```yaml
persistence:
  type: H2
  url: "jdbc:h2:./data/metadata;MODE=PostgreSQL"
  username: sa
  password: ""
```

PostgreSQL:

```yaml
persistence:
  type: POSTGRESQL
  url: "jdbc:postgresql://db.example.invalid:5432/searchable"
  username: searchable
  password: "secret"
  max-pool-size: 32
```

## 4. `index` セクション

`IndexConfig`
(`searchable-core/src/main/java/io/searchable/core/application/config/IndexConfig.java:17-32`)
に対応する。

| フィールド | 型 | 必須 | デフォルト | 説明 |
| --- | --- | --- | --- | --- |
| `directory` | path (string) | 任意 | `<data-directory>/indexes` (正規化後) | Lucene インデックスのルートディレクトリ。`null` は拒否される (`IndexConfig.java:20`)。 |
| `backend` | enum | 任意 | `FILESYSTEM` | ストレージバックエンド。`null` 指定時は `FILESYSTEM` にフォールバック (`IndexConfig.java:21`)。 |

### 4.1 `backend` の取り得る値

`StorageBackend` (`searchable-core/src/main/java/io/searchable/core/infrastructure/lucene/StorageBackend.java:7-19`)。

| 値 | 意味 |
| --- | --- |
| `FILESYSTEM` | ローカルファイルシステムに索引を保存。リードオンリーモードに対応。 |
| `MEMORY` | プロセス内 `ByteBuffersDirectory` に保持。JVM 終了で消失。リードオンリーモード非対応。 |

### 4.2 `directory` のデフォルト解決と正規化

- セクション自体が未指定 → `IndexConfig.defaults()` が `Path.of("./data/indexes")` を持つ sentinel として適用される (`IndexConfig.java:29-31`、`SearchableConfig.java:32`)
- `ConfigLoader.load(Path)` 経由では `SearchableConfig.normalize` が以下の規則で書き換える (`SearchableConfig.java:79-87`)
  - sentinel (`./data/indexes`) と一致した場合は `<absoluteData>/indexes` に置換
  - 絶対パスはそのまま (`normalize()` を通すのみ)
  - 相対パスは絶対化済み `data-directory` を base に解決
- `backend == MEMORY` でも `directory` フィールド自体は必須 (layout 用呼び出し元のために保持される) だが、ランタイム上は内容が無視される (`IndexConfig.java:11-15`)。

## 5. `plugins` セクション

`PluginsConfig`
(`searchable-core/src/main/java/io/searchable/core/application/config/PluginsConfig.java:10-15`)
に対応する。

| フィールド | 型 | 必須 | デフォルト | 説明 |
| --- | --- | --- | --- | --- |
| `directory` | path (string) | 任意 | `null` (= classpath のみ) | プラグイン JAR を格納するディレクトリ。`null` の場合はクラスパス上のプラグインのみが対象。 |

### 5.1 デフォルト解決と正規化

- セクション自体が未指定 → `PluginsConfig.classpathOnly()` (`directory = null`)
  が適用される (`PluginsConfig.java:12-14`、`SearchableConfig.java:33`)
- `directory` が指定されている場合、`SearchableConfig.normalize` が `index.directory`
  と同じルールで `data-directory` 基準に絶対化する (`SearchableConfig.java:89-94`)。
  `null` の場合は `null` のまま保持される。

## 6. `global` セクション

`SearchableGlobalConfig`
(`searchable-core/src/main/java/io/searchable/core/application/config/SearchableGlobalConfig.java:18-48`)
に対応する。
namespace を新規作成する際に値が未指定の場合のフォールバックとして使われる。

| フィールド | 型 | 必須 | デフォルト | 説明 |
| --- | --- | --- | --- | --- |
| `default-architecture` | enum | 必須 (セクションを書く場合) | `FULL_TEXT` (セクション省略時) | `SearchType` の値。`null` は `NullPointerException` を投げる (`SearchableGlobalConfig.java:26`)。 |
| `default-search-strategy` | enum | 必須 (セクションを書く場合) | `SEQUENTIAL` (セクション省略時) | `SearchStrategy` の値。`null` は拒否される (`SearchableGlobalConfig.java:27`)。 |
| `default-search-order` | enum | 必須 (セクションを書く場合) | `FULL_TEXT_FIRST` (セクション省略時) | `SearchOrder` の値。`null` は拒否される (`SearchableGlobalConfig.java:28`)。 |
| `analyzer` | enum | 任意 | `KUROMOJI` | 日本語アナライザー実装。`null` は `KUROMOJI` にフォールバック (`SearchableGlobalConfig.java:29`)。 |

セクション自体を省略すると `SearchableGlobalConfig.defaults()` (全フィールドがデフォルト) が適用される
(`SearchableGlobalConfig.java:40-47`、`SearchableConfig.java:34`)。
一方、`global:` を書きつつ `default-architecture` 等の個別フィールドだけを省略した場合は
`NullPointerException` (→ `ConfigLoader` で `IllegalStateException` にラップ) になる。

### 6.1 `default-architecture` の取り得る値

`SearchType` (`searchable-core/src/main/java/io/searchable/core/domain/search/SearchType.java:6-10`)。

| 値 | 意味 |
| --- | --- |
| `FULL_TEXT` | 全文検索のみ。 |
| `VECTOR` | ベクトル検索のみ。 |
| `HYBRID` | 全文 + ベクトルのハイブリッド。 |

### 6.2 `default-search-strategy` の取り得る値

`SearchStrategy` (`searchable-core/src/main/java/io/searchable/core/domain/search/SearchStrategy.java:6-11`)。

| 値 | 意味 |
| --- | --- |
| `SEQUENTIAL` | 全文検索とベクトル検索を逐次実行。 |
| `PARALLEL` | 並列実行し結果をマージ。 |

### 6.3 `default-search-order` の取り得る値

`SearchOrder` (`searchable-core/src/main/java/io/searchable/core/domain/search/SearchOrder.java:6-9`)。
`default-search-strategy = SEQUENTIAL` のときの実行順序を決める。

| 値 | 意味 |
| --- | --- |
| `FULL_TEXT_FIRST` | 全文検索を先に実行。 |
| `VECTOR_FIRST` | ベクトル検索を先に実行。 |

### 6.4 `analyzer` の取り得る値

`AnalyzerType` (`searchable-core/src/main/java/io/searchable/core/infrastructure/lucene/AnalyzerType.java:14-32`)。

| 値 | 意味 |
| --- | --- |
| `KUROMOJI` | Lucene 同梱の Kuromoji アナライザー (常時利用可能)。 |
| `SUDACHI` | Sudachi バックエンド。`com.worksap.nlp:sudachi` と `lucene-analyzers-sudachi`、および Sudachi 辞書をクラスパス上に追加する必要がある。クラスが見つからない場合は `KUROMOJI` にフォールバックして警告ログを出す。 |

## 7. パス解決の優先順位

ADR-0002 (`docs/devel/adr/0002-data-directory-relative-path-resolution.md`) に基づき、
`ConfigLoader.load(Path)` は YAML をデシリアライズした後に
`SearchableConfig.normalize(raw, base)` を呼び出してすべてのパス系フィールドを絶対化する
(`ConfigLoader.java:32-44`)。

| フィールド | base | 解決規則 |
| --- | --- | --- |
| `data-directory` | YAML ファイルの親ディレクトリ (絶対化済み) | 絶対なら `normalize()` のみ。相対は base に解決 (`SearchableConfig.java:60-77`)。 |
| `index.directory` | 正規化後の `data-directory` | sentinel (`./data/indexes`) なら `<data-directory>/indexes` に置換。絶対なら as-is。相対は base に解決 (`SearchableConfig.java:79-87`)。 |
| `plugins.directory` | 正規化後の `data-directory` | `null` はそのまま。絶対なら as-is。相対は base に解決 (`SearchableConfig.java:89-94`)。 |
| `persistence.url` 内ファイルパス | 正規化後の `data-directory` | §3.2 の規則で書き換え。 |

`InputStream` ベースの `ConfigLoader.load(InputStream)` (`ConfigLoader.java:46-53`) は
YAML をデシリアライズするだけで `normalize` を呼ばないため、相対パスは JVM の
`user.dir` 基準で解釈される。テスト経路を除き、通常はファイル経由のロードを用いる。

Spring Boot 製サンプル (`examples/api`、`searchable-admin`) は本 YAML ではなく
`application.properties` から `SearchableProperties.normalizePaths()` 経由で読み込み、
base に JVM CWD を用いる (
`searchable-admin/src/main/java/io/searchable/admin/config/SearchableProperties.java:57-82`、
`examples/api/src/main/java/io/searchable/example/api/config/SearchableProperties.java:63-88`
)。本仕様 (ライブラリ本体の YAML 契約) のスコープ外。

## 8. 環境変数・システムプロパティによる上書き

`ConfigLoader` は YAML ファイルの内容のみを読み込み、環境変数やシステムプロパティに
よる上書き機構は実装していない (`ConfigLoader.java:21-54`)。`searchable.yaml`
そのものへの環境変数展開 (`${HOME}` など) もサポート対象外
(ADR-0002 §「検討した選択肢」#5)。

Spring Boot 製サンプル側では `SearchableProperties` が独自に環境変数を見るケースがある
(例: `SEARCHABLE_API_KEY` で API キーを上書き、
`examples/api/src/main/java/io/searchable/example/api/config/SearchableProperties.java:194-198`)
が、これらはサンプル固有の追加機能であり、ライブラリ本体の `searchable.yaml` 契約には
含まれない。

## 9. ロード時の例外仕様

`ConfigLoader` が投げる例外の主要種別は以下のとおり。

| 状況 | 例外 | 発生箇所 | メッセージ例 |
| --- | --- | --- | --- |
| `load(Path)` または `load(InputStream)` の引数が `null` | `NullPointerException` | `ConfigLoader.java:33,47` | `file must not be null` / `input must not be null` |
| YAML ファイルが存在しない / 読み取り失敗 | `IllegalStateException` (cause: `IOException`) | `ConfigLoader.java:41-43` | `Failed to read config from <path>` |
| YAML のパース失敗 (構文エラー、必須フィールド欠落による record コンストラクタ NPE 等) | `IllegalStateException` (cause: `IOException`) | `ConfigLoader.java:48-52` | `Failed to parse YAML config` |
| `data-directory` が未指定 | `IllegalStateException` (cause: `NullPointerException`) | `SearchableConfig.java:30` 由来 → `ConfigLoader.java:51` でラップ | `dataDirectory must not be null` |
| `persistence` セクションが未指定 | `IllegalStateException` (cause: `NullPointerException`) | `SearchableConfig.java:31` 由来 | `persistence must not be null` |
| `persistence.type` が `null` | `IllegalStateException` (cause: `NullPointerException`) | `PersistenceConfig.java:28` 由来 | `type must not be null` |
| `persistence.type` が空白 | `IllegalStateException` (cause: `IllegalArgumentException`) | `PersistenceConfig.java:32-34` 由来 | `type must not be blank` |
| `persistence.url` が `null` | `IllegalStateException` (cause: `NullPointerException`) | `PersistenceConfig.java:29` 由来 | `url must not be null` |
| `persistence.url` が空白 | `IllegalStateException` (cause: `IllegalArgumentException`) | `PersistenceConfig.java:35-37` 由来 | `url must not be blank` |
| `persistence.username` が `null` | `IllegalStateException` (cause: `NullPointerException`) | `PersistenceConfig.java:30` 由来 | `username must not be null` |
| `index.directory` が `null` (セクション自体は記述あり) | `IllegalStateException` (cause: `NullPointerException`) | `IndexConfig.java:20` 由来 | `directory must not be null` |
| `global.default-architecture` / `default-search-strategy` / `default-search-order` が `null` (セクション自体は記述あり) | `IllegalStateException` (cause: `NullPointerException`) | `SearchableGlobalConfig.java:26-28` 由来 | `defaultArchitecture must not be null` 等 |
| `persistence.type` が `H2` / `POSTGRESQL` / `JDBC` 以外 (`DataSourceFactory.create` 呼び出し時) | `IllegalArgumentException` | `DataSourceFactory.java:59-60` | `Unsupported persistence type: <value>` |

参考: `ConfigLoaderTest` (`searchable-core/src/test/java/io/searchable/core/application/config/ConfigLoaderTest.java:37-46`)、
`ConfigLoaderExtraTest` (`searchable-core/src/test/java/io/searchable/core/application/config/ConfigLoaderExtraTest.java:18-65`)
が上記挙動を検証している。

`DataSourceFactory.create` で発生する `IllegalArgumentException` は `ConfigLoader` の
内部ではなく `SearchableLibrary` 初期化など下流の利用箇所で投げられる点に注意
(YAML のパース自体は通り、起動時に失敗する形になる)。

## 10. ランタイム可変性

`SearchableGlobalConfigProvider` (`searchable-core/src/main/java/io/searchable/core/application/config/SearchableGlobalConfigProvider.java:13-29`)
は `SearchableGlobalConfig` を保持する `AtomicReference` ベースのホルダーであり、admin UI が
`update(SearchableGlobalConfig)` を呼ぶことでアプリケーション再起動なしに `global` セクション相当の
既定値を差し替えられる。これは「ランタイムでの変更経路」であり、`searchable.yaml`
ファイル自体のホットリロード機構ではない。

他のセクション (`data-directory` / `persistence` / `index` / `plugins`) は
ランタイム変更の手段を提供しない (反映するには `SearchableLibrary` の再構築が必要)。
