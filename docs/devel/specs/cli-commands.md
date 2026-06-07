# CLI コマンド仕様

`searchable-cli` モジュールが提供する CLI の各サブコマンドの契約を定義する。

引数・終了コード・stdout/stderr フォーマット・例外時挙動を明示する。利用者ガイドではなく契約ドキュメント。

## 1. CLI 全体仕様

### 1.1 起動方法

エントリーポイントは `io.searchable.cli.SearchableCli` の `main(String[])` メソッド
（[`searchable-cli/src/main/java/io/searchable/cli/SearchableCli.java:53`][src-cli-main]）。

ビルド成果物は `maven-shade-plugin` による executable fat jar 単体で配布される
（配布方針は [`docs/devel/adr/0001-cli-executable-jar-with-shade-plugin.md`][adr-0001]）。

呼び出し形式:

```text
java -jar searchable-cli-<version>.jar [-c|--config <path>] <subcommand> [args]
```

picocli の `CommandLine` で構文解析され、`execute(args)` の戻り値がそのままプロセス終了コードとして
`System.exit(exit)` に渡される（[`SearchableCli.java:54-55`][src-cli-main]）。

サブコマンド未指定で起動した場合は親コマンドの `run()` が呼ばれ、`CommandLine.usage(this, System.out)`
により使用方法を stdout に印字して終了する（[`SearchableCli.java:48-51`][src-cli-run]）。

### 1.2 グローバルオプション

`SearchableCli` クラスに `mixinStandardHelpOptions = true` と `versionProvider` が設定されており、全サブコマンド
共通で以下のオプションを受け付ける（[`SearchableCli.java:25-45`][src-cli-class]）。

| オプション | 型 | 必須 | 既定値 | 説明 |
| --- | --- | --- | --- | --- |
| `-c`, `--config <path>` | `java.nio.file.Path` | サブコマンド側で利用するため実質必須 | なし | YAML 形式の `ApplicationConfig` ファイルパス |
| `-h`, `--help` | フラグ | 任意 | なし | 使用方法を stdout に印字して終了 |
| `-V`, `--version` | フラグ | 任意 | なし | バージョン文字列を stdout に印字して終了 |

`--config` は `scope = INHERIT` で全サブコマンドに継承される
（[`SearchableCli.java:43-45`][src-cli-config]）。値は picocli が `Path` 型に変換する。

`--version` の出力は `searchable-cli <version>` 形式。`<version>` は実行時 manifest の
`Implementation-Version` から取得され、未取得時は `dev` がフォールバックとして使われる
（[`SearchableCli.java:59-66`][src-cli-version]）。

### 1.3 データディレクトリの解決規則

`--config` で指定された YAML を `CliRuntime.loadConfig(Path)` 経由で `ConfigLoader` が読み込み、相対パスは
正規化される（[`CliRuntime.java:22-25`][src-cliruntime-load]）。正規化規則は
[`docs/devel/adr/0002-data-directory-relative-path-resolution.md`][adr-0002] に定義される
（`data-directory` は config ファイルの親ディレクトリ基準、`index.directory` 等は `data-directory` 基準）。

CLI から開く `SearchableLibrary` は次のいずれかのファクトリで生成される
（[`CliRuntime.java:27-36`][src-cliruntime-open]）。

- `CliRuntime.openLibrary(configPath)` — 通常モード（書き込み可）
- `CliRuntime.openReadOnlyLibrary(configPath)` — `readOnly(true)` 指定

各サブコマンドの仕様節で、いずれを使うかを「動作契約」に記す。

### 1.4 終了コード一覧

| コード | 意味 |
| --- | --- |
| `0` | 正常終了 |
| `1` | サブコマンド固有の論理エラー（例: `delete` で対象ドキュメントが存在しない、`ingest` で Namespace 未作成かつ非対話・`--create-namespace` 未指定） |
| `2` | 設定ファイル読み込み失敗（`validate-config` のみが明示的に返す）、または picocli の引数解析エラー |
| その他 | picocli の標準終了コード規約に準拠 |

