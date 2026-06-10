# Searchable デモ環境セットアップ手順

Docker Compose を用いた最小デモ環境の構築方法です。

## 1. 前提

- Docker 24 以上
- Docker Compose v2
- 4GB 以上のメモリ

## 2. 構成

```text
┌────────────────────────────────────────────┐
│ searchable (REST + UI on port 8080)        │
│   Java 21 / Spring Boot 3 / Lucene 10      │
│   /data 永続化ボリューム                   │
└─────────────────┬──────────────────────────┘
                  │
            ┌─────┴────────┐
            │ Browser /    │
            │ curl / MCP   │
            └──────────────┘
```

## 3. 起動

```bash
git clone <repo>
cd searchable
docker compose up --build
```

初回はビルドに数分かかります。`Started SearchableUiApplication` が表示
されれば準備完了です。

## 4. デモデータ投入

別ターミナルで次を実行します。

```bash
./docker/seed.sh http://localhost:8080
```

`docker/demo-data/` 配下の Markdown 4 ファイルが `demo` Namespace に
登録されます。

## 5. 動作確認

ブラウザで以下を開きます。

| URL | 内容 |
| --- | --- |
| <http://localhost:8080/> | Dashboard（メトリクス + グラフ） |
| <http://localhost:8080/namespaces> | Namespace 管理 |
| <http://localhost:8080/indexes/demo> | demo Namespace の詳細 |
| <http://localhost:8080/documents/upload> | ファイルアップロード |
| <http://localhost:8080/settings> | グローバル設定 |

REST 経由で検索する例です。

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "ベクトル検索",
    "namespaceIds": ["demo"],
    "searchType": "HYBRID"
  }'
```

## 6. 環境変数による設定

| 変数 | デフォルト | 説明 |
| --- | --- | --- |
| `SEARCHABLE_DATA_DIRECTORY` | `/data` | データ root |
| `SEARCHABLE_PERSISTENCE_URL` | `jdbc:h2:/data/metadata;MODE=PostgreSQL` | H2 接続先 |
| `SEARCHABLE_INDEX_DIRECTORY` | `/data/indexes` | Lucene index 配置先 |
| `SEARCHABLE_GLOBAL_DEFAULT_ARCHITECTURE` | `HYBRID` | 既定検索アーキテクチャー |

任意の Spring Boot プロパティを `SEARCHABLE_XXX_YYY` 形式の環境変数
として渡すことで上書き可能です（kebab-case → SCREAMING_SNAKE_CASE）。

## 7. ボリューム

`searchable-data` 名のローカルボリュームに永続化されます。

```bash
docker volume ls
docker volume inspect searchable_searchable-data
```

リセットする場合は次のとおりです。

```bash
docker compose down -v
```

## 8. ログの確認

```bash
docker compose logs -f searchable
```

## 9. シャットダウン

```bash
docker compose down            # コンテナー停止（データ保持）
docker compose down -v         # データボリュームも削除
```

## 10. トラブルシューティング

### ビルドが遅い

- Maven 依存を一度ローカル（`~/.m2`）でビルドしてから docker
  build すると速くなります
- 初回は Lucene / Spring Boot 等で 300MB 程度ダウンロードします

### ポート競合

```bash
docker compose down
# docker-compose.yml の "8080:8080" を "8081:8080" 等に変更
```

### `seed.sh` がアップロードに失敗

- サーバー起動直後だと初期化中の場合があります。`seed.sh` はリトライ
  ループを持っていますが、それでも失敗する場合は再実行してください

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 3
