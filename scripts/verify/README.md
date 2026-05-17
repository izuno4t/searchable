# scripts/verify/

`docs/verify.ja.md` で定義した動作検証ステップ 0〜7 を自動実行する
スクリプト群。**取込対象ドキュメントの取得元** を切り替えられるよう、
ソース取得部分は `sources/` 配下に分離してある。

## 構成

```text
scripts/verify/
├── README.md
├── run.sh                 # ステップ 0〜7 のオーケストレータ
└── sources/
    ├── bundled.sh         # ./docker/demo-data/ をコピー
    └── internet.sh        # ja.wikipedia.org から取得
```

`run.sh` は実行時に `sources/<NAME>.sh` を `source` し、
ステージング先 (`.verify/staging/`) にファイルを配置させてから、
そこを `examples/api` 経由で取込・検索・後始末する。

ソーススクリプトは 2 つの環境変数を export することが契約:

| 変数 | 用途 |
| --- | --- |
| `VERIFY_QUERY_FULLTEXT` | ステップ 5 (全文検索) に使うクエリ |
| `VERIFY_QUERY_VECTOR` | ステップ 6 (ベクトル/ハイブリッド) に使うクエリ |

新しいソースを追加するときは `sources/` に bash ファイルを足し、
上記 2 変数を export するだけでよい。

## 前提

- Java 21 以上 (`java -version`)
- `curl`, `jq`
- Maven Wrapper (`./mvnw`) は同梱済み
- TCP 8080 が空いている
- `internet` ソースを使う場合はインターネット接続

## 使い方

```bash
# 既定 (bundled, REST 取込): docker/demo-data + 合成サンプル
scripts/verify/run.sh

# Wikipedia からダウンロード
scripts/verify/run.sh --source internet

# ParserRegistry を実際に動かす取込 (HTML タグ除去等が走る)
scripts/verify/run.sh --ingest-via cli

# JAR を再ビルドしない (前回成果物の再利用)
scripts/verify/run.sh --skip-build

# 検証完了後にサーバを残す
scripts/verify/run.sh --keep-running

# ポート違いのサーバを使う (起動側は別途用意)
scripts/verify/run.sh --skip-build --base-url http://localhost:9090
```

## 取込モード (`--ingest-via`)

ステップ 4 の取込経路を 2 つから選ぶ。検索ステップ (5/6) は
どちらのモードでも同じ REST API 経由なので、合否判定は共通。

| モード | コマンド | 経由する取込口 | 動くパーサ | 速度 |
| --- | --- | --- | --- | --- |
| `api`(既定) | `POST /api/v1/index/batch` | JSON `content` をそのまま索引化 | なし(タグ・マークアップは残る) | 速い (API 1 回起動) |
| `cli` | `searchable ingest` | `ParserRegistry` 経由 | `PlainText` / `Markdown` / `AsciiDoc` / `Html` | 遅い (API 停止→CLI→再起動) |

CLI モードは「ハイブリッド検索の対象テキストがパーサで正しく抽出されているか」まで含めて検証したいときに使う。
H2 ファイルモードは JVM 排他のため、CLI が同じデータディレクトリを開けるよう API を一旦止める。

## 実行されるステップ

`docs/verify.ja.md` 表 §2 と 1:1 対応。

| # | スクリプト関数 | 確認内容 |
| --- | --- | --- |
| 0 | `step_0_prereq` | Java 21+ / 必須コマンド / ポート空き |
| 1 | `step_1_build` | `./mvnw -pl examples/api -am package -DskipTests` |
| 2 | `step_2_start` | `java -jar` で API 起動 → `GET /api/v1/namespaces` 200 |
| 3 | `step_3_namespace` | HYBRID で Namespace 作成、再作成は 409 |
| 4 | `step_4_index` | `POST /api/v1/index/batch` で全ファイル取込 |
| 5 | `step_5_fulltext` | `searchType=FULL_TEXT` で `hits>=1` |
| 6 | `step_6_vector` | `searchType=HYBRID` で `hits>=1` かつ `maxScore>0` |
| 7 | `step_7_cleanup` | Namespace を DELETE、後続 GET が 404 |

いずれかが失敗するとそこで終了し、終了コード 1 を返す。
サーバプロセスはトラップで停止される (`--keep-running` 指定時は残す)。

## 出力先

`run.sh` は以下に書き込む。除けば次回再構築される。

| パス | 内容 |
| --- | --- |
| `.verify/staging/` | ソーススクリプトが配置したファイル |
| `.verify/data/` | 検証中の Searchable データディレクトリ (`SEARCHABLE_DATA_DIRECTORY`) |
| `.verify/server.log` | API サーバの標準出力・エラー |
| `.verify/build.log` | `mvn package` の出力 (ビルド時のみ) |
| `.verify/searchable.yaml` | CLI 取込モード用に動的生成される YAML 設定 |
| `.verify/cli-ingest.log` | `searchable ingest` の出力 (CLI モード時) |