`0`/`1`/`2` 以外は picocli の `CommandLine.execute()` が返す既定値（使用方法エラー時の `2` 等）に従う。

### 1.5 ロギング

CLI 標準出力は本仕様に従う。SLF4J + Logback のログは別チャネルで、本仕様では契約しない。
レベル設定の運用は利用者ガイド側に記述する。

---

## 2. サブコマンド一覧

`SearchableCli` の `@Command(subcommands = { ... })` に登録された 8 サブコマンドを対象とする
（[`SearchableCli.java:30-39`][src-cli-subcommands]）。

| サブコマンド | 実装クラス | 用途 |
| --- | --- | --- |
| `ingest` | `IngestCommand` | ファイル/ディレクトリの取り込み |
| `delete` | `DeleteCommand` | ドキュメント単体削除 |
| `rebuild` | `RebuildCommand` | Namespace 配下全削除 |
| `status` | `StatusCommand` | インデックス統計表示 |
| `backup` | `BackupCommand` | スナップショット取得 |
| `restore` | `RestoreCommand` | スナップショットからのリストア |
| `list-plugins` | `ListPluginsCommand` | 検出済みプラグイン列挙 |
| `validate-config` | `ValidateConfigCommand` | 設定ファイル検証 |

---

## 3. `ingest` サブコマンド

実装: [`searchable-cli/src/main/java/io/searchable/cli/command/IngestCommand.java`][src-ingest]

### 3.1 シノプシス

```text
searchable --config <yaml> ingest \
  --namespace <ns> \
  [--source-type <name>] \
  [--id-prefix <prefix>] \
  [--create-namespace[=true|false]] \
  <PATH>
```

### 3.2 引数・オプション

| 名称 | 種別 | 必須 | 既定値 | 型 | 説明 |
| --- | --- | --- | --- | --- | --- |
| `--namespace` | option | 必須 | — | `String` | 取込先 Namespace ID（[`IngestCommand.java:44-46`][src-ingest]） |
| `--source-type` | option | 任意 | `file` | `String` | データソースプラグイン名。`file` は組み込みファイルシステムパーサーを使用（[`IngestCommand.java:48-50`][src-ingest]） |
| `--id-prefix` | option | 任意 | `""` | `String` | 生成ドキュメント ID の接頭辞。ID は `<id-prefix><fileName>` で組み立てられる（[`IngestCommand.java:56-57`][src-ingest], [`IngestCommand.java:98-99`][src-ingest]） |
| `--create-namespace` | option | 任意 | 未指定（後述） | `Boolean` | Namespace が存在しない場合の自動作成可否（[`IngestCommand.java:59-61`][src-ingest]） |
| `PATH` | positional | 必須 | — | `Path` | 取込対象のファイルまたはディレクトリ（[`IngestCommand.java:52-54`][src-ingest]） |

オプション総数: 4、位置パラメータ数: 1。

### 3.3 動作契約

- `CliRuntime.openLibrary(configPath)` で `SearchableLibrary` を書き込みモードで開く
  （[`IngestCommand.java:70`][src-ingest]）。
- 対象 Namespace の存在を `NamespaceService.findById` で確認し、未存在の場合は §3.4 の手順で
  作成可否を決定する（[`IngestCommand.java:195-210`][src-ingest]）。
- `PATH` が通常ファイルなら単一エントリ、ディレクトリなら `Files.walk` で再帰列挙して通常ファイルのみを
  対象とする（[`IngestCommand.java:175-185`][src-ingest]）。
- 各ファイルは `ParserRegistry.defaults().resolveForFile(fileName)` でパーサーを解決する。未対応拡張子は
  警告を出してスキップし、バッチ全体は継続する（[`IngestCommand.java:80-87`][src-ingest]）。
- パーサーは `parser.parse(InputStream, fileName)` をバイト列で呼び出し、`ParsedDocument` を取得する
  （[`IngestCommand.java:88-96`][src-ingest]）。
