# MCP サーバー

Searchable は Model Context Protocol (MCP) サーバーとしても利用でき、
Claude Desktop などの AI クライアントからツール経由で検索を呼び出せる。

## ツール

- `search_documents`: query / namespace_ids / search_type / max_results を
  受け取り、ヒットを整形して返す。

## 起動

```bash
java -jar searchable-mcp.jar --config /path/to/searchable.yaml
```

Claude Desktop の `mcpServers` 設定で上記コマンドを指定する。
