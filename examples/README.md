# Searchable サンプルアプリケーション

Searchable の代表的な **3 つの利用パターン** を、それぞれサンプル
アプリケーションとして提供する。各サンプルは独立した Maven / 静的
プロジェクトで、本体のマルチモジュールビルドには含めない。

ライブラリ本体の要件は [docs/devel/requirements.md](../docs/devel/requirements.md)
を参照。

## 利用パターン一覧

### 1. 普通の Web アプリ(library 組み込み)

Searchable を **ライブラリとして組み込んだ単一プロセス Web アプリ**。
インデックス・検索・UI 表示すべてを 1 つの JVM 内で完結させる。

- 想定ユースケース: 社内ドキュメント検索ポータル、組み込み検索
- 外部 API サーバー不要、デプロイ単位が 1 つ
- バックエンド = フロントエンドが同居するため、書込と検索を同一プロセスで処理
- サンプル: [`examples/webapp/`](webapp/) (Spring Boot + Thymeleaf)
- 要件: [webapp/requirements.ja.md](webapp/requirements.ja.md)

```text
┌─────────────────────────────┐
│  Web アプリ (Thymeleaf)      │
│  ├─ 検索 UI                  │
│  └─ Searchable (embedded)    │
└──────────────┬──────────────┘
               ▼
       インデックス (FS / DB)
```

### 2. API 経由(REST API サーバー + フロントエンド)

検索バックエンドを **REST API サーバー** として独立させ、複数の
クライアント(Web、モバイル、他システム)から呼び出す構成。

- 想定ユースケース: マイクロサービスとしての検索基盤、多クライアント対応
- 言語非依存(REST + JSON)
- 認証は API Key(オプション)
- サンプル: [`examples/api/`](api/) (REST API サーバー、Spring Boot) +
  [`examples/search-ui/`](search-ui/) (HTML+JS クライアント)
- 要件: [api/requirements.ja.md](api/requirements.ja.md) /
  [search-ui/requirements.ja.md](search-ui/requirements.ja.md)

```text
┌──────────────────┐  HTTP   ┌──────────────────┐
│  search-ui       │ ─────▶ │  api (REST)      │
│  (HTML + JS)     │         │  + Searchable     │
└──────────────────┘         └────────┬─────────┘
                                      ▼
                             インデックス (FS / DB)
```

### 3. MCP 経由(AI クライアント連携)

AI クライアント (Claude Desktop 等) から Searchable の検索機能を
ツールとして呼び出す構成。

- 想定ユースケース: AI 回答の根拠提示、RAG 構築
- プロトコル: MCP (Model Context Protocol)
- 動作モード: stdio / SSE
- サンプル: [`examples/mcp/`](mcp/)
- 要件: [mcp/requirements.ja.md](mcp/requirements.ja.md)

```text
┌──────────────────┐  MCP    ┌──────────────────┐
│ Claude Desktop   │ ─────▶ │  mcp サーバー      │
│ (AI クライアント)  │         │  + Searchable     │
└──────────────────┘         └────────┬─────────┘
                                      ▼
                             インデックス (FS / DB)
```

## サンプル一覧

### アプリケーションのリファレンス

| サンプル | パターン | ビルド方法 |
| --- | --- | --- |
| [webapp](webapp/) | Web アプリ(library 組み込み) | `mvn -f examples/webapp/pom.xml package` |
| [api](api/) | API 経由(サーバー) | `mvn -f examples/api/pom.xml package` |
| [search-ui](search-ui/) | API 経由(クライアント) | ビルド不要(静的ファイル) |
| [mcp](mcp/) | MCP 経由 | `mvn -f examples/mcp/pom.xml package` |

### プラグインのリファレンス実装

`DataSourcePlugin` SPI 等の拡張ポイントを実装する際の参考実装。
本リポジトリではあくまでリファレンスであり、本番運用は別リポジトリ等での
ハードニングを推奨。

| サンプル | 概要 | ビルド方法 |
| --- | --- | --- |
| [plugin-datasource-s3](plugin-datasource-s3/) | S3 互換ストレージから取込む `DataSourcePlugin` 実装 | `mvn -f examples/plugin-datasource-s3/pom.xml package` |

### AI プロバイダ設定のリファレンス

`searchable-admin` で `AiProvider` SPI を有効化する際の設定例。コード
ではなく、設定ファイル + 起動手順のセットとして提供する。

| サンプル | 概要 | 形式 |
| --- | --- | --- |
| [ai-ollama](ai-ollama/) | ローカル Ollama サーバーで AI 要約を有効化する設定例(専用 `OllamaProvider` 利用、OpenAI 互換ルートも併記) | 設定ファイル + docker-compose |

各サンプルをビルドする前に、Searchable 本体をローカル Maven リポジトリに
インストールしておくこと。

```bash
mvn -B clean install -DskipTests
```

## サンプルを試す（インデックス登録 → 検索の最短ルート）

各サンプルの個別 README に **Quick start** セクションを設けており、
そこにアプリの起動・インデックス登録・検索までを 1 本のシナリオで
記載している。本節では全サンプル共通の前提と選択肢のみを示す。

### 文書 metadata の予約キー(共通)

すべての取込経路で `Document.metadata` に次のキーが設定されることが
期待される(詳細は [docs/devel/design/architecture/overview.md §5.7](../docs/devel/design/architecture/overview.md)):

