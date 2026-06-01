# Searchable - Getting Started

Searchable を最初に試すための 2 つの最短ルートをまとめる。用途に応じて
片方だけ実施すればよい。

| ケース | 向く用途 | 所要 | 手数 |
| --- | --- | --- | --- |
| **A. API でとにかく動かす** | curl だけで動作確認したい / 自分のアプリから REST 経由で使う前提 | 〜5 分 | サーバ起動 + curl 3 本 |
| **B. Webアプリで検索する** | ローカル文書をインデックス化してブラウザの検索画面を体験したい | 〜10 分 | ドキュメント準備 + CLI ingest + webapp 起動 |

両ケースとも「事前準備」を済ませてから着手する。

> 本ガイドは webapp / API サンプルでの動作確認用。Java ライブラリとして
> 自分のアプリに組み込む場合は [usage.ja.md](usage.ja.md)、CLI の全
> サブコマンドは [cli-guide.ja.md](cli-guide.ja.md) を参照。

---

## 事前準備

### 前提環境

| 項目 | バージョン |
| --- | --- |
| Java | 21 以上 |
| Maven | 3.9 以上(同梱の `./mvnw` を使う場合は不要) |
| メモリ | 1GB 以上の空き |
| OS | macOS / Linux / Windows |

`java -version` と `./mvnw -v` が成功すれば準備完了。

### ソース取得とコアライブラリのインストール

```bash
git clone <repository-url>
cd searchable
./mvnw -B clean install -DskipTests
```

3 つ目のコマンドで `searchable-core` などが `~/.m2/repository` に置かれ、
ケース A / B どちらの `examples/*` も依存解決できるようになる。

---

## ケース A: API でとにかく動かす

最小構成で REST API サーバーを立ち上げ、`curl` 3 本で
「Namespace 作成 → ドキュメント登録 → 検索」を通す。

### A.1 API サーバーをビルドする

`examples/api` はルート POM のリアクターに含まれない **独立 Maven
プロジェクト** のため、`-f` でサブプロジェクトの POM を指定する。

```bash
./mvnw -B -f examples/api/pom.xml package
```

成果物: `examples/api/target/api-example-1.0.0-SNAPSHOT.jar`(Spring Boot fat jar)

### A.2 起動する

```bash
java -jar examples/api/target/api-example-1.0.0-SNAPSHOT.jar \
     --spring.config.location=examples/api/application.properties
```

ログに `Started SearchableApplication` が出ればポート `8080` で待ち受け中。

別ターミナルで疎通確認:

```bash
curl http://localhost:8080/api/v1/namespaces
# => {"namespaces":[],"total":0}
```

### A.3 Namespace を作る

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "quickstart",
    "name": "Quickstart",
    "config": {"architecture": "FULL_TEXT"}
  }'
```

### A.4 ドキュメントを 1 件登録する

```bash
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "quickstart",
    "document": {
      "id": "doc-1",
      "title": "Searchable について",
      "content": "Searchable は日本語形態素解析に対応した全文検索ライブラリです。",
      "metadata": {
        "url": "https://docs.example.com/doc-1",
        "contentType": "text/markdown"
      }
    }
  }'
```

### A.5 検索する

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "形態素解析",
    "namespaceIds": ["quickstart"]
  }'
```

`hits` 配列に `doc-1` が含まれていれば成功。

複数件の一括取込・全 API 一覧は [examples/api/README.md](../examples/api/README.md)
および [api-specification.ja.md](../examples/api/api-specification.ja.md) を参照。

---

## ケース B: webapp で UI から検索する

ローカルのドキュメントを CLI で取り込んでインデックスを作り、
そのインデックスを参照する webapp を起動してブラウザで検索する。

### B.1 CLI と webapp をビルドする

```bash
./mvnw -B -f searchable-cli/pom.xml package
./mvnw -B -f examples/webapp/pom.xml package
```

成果物:

- `searchable-cli/target/searchable-cli-1.0.0-SNAPSHOT.jar` と `searchable-cli/target/lib/`
- `examples/webapp/target/webapp-example-1.0.0-SNAPSHOT.jar`(Spring Boot fat jar)

### B.2 ドキュメントを用意する

検索したいドキュメントを任意のディレクトリに置く。本ガイドでは動作確認用に
最小サンプルを作成する。

```bash
mkdir -p ~/sample-docs
cat > ~/sample-docs/hello.md <<'EOF'
# はじめに

Searchable は日本語形態素解析に対応した全文検索ライブラリです。
ベクトル検索と組み合わせたハイブリッド検索にも対応しています。
EOF

cat > ~/sample-docs/architecture.md <<'EOF'
# アーキテクチャ概要

Searchable は Lucene をベースに、Kuromoji / Sudachi の形態素解析と
ONNX Runtime によるベクトル化を統合した全文検索エンジンです。
EOF
```

対応形式: Markdown / プレーンテキスト / HTML / AsciiDoc / PDF /
Office(Word・Excel・PowerPoint)。ファイル拡張子で自動判別される。

> 既に手元のドキュメントディレクトリ(例: `~/Documents/handbook`)を使う場合は
> 本節をスキップして、B.3 のパスを差し替えればよい。

### B.3 CLI でインデックスを作成する

