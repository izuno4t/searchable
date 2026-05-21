# Searchable CLI 利用ガイド

`searchable-cli` モジュールが提供するコマンドラインツール
(`searchable`) の利用方法をまとめる。インデックスの取込・削除・
再構築・状態確認・バックアップ/リストア・プラグイン確認・設定検証を
カバーする。

## 1. 前提

- Java 21 以上
- `searchable.yaml` 形式の設定ファイル(例: `docs/setup-guide.md` 参照)

## 2. ビルドと配布

```bash
mvn -pl searchable-cli -am clean package
# 生成物:
#   searchable-cli/target/searchable-cli-1.0.0-SNAPSHOT.jar
#   searchable-cli/target/lib/        (依存 JAR)
#   searchable-cli/src/main/scripts/searchable  (起動シェル)
```

起動シェルは以下のパスから classpath を探索する:

1. `$SEARCHABLE_HOME/lib/*`
2. シェルと同階層の `lib/`
3. 開発チェックアウト時は `target/classes` + `target/lib/*`

## 3. 共通オプション

| オプション | 説明 |
| --- | --- |
| `-c`, `--config <path>` | YAML 設定ファイルのパス(全サブコマンドで必須) |
| `-h`, `--help` | ヘルプを表示 |
| `-V`, `--version` | バージョンを表示 |

## 4. サブコマンド

### ingest

ファイルまたはディレクトリを再帰的に走査し、`searchable-core` で
取込する。

```bash
searchable --config searchable.yaml ingest \
  --namespace docs \
  --source-type file \
  --id-prefix manual- \
  path/to/source
```

オプション:

- `--namespace`: 取込先 Namespace ID
- `--source-type`: プラグイン名(`file` は組込のファイルシステム)
- `--id-prefix`: 生成されるドキュメント ID の接頭辞(省略可)

各ファイルには予約 metadata キーが自動で設定される(詳細は
[docs/architecture.md §5.7](architecture.md)):

| キー | 値 | 由来 |
| --- | --- | --- |
| `url` | `file:///<absolute-path>` (RFC 3986 URI) | `Path.toUri()` |
| `path` | 絶対ファイルパス(string) | `Path.toAbsolutePath()` |
| `contentType` | MIME(例: `text/markdown`, `application/pdf`) | パーサー定義 |

`metadata.url` は検索結果からの直リンク用、`metadata.contentType` は UI
側の表示切替やセクション anchor 生成の base として使われる。

### delete

```bash
searchable --config searchable.yaml delete --namespace docs --id manual-foo.md
```

### rebuild

Namespace 配下のドキュメントを全削除する。再取込前のクリア用。

```bash
searchable --config searchable.yaml rebuild --namespace docs
```

> 内部実装は検索を停止しない方式: 旧バージョンのインデックスで検索を
> 続けたまま、新しい空のバージョンディレクトリを書き出し、完了時に
> ディレクトリ名を不可分にリネームして切り替える。旧バージョンは
> 既定 30 秒の猶予期間を経たあとに削除される。詳細は
> [docs/architecture.md §5.7](architecture.md) 参照。

### status

全 Namespace を読み込み専用モードで開き、ドキュメント数とディスク
使用量を表示する。

```bash
searchable --config searchable.yaml status
```

### backup / restore

`BackupService` / `RestoreService` を呼び出す。

```bash
searchable --config searchable.yaml backup --target /var/backups/searchable
searchable --config searchable.yaml restore --source /var/backups/searchable
```

### list-plugins

`PluginLoader.overview()` を経由して、現在 classpath から検出可能な
プラグイン一覧を出力する。

```bash
searchable --config searchable.yaml list-plugins
```

### validate-config

設定ファイルを読み込み、解釈結果を表示する(ドライラン)。

```bash
searchable --config searchable.yaml validate-config
```

設定エラー時は終了コード 2 と `Configuration error: ...` メッセージ
を出力する。

## 5. ログ出力

CLI は `logback.xml` を同梱しており、デフォルトでは `INFO` レベル。
環境変数 `SEARCHABLE_LOG_LEVEL=DEBUG` で詳細出力に切り替えられる。

## 6. 終了コード

| コード | 意味 |
| --- | --- |
| 0 | 正常終了 |
| 1 | サブコマンド固有の論理エラー(例: `delete` 時に対象なし) |
| 2 | 設定/引数のエラー |
| その他 | picocli 規約に準拠(`--help` で確認) |

## 7. 関連ドキュメント

- [docs/setup-guide.md](setup-guide.md) — 設定ファイルの記法
- [docs/usage.ja.md](usage.ja.md) — ライブラリとしての利用
- [docs/architecture.md](architecture.md) — ストレージレイヤーと構成