| キー | 値 | 用途 |
| --- | --- | --- |
| `url` | RFC 3986 形式の URI（`file:///` / `http(s)://` / `s3://` 等、スキーム必須） | 検索結果から元文書へのリンク、`SubResult.anchorUrl` の基点 |
| `contentType` | MIME タイプ（`text/plain` / `text/markdown` / `text/html` / `application/pdf` 等） | UI のレンダリング切替、RAG 連携時の形式情報 |
| `category` / `lang` / `tags` | string / string array | ファセット集計 |

`searchable-cli` の `ingest`、`examples/webapp` の起動時取込、
`examples/plugin-datasource-s3` の S3 取込はいずれも上記キーを自動で
設定する。`examples/api` の REST 取込はクライアント側で設定する
(`api-specification.ja.md` 参照)。

### 共通の前準備

1. ライブラリ本体を install（`searchable-core` などのコア JAR をローカル
   `~/.m2` に配置）。

   ```bash
   mvn -B clean install -DskipTests
   ```

2. `searchable-cli`（後述）を使う場合はビルドしておく。

   ```bash
   mvn -pl searchable-cli -am clean package
   ```

### インデックス登録の 2 つの選択肢

各サンプルアプリには **アプリ経由で投入する経路** と
**`searchable-cli` (searchable-cli) で投入する経路** の 2 通りが用意してある。
どちらを選んでも、最終的に同じ Lucene インデックス（`data-directory`
配下）に書き込まれる。

| 経路 | 仕組み | こんなときに |
| --- | --- | --- |
| **アプリ経由** | 各サンプル固有の書き込み IF（REST API、起動時バッチ ingest など）から登録 | アプリと同じプロセスで完結させたい / 認証や検証付きで投入したい |
| **searchable-cli 経由** | `searchable-cli` の `ingest` サブコマンドで、アプリと同じ `data-directory` を指す `searchable.yaml` を使って投入 | 大量データを一括投入したい / アプリを起動せずに事前構築したい / 読み取り専用のサンプル (`mcp`, `search-ui`) でインデックスを用意したい |

searchable-cli 経路を採るときの肝は **アプリと CLI で同じ `data-directory` を指す
設定にする** こと。`searchable.yaml` が指すのは
**アプリの index 格納場所**（webapp なら `./data/webapp/indexes`）であり、
**ソースとなるドキュメントの場所** とは別物。ソースは
`~/Documents/handbook` や `/var/data/manuals` など、アプリのデータ領域とは
無関係な場所にあることがほとんど。

たとえば次の `searchable.yaml` を用意すれば、`examples/webapp` の
データディレクトリを CLI からも触れる。

```yaml
# searchable.yaml — アプリのデータ領域（Lucene index + metadata DB の保管先）を表す設定。
# 以下のパスはすべて「アプリが自分用に確保するストレージ」を指しており、
# ingest 対象のソースドキュメントの場所ではない。
# ソースは下の ingest コマンドに引数として渡す（~/Documents/handbook など、
# このアプリのデータ領域とは無関係な場所が一般的）。

# アプリが永続化するもの全部の親ディレクトリ。
# アプリ側の searchable.data-directory と必ず一致させる
# （同じ index を両方のプロセスが見るため）。
data-directory: ./data/webapp

# メタデータ DB（namespace 一覧、ドキュメント ↔ index ポインタ等）。
# H2 ファイル ./data/webapp/metadata.mv.db に保存される。
persistence:
  type: H2
  url: "jdbc:h2:./data/webapp/metadata;MODE=PostgreSQL"
  username: sa
  password: ""

# Lucene の index ファイルの書き込み先。
# 慣例的に data-directory 配下に置く。
index:
  directory: ./data/webapp/indexes
```

```bash
# ingest の最後の引数 = ソースディレクトリ。data-directory とは独立で、
# 普段ドキュメントが置いてある場所をそのまま指定する。
./searchable-cli/src/main/scripts/searchable \
  --config ./searchable.yaml \
  ingest --namespace default --source-type file \
  ~/Documents/handbook
```

### サンプル別の特性

| サンプル | アプリ経由の書き込み | searchable-cli 経路の必要性 |
| --- | --- | --- |
| [`api`](api/) | `POST /api/v1/index/documents` 等の REST | 任意（大量データなら推奨） |
| [`webapp`](webapp/) | `searchable.ingest.enabled=true` での起動時バッチ ingest | 任意（任意のタイミングで再ingest したい場合に有効） |
| [`mcp`](mcp/) | **なし**（MCP サーバーは読み取り専用） | **必須**（事前に searchable-cli か `api` でインデックスを構築） |
| [`search-ui`](search-ui/) | **なし**（静的クライアント） | アプリ経由・searchable-cli いずれも可（`api` 側に投入） |
| [`plugin-datasource-s3`](plugin-datasource-s3/) | プラグインを組み込んだホスト側で起動時 ingest | searchable-cli にプラグインを乗せる構成も可（[verify.md](plugin-datasource-s3/verify.md) 参照） |

詳しいコマンド例とサンプルデータ投入方法は各サンプルの README の
`Quick start` 節を参照する。

## サンプルの位置付け

- いずれも **リファレンス実装**。プロダクション用途では認証・監査・運用要件を
  追加実装することを推奨
- ただし `examples/api` と `examples/mcp` は **API Key 認証** をサポートし、
  軽度の本番利用に耐える品質を目指す
- パッケージは `io.searchable.example.*` 系列、Maven groupId は
  `io.searchable.example`