#### 設定ファイル

リポジトリルートに `searchable.yaml` を作成する。`data-directory` は
**インデックスとメタデータ DB の保存先** で、webapp のデフォルト
(`./data/webapp`)に揃えておくと B.4 の起動が追加設定なしで済む。

```yaml
# searchable.yaml - CLI と webapp が共有する index/DB の置き場所
data-directory: ./data/webapp

persistence:
  type: H2
  url: "jdbc:h2:./data/webapp/metadata;MODE=PostgreSQL"
  username: sa
  password: ""

index:
  directory: ./data/webapp/indexes
```

> ここで指定するパスは **保存先**(webapp が所有する領域)。前節の
> `~/sample-docs` は **ソース** で、`ingest` の引数として別途渡す。
> 両者は無関係のディレクトリで構わない。

#### ingest 実行

```bash
java -jar searchable-cli/target/searchable-cli-1.0.0-SNAPSHOT.jar \
  --config ./searchable.yaml \
  ingest \
  --namespace default \
  --source-type file \
  ~/sample-docs
```

完了すると以下が生成される:

- `./data/webapp/indexes/default/<timestamp>/` — Lucene インデックス
- `./data/webapp/metadata.mv.db` — メタデータ DB(H2)

#### 取込結果の確認

```bash
java -jar searchable-cli/target/searchable-cli-1.0.0-SNAPSHOT.jar \
  --config ./searchable.yaml status
```

`default` Namespace のドキュメント数とディスク使用量が表示されれば成功。

### B.4 webapp を起動する

webapp はデフォルトで H2 を組込みモードで開く(シングルライター)。
CLI の `ingest` が終わってから起動する。

```bash
java -jar examples/webapp/target/webapp-example-1.0.0-SNAPSHOT.jar
```

ログに `Started SearchableApplication` が出れば
`http://localhost:8080` で待ち受け開始。webapp の
`application.properties` のデフォルトが `./data/webapp` を指しているため、
追加設定なしで B.3 のインデックスを読み込む。

別の場所に置いた場合は明示する:

```bash
java -jar examples/webapp/target/webapp-example-1.0.0-SNAPSHOT.jar \
  --searchable.data-directory=./data/webapp \
  --searchable.persistence.url="jdbc:h2:./data/webapp/metadata;MODE=PostgreSQL"
```

### B.5 ブラウザで検索する

ブラウザで <http://localhost:8080/> を開き、検索ボックスにクエリを入力する。

例: `形態素解析` で検索 → `hello.md` と `architecture.md` の両方がヒット。

クリックすると詳細ページ(`/documents/{namespace}/{id}`)へ遷移し、
ハイライト付きの本文と、`metadata.url` で生成された原ファイルへの
リンクが表示される。

URL 直叩きでも実行可:

```bash
curl 'http://localhost:8080/?q=%E5%BD%A2%E6%85%8B%E7%B4%A0%E8%A7%A3%E6%9E%90'
```

### B.6 ドキュメントを追加する

新しいファイルを `~/sample-docs/` に置き、B.3 の `ingest` を再実行すれば
差分が取り込まれる(コンテンツハッシュで変更検知)。**実行前に webapp を
停止する** こと(H2 組込みモードのため)。

```bash
# 1) webapp を停止 (Ctrl+C)
# 2) 再 ingest
java -jar searchable-cli/target/searchable-cli-1.0.0-SNAPSHOT.jar \
  --config ./searchable.yaml \
  ingest --namespace default --source-type file ~/sample-docs
# 3) webapp 再起動
java -jar examples/webapp/target/webapp-example-1.0.0-SNAPSHOT.jar
```

> CLI と webapp を同時稼働させたい場合は永続化を PostgreSQL か H2 サーバー
> モード(TCP)に切り替える。詳細は [setup-guide.md](setup-guide.md)。

---

## 次に読むもの

- 設定項目と運用手順の全体像: [setup-guide.md](setup-guide.md)
- CLI の全サブコマンド: [cli-guide.ja.md](cli-guide.ja.md)
- 自分のアプリに組み込んで使う: [usage.ja.md](usage.ja.md)
- REST API の全エンドポイント: [examples/api/README.md](../examples/api/README.md) / [api-specification.ja.md](../examples/api/api-specification.ja.md)
- 設計思想と内部構造: [architecture.md](architecture.md)

## 困ったときは

| 症状 | 対処 |
| --- | --- |
| ポート 8080 が使用中 | `--server.port=8081` を起動引数に追加 |
| ビルド失敗 | `java -version` が 21 以上か確認、`~/.m2` の `searchable-core` インストールを再実行 |
| webapp/API 起動時の H2 ロックエラー | 同じ DB を握る別プロセス(CLI / もう一方のサーバー)を停止してから再起動 |
| 検索結果が 0 件 | ケース A は登録 curl のレスポンス、ケース B は `status` でドキュメント数を確認 |
| 文字化け | ターミナルと application.properties の文字コード(UTF-8)を確認 |

それ以外は [setup-guide.md](setup-guide.md) のトラブルシューティング節を参照。

---

**Document Version**: 2.1
**Last Updated**: 2026-06-01
**Status**: M1 完了版(基盤・CLI・API/webapp サンプル)対応