- 取得した `Document` には次の予約 metadata が自動付与される（[`IngestCommand.java:97-113`][src-ingest]）。

  | キー | 値 |
  | --- | --- |
  | `url` | `Path.toAbsolutePath().toUri().toString()` |
  | `path` | `Path.toAbsolutePath().toString()` |
  | `contentType` | `DocumentParser#contentType()` の戻り値 |

- ドキュメント ID は `<id-prefix><fileName>`、`indexedAt` は `Instant.now()`
  （[`IngestCommand.java:98-113`][src-ingest]）。
- 書き込みは `IndexService.rebuildFrom(namespace, docs)` で **rebuild 方式** によりまとめて投入される。
  新規バージョンディレクトリへ書き出した後にアトミックに昇格する
  （[`IngestCommand.java:122`][src-ingest]）。
- `SearchableLibrary.close()` 後に `PidRegistry(dataDirectory).broadcastSighup()` を実行し、登録済みの
  別プロセスに SIGHUP を送る。送信件数を `notified` として保持する
  （[`IngestCommand.java:128`][src-ingest]）。

### 3.4 `--create-namespace` 解決順序

`decideAutoCreate()` で次の優先順位により決定する（[`IngestCommand.java:212-228`][src-ingest]）。

1. `--create-namespace=true` が明示されていれば作成する。
2. `--create-namespace=false` が明示されていれば作成せず終了コード `1` を返す。
3. オプション未指定で TTY（`System.console() != null`）の場合、`Namespace '<ns>' does not exist.
   Create it? [Y/n]:` を prompt として表示する。返答が `null`/`y`/`yes`/空 trim 結果のいずれかは作成、
   それ以外は非作成。
4. オプション未指定で非対話の場合、エラー扱いで作成しない。

作成時は `NamespaceService.create(namespace, namespace, NamespaceConfigPatch.empty())` を呼び、
`stderr` に `Created namespace '<ns>'.` を出力する（[`IngestCommand.java:207-208`][src-ingest]）。

### 3.5 終了コード

| コード | 条件 |
| --- | --- |
| `0` | 取込処理が完走（パーサー未対応によるスキップは含めて成功） |
| `1` | Namespace が存在せず作成も拒否された場合（[`IngestCommand.java:71-73`][src-ingest]） |

`PATH` が存在しない場合は `collectFiles()` が `IllegalArgumentException("PATH does not exist: ...")` を
スローし、picocli の例外ハンドラに渡る（[`IngestCommand.java:179-181`][src-ingest]）。

### 3.6 stdout / stderr 出力フォーマット

#### stdout

完了サマリは ANSI 装飾付きで次のテンプレートを `printSummary()` から出力する
（[`IngestCommand.java:150-168`][src-ingest]）。

```text

============================================================
  [OK] INGEST COMPLETE  --  namespace: <namespace>
============================================================
  Source       : <PATH-absolute> (source-type=<sourceType>)
  Indexed      : <indexed> documents
  Skipped      : <skipped> files (no parser)
  Elapsed      : <seconds>.<ss> s
------------------------------------------------------------
  By extension :  <ext1>=<n1>  <ext2>=<n2> ...
============================================================
```

- `<indexed>` は `IndexService.rebuildFrom` が返した投入件数。
- `<skipped>` はパーサー未解決のため除外したファイル数。
- `<By extension>` は拡張子（小文字、ドット付き。拡張子なしは `(no-ext)`）ごとの件数を 2 スペース区切りで
  並べる。スキップのみで取込ゼロの場合は `(none)`。
- `<seconds>` は `System.nanoTime()` 差分を秒に直し小数 2 桁で表示。

SIGHUP 送信件数が 1 以上のとき、上記の続きに次の 1 行が追加される
（[`IngestCommand.java:169-172`][src-ingest]）。

```text
  Notified <notified> running app(s) via SIGHUP -> hot reload.
```

