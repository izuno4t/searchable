# ADR-0002: 設定ファイルの path 解決を `data-directory` 基準に変更する

- ステータス: 採用
- 日付: 2026-06-01
- 関連: TASK-009(M1 残務)、ADR-0001(CLI fat jar 化)

## コンテキスト

`searchable-core` の `ConfigLoader` は YAML を Jackson でデシリアライズするだけで、
`Path` 系フィールド(`data-directory` / `index.directory` /
`plugins.directory` / `persistence.url` 内のファイルパス)はすべて relative の
まま保持される。

実体に到達する場面では:

- `Files.*` / `Files.newInputStream` などの NIO API → JVM の `user.dir` 基準で解決
- `org.apache.lucene.store.MMapDirectory` → 渡された `Path` をそのまま使うので、
  relative なら `user.dir` 基準
- H2 JDBC URL の `jdbc:h2:./data/...` → H2 自身が `user.dir` 基準で解決

つまり **全 path が JVM の起動ディレクトリ(CWD)基準** で解決される。

### 観測された不整合

`docs/public/getting-started.ja.md` のケース B では、CLI(ingest)と webapp(検索 UI)が
同じインデックスを共有する前提だが:

- リポジトリルートから `searchable-cli` を起動して `data-directory: ./data/webapp`
  に書き込む
- 別ディレクトリから `examples/webapp` を起動すると、その CWD 配下の
  `./data/webapp` を見にいくため、CLI が作ったインデックスが見えない

ユーザは `searchable.yaml` を指しているのだから、その「設定された場所」を
基準に解決するのが直感に合う、というフィードバック(2026-06-01)。

## 検討した選択肢

| # | 方式 | 直感 | 後方互換 | 採用 |
| --- | --- | --- | --- | --- |
| 1 | **現状(JVM CWD 基準)維持** | × | ✅ | ✕ |
| 2 | サンプル/ドキュメントで絶対パスを推奨し、挙動は変えない | △ | ✅ | ✕(現状の workaround にあたるが根治しない) |
| 3 | すべての path を config ファイルの親ディレクトリ基準に解決 | ○ | △ | ✕(`data-directory` を anchor として明示する形のほうが探索性が高い) |
| 4 | **`data-directory` を anchor とし、他 path はそれ基準で解決** | ◎ | △ | ✅ |
| 5 | 環境変数展開(`${HOME}` 等)サポートを追加 | △ | ✅ | ✕(直交した話題、必要なら別タスク) |

採用方針: **#4** を取り、`data-directory` 自身は **config ファイルの親ディレクトリ**
を基準に解決する。Spring Boot 経由で webapp が `data-directory` を直接プロパティ
として受ける場合、config ファイルは存在しないので **CWD を fallback の base** に
用いる。

## 決定

### 解決順序(優先度順)

1. `data-directory`
   - 絶対パスなら as-is(`normalize()` だけ通す)
   - 相対パスなら **config ファイルの親ディレクトリ** を base に解決
   - config ファイル経路を持たない構築(webapp の Spring Boot binding 等)では
     CWD を base にする
2. `index.directory`
   - 未設定(`null`)なら `<data-directory>/indexes` を採用
   - 絶対なら as-is
   - 相対なら **`data-directory` を base に解決**
3. `plugins.directory`
   - `null`(classpath only)はそのまま
   - 絶対 / 相対の扱いは `index.directory` と同じ
4. `persistence.url`(JDBC URL 内の組込み H2 ファイルパス)
   - URL が `jdbc:h2:` で始まり、かつ `mem:` / `tcp:` / `ssl:` / `zip:` / `nio*:` /
     `memFS:` / `memLZF:` などの非ファイルモードでない場合に限り、URL に
     含まれるファイルパスを `data-directory` 基準で絶対化して **URL を書き換え**
     てから H2 に渡す
   - 上記以外の URL(H2 サーバーモード / メモリ / 他 RDBMS)は touchしない
5. **正規化後の絶対パスを起動時に INFO ログ出力**(`SearchableLibrary` 初期化ログを利用)

### 実装契約

`ApplicationConfig` に `static normalize(ApplicationConfig raw, Path base)` を
追加する。`normalize` は path を絶対化した **新しい `ApplicationConfig` を返す**
不変な変換とし、呼び出し側で挿入する位置を 2 箇所に限定する:

- `ConfigLoader.load(Path file)` の戻り直前(`base = file.toAbsolutePath().getParent()`)
- `examples/webapp/SearchableWebappApplication#searchableLibrary` ビルド時
  (`base = Path.of("").toAbsolutePath()` で CWD)

`ApplicationConfig` 自身の record コンストラクタには「絶対パス必須」のような
不変条件を **追加しない**。直接 `new ApplicationConfig(...)` を呼ぶ既存テストや
今後のテストヘルパーが壊れないようにするため。

### 後方互換

- **共通ケースは挙動不変**: `searchable.yaml` を CWD に置き `data-directory: ./data/webapp`
  と書いている場合、config ファイル親 = CWD なので解決結果は変わらない
- **挙動が変わるケース**: `--config /etc/searchable.yaml` のように config を CWD
  と異なる場所に置いていた場合、保存先が `<CWD>/data/webapp` から
  `/etc/data/webapp` に変わる。CLI と webapp を別 CWD から起動して同じ index
  を共有していた運用は、本変更で **正しく動くようになる**(旧運用に依存して
  いれば config 内 path の見直しが必要)
- **getting-started の workaround 解除**: 直近で導入した `$HOME/searchable-data`
  の絶対パス策は、本タスク完了後は不要になるので「config 隣の相対パス
  (`data-directory: ./data`)」に書き換える

### マイグレーションノート

`docs/public/setup-guide.md` に以下を追記する:

- 0.x → 0.y 間で path 解決基準が CWD 基準から **config ファイル基準** に変更
  された旨
- 影響を受けるのは「相対パス + config を CWD と異なる場所に配置」の組合せ
- 切替方法: 既存の絶対パスはそのまま動く。相対パスに戻す場合は config 親
  ディレクトリからの相対で書く

## 影響範囲

### コード

- `searchable-core/src/main/java/io/searchable/core/application/config/ApplicationConfig.java`
  に `normalize(ApplicationConfig, Path)` を追加
- `ConfigLoader.load(Path file)` に正規化呼び出しを挿入
- `IndexConfig` / `PluginsConfig` / `PersistenceConfig` には変更を加えない
  (正規化は `ApplicationConfig` 側で完結させ、各サブ config を再構築する形にする)
- `SearchableWebappApplication` の `@Bean searchableLibrary` で正規化を実施
- `SearchableLibrary` の初期化ログ(`log.info("SearchableLibrary initialized ...")`)
  はすでに `dataDirectory` 等を出しているので追加変更は不要。`indexDirectory` /
  `persistence.url` も出るよう確認

### ドキュメント

- `docs/public/getting-started.ja.md` の `$HOME/searchable-data` workaround を撤去し
  「config 隣の `./data`」に書き換え
- `examples/webapp/README.md` の `./data/webapp` 説明を「webapp の
  application.properties デフォルト = CWD/data/webapp」のニュアンスを残しつつ、
  ベストプラクティスは「config ファイル + 隣の相対パス」と並記
- `docs/public/cli-guide.ja.md` の searchable.yaml 例を見直し
- `docs/public/setup-guide.md` にマイグレーションノート

### テスト

- `ConfigLoader` の正規化テスト
  - 絶対パスは as-is
  - 相対パスは config 親基準で解決
  - `index.directory` 未設定で `<data-directory>/indexes` がデフォルト
  - H2 URL の相対パスが書き換わる
  - H2 mem/tcp/ssl/zip など他モードでは URL を変更しない
- `SearchableWebappApplication` 経路では CWD fallback が機能することを確認

## 制限事項

- H2 URL の解析は限定的(`jdbc:h2:` の embedded file モードのみを書き換える)。
  独自パラメータが ; 区切りで続く一般的なケースのみカバー。クエリパラメータ
  (`?`)やパスにセミコロンを含む特殊ケースは tested-not-supported とする
- 他 RDBMS(PostgreSQL / 一般 JDBC)の URL は対象外。元々ファイルベースでは
  ないので path 解決の問題は発生しない

## 参考

- 本 ADR の議論経緯: チャットログ(M2 着手直後の M1 残務調査時、2026-06-01)
- Spring Boot の relative path 解決(`spring.config.location` 等)の挙動は本件
  と独立。webapp 側で Spring Boot による解決を経た値を `ApplicationConfig` に
  渡したあとに本 normalizer を通す設計
