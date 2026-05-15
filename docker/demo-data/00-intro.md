# Searchable 入門

Searchable は Java 21 上で動作する、日本語に最適化された全文検索 +
ベクトル検索ライブラリです。

## 主要機能

- Apache Lucene による全文検索
- Kuromoji による日本語形態素解析
- HNSW によるベクトル類似度検索
- Namespace による論理分離
- REST API / MCP サーバー / 管理 UI の3形態で提供

## クイックスタート

1. Maven でビルド (`mvn -B clean package`)
2. `searchable-ui-*.jar` を起動
3. ブラウザで <http://localhost:8080> を開く