#### stderr

- パーサー未対応ファイル: `WARN: No parser registered for <path> -- skipping.`
  （[`IngestCommand.java:83-84`][src-ingest]）
- Namespace 自動作成拒否時:
  `ERROR: Namespace '<ns>' does not exist. Re-run with --create-namespace to auto-create.`
  （[`IngestCommand.java:202-205`][src-ingest]）
- Namespace 自動作成成功時: `Created namespace '<ns>'.`（[`IngestCommand.java:208`][src-ingest]）

### 3.7 エラー時挙動

- パーサー解決失敗は warn 扱いでバッチ続行（前項参照）。
- `Files.newInputStream` や `parser.parse` 由来の `IOException` は `call()` の `throws Exception` 経由で
  picocli に伝播し、プロセスは異常終了する。
- `PATH` 不存在は `IllegalArgumentException` で異常終了。

---

## 4. `delete` サブコマンド

実装: [`searchable-cli/src/main/java/io/searchable/cli/command/DeleteCommand.java`][src-delete]

### 4.1 シノプシス

```text
searchable --config <yaml> delete --namespace <ns> --id <documentId>
```

### 4.2 引数・オプション

| 名称 | 種別 | 必須 | 既定値 | 型 | 説明 |
| --- | --- | --- | --- | --- | --- |
| `--namespace` | option | 必須 | — | `String` | 削除対象が属する Namespace ID（[`DeleteCommand.java:17`][src-delete]） |
| `--id` | option | 必須 | — | `String` | 削除対象ドキュメント ID（[`DeleteCommand.java:18`][src-delete]） |

オプション総数: 2。

### 4.3 動作契約

- `CliRuntime.openLibrary(configPath)` で書き込みモードのライブラリを開く（[`DeleteCommand.java:22`][src-delete]）。
- `IndexService.delete(namespace, id)` を呼び、`boolean removed` を取得する
  （[`DeleteCommand.java:23`][src-delete]）。
- ライブラリは try-with-resources で必ず close される。

### 4.4 終了コード

| コード | 条件 |
| --- | --- |
| `0` | 対象ドキュメントが存在し、削除に成功 |
| `1` | 対象ドキュメントが存在しない（[`DeleteCommand.java:27`][src-delete]） |

### 4.5 stdout / stderr 出力フォーマット

#### stdout

```text
Deleted <id> from <namespace>.
```

または

```text
No document with id <id> in <namespace>.
```

（[`DeleteCommand.java:24-26`][src-delete]）

#### stderr

このコマンドからは stderr への明示出力はない。SIGHUP のブロードキャストも行わない。

### 4.6 エラー時挙動

`IndexService.delete` の例外は picocli の標準例外ハンドラに伝播する。

---

## 5. `rebuild` サブコマンド

実装: [`searchable-cli/src/main/java/io/searchable/cli/command/RebuildCommand.java`][src-rebuild]

### 5.1 シノプシス

```text
searchable --config <yaml> rebuild --namespace <ns>
```

### 5.2 引数・オプション

| 名称 | 種別 | 必須 | 既定値 | 型 | 説明 |
| --- | --- | --- | --- | --- | --- |
| `--namespace` | option | 必須 | — | `String` | クリア対象の Namespace ID（[`RebuildCommand.java:17`][src-rebuild]） |

オプション総数: 1。

### 5.3 動作契約

- `CliRuntime.openLibrary(configPath)` で書き込みモードのライブラリを開く（[`RebuildCommand.java:21`][src-rebuild]）。
- `IndexService.rebuild(namespace)` を呼び、Namespace 配下の全ドキュメントを削除する
  （[`RebuildCommand.java:22`][src-rebuild]）。

### 5.4 終了コード

| コード | 条件 |
| --- | --- |
| `0` | 常に成功（[`RebuildCommand.java:25`][src-rebuild]） |

### 5.5 stdout / stderr 出力フォーマット

#### stdout

```text
Cleared namespace <namespace>; ready for re-ingest.
```

