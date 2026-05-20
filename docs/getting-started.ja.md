# Searchable - Getting Started

Searchable を初めて触る人向けの最短経路ガイド。
ビルドから検索 API の動作確認までを 5〜10 分で完了することを目標にする。

詳細な設定や運用手順は [setup-guide.md](setup-guide.md) を、
API の全体仕様は [usage.ja.md](usage.ja.md) を参照する。

## 1. 前提環境

| 項目 | バージョン |
| --- | --- |
| Java | 21 以上 |
| Maven | 3.9 以上 (同梱の `./mvnw` を使う場合は不要) |
| メモリ | 1GB 以上の空き |
| OS | macOS / Linux / Windows |

`java -version` と `mvn -v` (または `./mvnw -v`) が成功すれば準備完了。

## 2. ソースを取得する

```bash
git clone <repository-url>
cd searchable
```

## 3. ビルドする

ライブラリ本体（コアモジュール）をローカル Maven リポジトリにインストールする。

```bash
./mvnw -B clean install -DskipTests
```

これで `searchable-core` などコア JAR がローカル `~/.m2` に配置され、
`examples/` 配下のサンプルアプリから依存解決できるようになる。

> Maven がインストール済みであれば `mvn -B clean install -DskipTests` でも可。

続いて REST API サンプル（Spring Boot）をビルドする。

`examples/` 配下のサンプルアプリはルート POM のリアクターには含まれない
**独立した Maven プロジェクト**として構成しており、`~/.m2` に
インストール済みの `searchable-core` 等を依存として参照する。
このためルートからは `-f` でサブプロジェクトの POM を指定してビルドする。

```bash
./mvnw -B -f examples/api/pom.xml package
```

> `examples/api` ディレクトリに移動してから `../../mvnw -B package` を
> 実行しても結果は同じ。

成果物:

- `examples/api/target/api-example-1.0.0-SNAPSHOT.jar` （Spring Boot fat jar）

## 4. REST API サーバーを起動する

```bash
java -jar examples/api/target/api-example-1.0.0-SNAPSHOT.jar
```

ログに `Started SearchableApplication` が出ればポート `8080` で待ち受けている。

別ターミナルで疎通確認:

```bash
curl http://localhost:8080/api/v1/namespaces
# => {"namespaces":[],"total":0}
```

## 5. 最初の検索を実行する

Namespace を作成 → ドキュメントを 1 件登録 → 検索、の最短コース。

### 5.1 Namespace を作る

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "quickstart",
    "name": "Quickstart",
    "config": {"architecture": "FULL_TEXT"}
  }'
```

### 5.2 ドキュメントを登録する

```bash
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "quickstart",
    "document": {
      "id": "doc-1",
      "title": "Searchable について",
      "content": "Searchable は日本語形態素解析に対応した全文検索ライブラリです。"
    }
  }'
```

### 5.3 検索する

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "形態素解析",
    "namespaceIds": ["quickstart"]
  }'
```

`hits` 配列に `doc-1` が含まれていれば成功。

## 6. Java ライブラリとして使う場合

Maven プロジェクトに以下を追加する。

```xml
<dependency>
  <groupId>com.searchable</groupId>
  <artifactId>searchable-core</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

最小コード例:

```java
SearchableLibrary library = SearchableLibrary.builder()
    .dataDirectory("./data")
    .build();
library.start();

SearchService searchService = library.getSearchService();
SearchResult result = searchService.search(
    SearchRequest.builder()
        .query("形態素解析")
        .namespaceIds(List.of("quickstart"))
        .build());
```

組み込み利用の詳細は [usage.ja.md](usage.ja.md) の Java API 節を参照。

## 7. テストを実行する

```bash
./mvnw -B test
```

モジュール単位で実行する場合:

```bash
./mvnw -B -pl searchable-core -am test
```

`examples/api` は独立した Maven プロジェクトのため、`-pl` ではなく
`-f` でサブプロジェクトの POM を指定する（`searchable-core` を
事前に `install` しておく必要がある）。

```bash
./mvnw -B -f examples/api/pom.xml test
```

## 8. 次に読むもの

- 設定項目・運用手順の全体像: [setup-guide.md](setup-guide.md)
- Java / REST / MCP の使い方リファレンス: [usage.ja.md](usage.ja.md)
- 設計思想と内部構造: [architecture.md](architecture.md)
- AI クライアント (Claude Desktop など) からの利用: [examples/mcp/guide.ja.md](../examples/mcp/guide.ja.md)

## 9. 困ったときは

- ポート `8080` が使われている → `--server.port=8081` で起動
- ビルドが失敗する → `java -version` が 21 以上であることを確認
- それ以外 → [setup-guide.md](setup-guide.md) のトラブルシューティング節を参照

---

**Document Version**: 1.2
**Last Updated**: 2026-05-20
**Status**: Phases 1–5 complete（モジュール再構成中）
