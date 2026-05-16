# Searchable サンプルアプリケーション

Searchable の代表的な **3 つの利用パターン** を、それぞれサンプル
アプリケーションとして提供する。各サンプルは独立した Maven / 静的
プロジェクトで、本体のマルチモジュールビルドには含めない。

ライブラリ本体の要件は [docs/requirements.md](../docs/requirements.md)
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

| サンプル | パターン | ビルド方法 |
| --- | --- | --- |
| [webapp](webapp/) | Web アプリ(library 組み込み) | `mvn -f examples/webapp/pom.xml package` |
| [api](api/) | API 経由(サーバー) | `mvn -f examples/api/pom.xml package` |
| [search-ui](search-ui/) | API 経由(クライアント) | ビルド不要(静的ファイル) |
| [mcp](mcp/) | MCP 経由 | `mvn -f examples/mcp/pom.xml package` |
| [filesystem-plugin](filesystem-plugin/) | データソースプラグイン例 | `mvn -f examples/filesystem-plugin/pom.xml package` |

各サンプルをビルドする前に、Searchable 本体をローカル Maven リポジトリに
インストールしておくこと。

```bash
mvn -B clean install -DskipTests
```

## サンプルの位置付け

- いずれも **リファレンス実装**。プロダクション用途では認証・監査・運用要件を
  追加実装することを推奨
- ただし `examples/api` と `examples/mcp` は **API Key 認証** をサポートし、
  軽度の本番利用に耐える品質を目指す
- パッケージは `io.searchable.example.*` 系列、Maven groupId は
  `io.searchable.example`