（[`RebuildCommand.java:23`][src-rebuild]）

#### stderr

明示出力なし。

### 5.6 エラー時挙動

`IndexService.rebuild` の例外は picocli に伝播する。`ingest` と異なり SIGHUP ブロードキャストは行わない。

---

## 6. `status` サブコマンド

実装: [`searchable-cli/src/main/java/io/searchable/cli/command/StatusCommand.java`][src-status]

### 6.1 シノプシス

```text
searchable --config <yaml> status
```

### 6.2 引数・オプション

専用オプションなし（オプション総数: 0）。

### 6.3 動作契約

- `CliRuntime.openReadOnlyLibrary(configPath)` で **read-only** モードのライブラリを開く
  （[`StatusCommand.java:19`][src-status]）。
- `IndexStatisticsService.aggregate()` で `Statistics` を取得し、データディレクトリ・Namespace 数・ドキュメント数・
  インデックスサイズ・最終更新時刻を表示する（[`StatusCommand.java:20-23`][src-status]）。

### 6.4 終了コード

| コード | 条件 |
| --- | --- |
| `0` | 常に成功（[`StatusCommand.java:42`][src-status]） |

### 6.5 stdout / stderr 出力フォーマット

#### stdout

ANSI 装飾付きで次のテンプレートを出力（[`StatusCommand.java:25-41`][src-status]）。

```text

============================================================
  [OK] INDEX STATUS  --  data: <dataDir>
============================================================
  Namespaces   : <count>
  Documents    : <count>
  Index size   : <human> KB  (<bytes> bytes)
  Last updated : <ISO-8601 or "(no data)">
============================================================
```

- `<dataDir>` は `configuration().dataDirectory()` の絶対表現。
- `<human>` は 1024 進数で `B`/`KB`/`MB`/`GB`/`TB`/`PB` を選択し、小数 2 桁で表示
  （[`StatusCommand.java:46-57`][src-status]）。1024 未満は `<bytes> B` 形式。
- `<ISO-8601 or "(no data)">` は `Statistics.lastUpdated()` が null のとき `(no data)`、それ以外は
  `Instant.toString()` の結果（[`StatusCommand.java:22-23`][src-status]）。

#### stderr

明示出力なし。

### 6.6 エラー時挙動

統計取得時の例外は picocli の標準例外ハンドラに伝播する。

---

## 7. `backup` サブコマンド

実装: [`searchable-cli/src/main/java/io/searchable/cli/command/BackupCommand.java`][src-backup]

### 7.1 シノプシス

```text
searchable --config <yaml> backup --target <dir>
```

### 7.2 引数・オプション

| 名称 | 種別 | 必須 | 既定値 | 型 | 説明 |
| --- | --- | --- | --- | --- | --- |
| `--target` | option | 必須 | — | `Path` | スナップショット出力先ディレクトリ（[`BackupCommand.java:20-22`][src-backup]） |

オプション総数: 1。

### 7.3 動作契約

- `CliRuntime.openLibrary(configPath)` で書き込みモード相当のライブラリを開く
  （[`BackupCommand.java:26`][src-backup]）。
- `BackupService(library.indexProvider(), new IndexLayout(library.configuration().index().directory()))`
  を生成し、`backups.snapshot(target)` を実行する（[`BackupCommand.java:27-29`][src-backup]）。
- 戻り値 `summary` から取得時刻・総バイト数・対象 Namespace 数を取り出して表示する。

### 7.4 終了コード

| コード | 条件 |
| --- | --- |
| `0` | スナップショット成功（[`BackupCommand.java:32`][src-backup]） |

### 7.5 stdout / stderr 出力フォーマット

#### stdout

```text
Backup taken at <takenAt> -> <target> (<totalBytes> bytes, <namespaceCount> namespaces)
```

（[`BackupCommand.java:30-31`][src-backup]）

- `<takenAt>` は `BackupService.Summary#takenAt()` の `toString()` 結果。
- `<namespaceCount>` は `summary.namespaceIds().size()`。