## 取得スクリプトの単独実行

`sources/*.sh` は `run.sh` から `source` される使い方の他に、
**ドキュメント取得だけを単独で走らせる** こともできる。
取得先ディレクトリは第 1 引数または `VERIFY_STAGING_DIR` で指定する。

```bash
# バンドル: docker/demo-data から ./out へコピー
scripts/verify/sources/bundled.sh ./out

# インターネット: ja.wikipedia.org から ./out へ取得
scripts/verify/sources/internet.sh ./out
```

実行すると、配置先のパス・置いたファイル一覧・推奨クエリを stdout
に出力する。検索を別途自分のスクリプトから叩きたい場合などに使う。

## ソースごとの差分

| 項目 | `bundled` | `internet` |
| --- | --- | --- |
| 取得元 | `docker/demo-data/*.md` + シェル生成サンプル | `ja.wikipedia.org` (MediaWiki API) |
| ネットワーク | 不要 | 必須 |
| ステージされる形式 | `.md` (実物) + `.txt` / `.adoc` / `.html` (合成) | `.txt` / `.html` / `.md` (実物) |
| 想定ファイル数 | 7 (.md 4 + 合成 3) | 3 (1 記事 × 3 形式) |
| 全文クエリ (step 5) | `ベクトル検索` | `形態素解析` |
| ベクトルクエリ (step 6) | `意味的な類似性` | `自然言語処理` |

検証の合否条件はソースに依らず同じ (ヒット件数 1 以上、ベクトルは スコア > 0)。

## 取込経路とパーサ検証の限界

現状の `run.sh` は `examples/api` の
`POST /api/v1/index/batch` (JSON) 経由で取込むため、`content`
フィールドに **抽出済みのテキスト** を渡す形になる。
つまり `searchable-core` 側の `ParserRegistry` を介さず、
ファイルの中身を UTF-8 文字列としてそのまま送っている。

各形式の扱いは以下のとおり:

| 形式 | 拡張子 | 現 `run.sh` での扱い | 本物のパーサ検証 |
| --- | --- | --- | --- |
| Plain text | `.txt` `.text` `.log` | そのまま `content` に詰める | `searchable-cli` 経由が必要 |
| Markdown | `.md` `.markdown` | そのまま `content` に詰める (Lucene 側はマークアップも索引化) | 同上 |
| AsciiDoc | `.adoc` `.asciidoc` | そのまま `content` に詰める | 同上 |
| HTML | `.html` `.htm` `.xhtml` | そのまま `content` に詰める (タグ語も索引化される) | 同上 |
| PDF | `.pdf` | **不可**(バイナリのため `content` 文字列にできない) | `searchable-cli` 経由が必要 |

`searchable-cli ingest` は `ParserRegistry` を経由するため、
テキスト系 4 形式は `--ingest-via cli` で正しいパース経路を検証できる。

### PDF だけは別

ただし `PdfParser` は `parse(InputStream, ...)` のオーバーロードしか
実装しておらず (`PdfParser.parse(String, ...)` は
`UnsupportedOperationException`)、
一方で `IngestCommand` は `Files.readString(...)` でファイル全文を
文字列として読み込んでから `parse(String, ...)` を呼ぶため、
**CLI 経由でも PDF はまだ取込めない**。
PDF のパース検証は現状以下のいずれかが必要:

1. `searchable-admin` の `DocumentUploadController`(Multipart アップロード)を使う
2. `IngestCommand` を InputStream 経路に差し替える(コア側の修正)

`scripts/verify/` の取込モードはどちらにも未対応なので、PDF を投入
する場合は別経路で行う想定。

## トラブルシューティング

| 症状 | 原因 / 対処 |
| --- | --- |
| `port 8080 already in use` | 別プロセスが利用中。停止するか `--base-url` で逃がす |
| `api did not become ready within 180s` | 初回は ONNX モデル取得で時間がかかる。`.verify/server.log` を確認 |
| `no hits for full-text query` | クエリと取込ファイルが噛み合っていない。`sources/*.sh` の `VERIFY_QUERY_*` を見直す |
| `no hits for hybrid query` | ベクトル化が走っていない可能性。Namespace の `architecture=HYBRID` をログで確認 |
| `internet source: empty extract` | Wikipedia 側の記事名変更/レート制限。時間をおいて再試行 |
| `cli ingest failed` | `.verify/cli-ingest.log` を確認。`UnsupportedOperationException` が出る場合は PDF が staging に混入している可能性 |
| `Database may be already in use` | API が停止し切る前に CLI が走った。`stop_api` のタイミング問題なので再実行で解消することが多い |
