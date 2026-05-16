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
# 既定 (bundled): docker/demo-data を使う
scripts/verify/run.sh

# Wikipedia からダウンロードして検証
scripts/verify/run.sh --source internet

# JAR を再ビルドしない (前回成果物の再利用)
scripts/verify/run.sh --skip-build

# 検証完了後にサーバを残す
scripts/verify/run.sh --keep-running

# ポート違いのサーバを使う (起動側は別途用意)
scripts/verify/run.sh --skip-build --base-url http://localhost:9090
```

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
| 取得元 | `docker/demo-data/*.md` | `ja.wikipedia.org` (MediaWiki API) |
| ネットワーク | 不要 | 必須 |
| 想定ファイル数 | 4 | 3 |
| 全文クエリ (step 5) | `ベクトル検索` | `形態素解析` |
| ベクトルクエリ (step 6) | `意味的な類似性` | `自然言語処理` |

検証の合否条件は同じ (ヒット件数 1 以上、ベクトルは スコア > 0)。

## トラブルシューティング

| 症状 | 原因 / 対処 |
| --- | --- |
| `port 8080 already in use` | 別プロセスが利用中。停止するか `--base-url` で逃がす |
| `server did not become ready within 180s` | 初回は ONNX モデル取得で時間がかかる。`.verify/server.log` を確認 |
| `no hits for full-text query` | クエリと取込ファイルが噛み合っていない。`sources/*.sh` の `VERIFY_QUERY_*` を見直す |
| `no hits for hybrid query` | ベクトル化が走っていない可能性。Namespace の `architecture=HYBRID` をログで確認 |
| `internet source: empty extract` | Wikipedia 側の記事名変更/レート制限。時間をおいて再試行 |