#### stderr

明示出力なし。

### 7.6 エラー時挙動

`BackupService.snapshot` の例外は picocli の標準例外ハンドラに伝播する。

---

## 8. `restore` サブコマンド

実装: [`searchable-cli/src/main/java/io/searchable/cli/command/RestoreCommand.java`][src-restore]

### 8.1 シノプシス

```text
searchable --config <yaml> restore --source <dir>
```

### 8.2 引数・オプション

| 名称 | 種別 | 必須 | 既定値 | 型 | 説明 |
| --- | --- | --- | --- | --- | --- |
| `--source` | option | 必須 | — | `Path` | `backup` で出力されたディレクトリ（[`RestoreCommand.java:20-22`][src-restore]） |

オプション総数: 1。

### 8.3 動作契約

- `CliRuntime.openLibrary(configPath)` でライブラリを開く（[`RestoreCommand.java:26`][src-restore]）。
- `RestoreService(library.indexProvider(), new IndexLayout(library.configuration().index().directory()))`
  を生成し、`restore.restoreAll(source)` を実行する（[`RestoreCommand.java:27-29`][src-restore]）。
- 戻り値の `Collection` のサイズを復元 Namespace 数として表示する。

### 8.4 終了コード

| コード | 条件 |
| --- | --- |
| `0` | リストア成功（[`RestoreCommand.java:32`][src-restore]） |

### 8.5 stdout / stderr 出力フォーマット

#### stdout

```text
Restored <count> namespace(s) from <source>
```

（[`RestoreCommand.java:30-31`][src-restore]）

#### stderr

明示出力なし。

### 8.6 エラー時挙動

`RestoreService.restoreAll` の例外は picocli の標準例外ハンドラに伝播する。

---

## 9. `list-plugins` サブコマンド

実装: [`searchable-cli/src/main/java/io/searchable/cli/command/ListPluginsCommand.java`][src-listplugins]

### 9.1 シノプシス

```text
searchable --config <yaml> list-plugins
```

### 9.2 引数・オプション

専用オプションなし（オプション総数: 0）。

### 9.3 動作契約

- `CliRuntime.openReadOnlyLibrary(configPath)` で **read-only** モードのライブラリを開く
  （[`ListPluginsCommand.java:18`][src-listplugins]）。
- `library.pluginLoader().overview()` を取得し、SPI ごとに登録名一覧を出力する
  （[`ListPluginsCommand.java:19-26`][src-listplugins]）。

### 9.4 終了コード

| コード | 条件 |
| --- | --- |
| `0` | 常に成功（[`ListPluginsCommand.java:28`][src-listplugins]） |

### 9.5 stdout / stderr 出力フォーマット

#### stdout

`overview()` の各 SPI ごとに次のブロックを 1 件ずつ出力する
（[`ListPluginsCommand.java:19-26`][src-listplugins]）。

```text
<spiName>:
  - <pluginName1>
  - <pluginName2>
```

登録名が空の場合は `(none)` を 1 行で出力する。

```text
<spiName>:
  (none)
```

`overview()` の `Map` の反復順序が出力順となる。

#### stderr

明示出力なし。

### 9.6 エラー時挙動

`pluginLoader().overview()` の例外は picocli の標準例外ハンドラに伝播する。

---

## 10. `validate-config` サブコマンド

実装: [`searchable-cli/src/main/java/io/searchable/cli/command/ValidateConfigCommand.java`][src-validate]

### 10.1 シノプシス

```text
searchable --config <yaml> validate-config
```

### 10.2 引数・オプション

専用オプションなし（オプション総数: 0）。グローバル `--config` のみを参照する。

### 10.3 動作契約

- `CliRuntime.loadConfig(configPath)` で `ApplicationConfig` を読み込むのみで、`SearchableLibrary` は
  開かない（dry-run）（[`ValidateConfigCommand.java:19-20`][src-validate]）。
