# アーキテクチャ概要

Searchable は層分離されたマルチモジュール構成です。

## モジュール

- `searchable-plugins`: プラグイン SPI
- `searchable-core`: ドメイン・アプリ・インフラ層（Lucene/H2/parsers）
- `searchable-api`: REST API（Spring Boot）
- `searchable-mcp`: MCP サーバー（stdio JSON-RPC）
- `searchable-ui`: 管理 UI（Thymeleaf）

## 検索エンジン

- 全文検索: Lucene + Kuromoji（BM25）
- ベクトル検索: Lucene HNSW + 任意の EmbeddingProvider
- ハイブリッド: 順次（交差）/ 並列（RRF）
