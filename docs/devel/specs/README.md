# docs/devel/specs/

ライブラリ本体が外部に提供する I/F と振る舞いの仕様 (満たすべき入出力・契約・業務ルール) を管理する。

設計方法ではなく「何を満たせば正しいか」を記述する。設計の "どう実現するか" は [`../design/`](../design/) を参照。

## 方針

- **Java API および SPI のクラス・メソッド・例外仕様は Javadoc を正本とする**。本ディレクトリには重複して書かない (二重管理の防止)。
- **本ディレクトリで扱うのは、Javadoc で表現しきれない外部仕様** — 設定ファイルスキーマ、CLI 契約、取込時のメタデータ規約、検索の振る舞いなど、利用者から見た契約。

Javadoc は「ローカル生成のみ」を採用 (TASK-040)。公開ホスティングは行わず、
ソース直接参照または以下でローカル生成して読む。

```bash
./mvnw -pl searchable-core,searchable-plugins,searchable-ai \
       -DskipTests javadoc:javadoc
# 出力: <module>/target/reports/apidocs/index.html
```

## 配置済み仕様

| ファイル | 対象 |
| --- | --- |
| [`cli-commands.md`](cli-commands.md) | `searchable-cli` 各サブコマンドの引数・終了コード・stdout/stderr 契約 |
| [`config-yaml.md`](config-yaml.md) | `searchable.yaml` の全フィールドスキーマ・必須/任意・デフォルト・ロード時例外 |
| [`document-metadata.md`](document-metadata.md) | 取込時の予約メタデータキー (url / contentType / category / lang / tags ほか) と各パーサの自動付与挙動 |
| [`search-behavior.md`](search-behavior.md) | クエリ構文 / 検索戦略 (full-text / vector / hybrid) / スコア融合 (RRF / intersect) / BM25 既定値 / ハイライト / ページネーション |

## examples/ との関係

`examples/` 配下の REST API サーバー・MCP サーバーなどは「リファレンス実装」であり、それらの仕様は examples/ 内で完結する。本 `specs/` には含めない。

- REST API:
  [`examples/api/api-specification.ja.md`](../../../examples/api/api-specification.ja.md),
  [`examples/api/openapi.yaml`](../../../examples/api/openapi.yaml)
- MCP server: [`examples/mcp/guide.ja.md`](../../../examples/mcp/guide.ja.md)

これらは「ライブラリ本体の仕様」ではなく「サンプルアプリ固有の仕様」として扱う。

## Java API / SPI の仕様参照先

| カテゴリ | 参照先 |
| --- | --- |
| Java API (`SearchableLibrary` ほか) | `searchable-core/src/main/java/` 配下の Javadoc |
| SPI `DataSourcePlugin` | `searchable-plugins/src/main/java/io/searchable/plugin/` の Javadoc |
| SPI `AiProvider` | `searchable-ai/src/main/java/io/searchable/ai/` の Javadoc |
| 実装サンプル (`DataSourcePlugin`) | [`examples/plugin-datasource-s3/`](../../../examples/plugin-datasource-s3/) |

Javadoc の不足や曖昧さを発見した場合は、本ディレクトリではなく該当ソースの Javadoc 自体を改善する。