- 読み込み成功時に主要設定項目を stdout に表示する。

### 10.4 終了コード

| コード | 条件 |
| --- | --- |
| `0` | 設定読み込み成功（[`ValidateConfigCommand.java:28`][src-validate]） |
| `2` | `RuntimeException` を捕捉した場合（読み込み失敗）（[`ValidateConfigCommand.java:29-32`][src-validate]） |

### 10.5 stdout / stderr 出力フォーマット

#### stdout（成功時）

```text
OK: <configPath>
  data-directory : <dataDirectory>
  persistence    : <persistenceType> <persistenceUrl>
  index dir      : <indexDirectory>
  plugins dir    : <pluginsDirectory>
  default search : <defaultArchitecture>/<defaultSearchStrategy>
```

（[`ValidateConfigCommand.java:21-27`][src-validate]）

各値は `ApplicationConfig` のサブ設定 record から取得した文字列表現
（`Path.toString()` または getter 戻り値）。

#### stderr（失敗時）

```text
Configuration error: <RuntimeException.getMessage()>
```

（[`ValidateConfigCommand.java:30`][src-validate]）

### 10.6 エラー時挙動

`RuntimeException`（`ConfigLoader` 内の `JsonProcessingException` ラップ等を含む）を捕捉して
終了コード `2` を返す。チェック例外は throws 宣言されておらず picocli の通常パスに乗る。

---

## 参照

- [`searchable-cli/src/main/java/io/searchable/cli/SearchableCli.java`][src-cli-class]
- [`searchable-cli/src/main/java/io/searchable/cli/CliRuntime.java`][src-cliruntime-load]
- [`docs/devel/adr/0001-cli-executable-jar-with-shade-plugin.md`][adr-0001]
- [`docs/devel/adr/0002-data-directory-relative-path-resolution.md`][adr-0002]
- 利用者向け解説: [`docs/public/cli-guide.ja.md`](../../public/cli-guide.ja.md)

[src-cli-main]: ../../../searchable-cli/src/main/java/io/searchable/cli/SearchableCli.java
[src-cli-run]: ../../../searchable-cli/src/main/java/io/searchable/cli/SearchableCli.java
[src-cli-class]: ../../../searchable-cli/src/main/java/io/searchable/cli/SearchableCli.java
[src-cli-config]: ../../../searchable-cli/src/main/java/io/searchable/cli/SearchableCli.java
[src-cli-version]: ../../../searchable-cli/src/main/java/io/searchable/cli/SearchableCli.java
[src-cli-subcommands]: ../../../searchable-cli/src/main/java/io/searchable/cli/SearchableCli.java
[src-cliruntime-load]: ../../../searchable-cli/src/main/java/io/searchable/cli/CliRuntime.java
[src-cliruntime-open]: ../../../searchable-cli/src/main/java/io/searchable/cli/CliRuntime.java
[src-ingest]: ../../../searchable-cli/src/main/java/io/searchable/cli/command/IngestCommand.java
[src-delete]: ../../../searchable-cli/src/main/java/io/searchable/cli/command/DeleteCommand.java
[src-rebuild]: ../../../searchable-cli/src/main/java/io/searchable/cli/command/RebuildCommand.java
[src-status]: ../../../searchable-cli/src/main/java/io/searchable/cli/command/StatusCommand.java
[src-backup]: ../../../searchable-cli/src/main/java/io/searchable/cli/command/BackupCommand.java
[src-restore]: ../../../searchable-cli/src/main/java/io/searchable/cli/command/RestoreCommand.java
[src-listplugins]: ../../../searchable-cli/src/main/java/io/searchable/cli/command/ListPluginsCommand.java
[src-validate]: ../../../searchable-cli/src/main/java/io/searchable/cli/command/ValidateConfigCommand.java
[adr-0001]: ../adr/0001-cli-executable-jar-with-shade-plugin.md
[adr-0002]: ../adr/0002-data-directory-relative-path-resolution.md
