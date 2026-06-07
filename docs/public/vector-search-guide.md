# Searchable ベクトル検索利用ガイド

Searchable のベクトル検索 / ハイブリッド検索の有効化手順と
チューニングポイント。

## 1. 仕組み

- 文書登録時、`EmbeddingProvider` がドキュメントから埋め込みベクトルを
  生成し、Lucene HNSW インデックス（`KnnFloatVectorField`、
  類似度: `DOT_PRODUCT`）に保存
- 検索時、クエリ文字列を同じ EmbeddingProvider でベクトル化して
  k-NN 検索を実行
- ハイブリッド検索では、全文検索（BM25）とベクトル検索を組み合わせる

## 2. 埋め込みプロバイダ

| プロバイダ | 用途 | 設定 |
| --- | --- | --- |
| `hash`（デフォルト） | 開発・テスト用、決定的だが意味的検索は不可 | `searchable.embedding.dimension` |
| `onnx`（拡張） | 実運用、ONNX形式の埋め込みモデル | `searchable.embedding.model-path` + Tokenizer Bean |

### Hashプロバイダ（デフォルト）

```properties
searchable.embedding.provider=hash
searchable.embedding.dimension=384
```

- 384次元のSHA-256ベース決定的ベクトル
- 意味的検索は機能しない（同一テキストには同一ベクトル、それ以外は擬似ランダム）
- ベクトル経路の挙動確認やパフォーマンステストに有用

### Onnxプロバイダ（実モデル利用）

ONNX形式の埋め込みモデルを使う場合、Spring Configuration で
`EmbeddingProvider` Bean を上書きする。

```java
@Configuration
public class MyEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingProvider onnxEmbedding(final SearchableProperties props) {
        final SearchableProperties.Embedding e = props.getEmbedding();
        final Tokenizer tokenizer = new MyTokenizer(e.getModelPath());  // 別途実装
        return new OnnxEmbeddingProvider(
            e.getModelId(),
            e.getModelPath(),
            tokenizer,
            e.getDimension(),
            e.getMaxSequenceLength()
        );
    }
}
```

`application.properties`:

```properties
searchable.embedding.provider=onnx
searchable.embedding.model-id=multilingual-e5-small
searchable.embedding.model-path=/path/to/multilingual-e5-small.onnx
searchable.embedding.dimension=384
searchable.embedding.max-sequence-length=512
```

**注意**: Tokenizer は別途実装が必要（SentencePiece等のモデル固有
トークナイザ）。`com.searchable.core.domain.embedding.Tokenizer`
インタフェースを実装し、`encode(text, maxLength)` で
`{inputIds, attentionMask}` を返す。

## 3. Namespace 設定

Namespace の `architecture` を設定することでデフォルト挙動を変更:

| 値 | 動作 |
| --- | --- |
| `FULL_TEXT` | 全文検索のみ |
| `VECTOR` | ベクトル検索のみ |
| `HYBRID` | 全文 + ベクトル検索の組み合わせ |

### Namespace作成例

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "knowledge-base",
    "name": "Knowledge Base",
    "config": {
      "architecture": "HYBRID",
      "searchStrategy": "PARALLEL",
      "searchOrder": "FULL_TEXT_FIRST"
    }
  }'
```

### `searchStrategy`（ハイブリッド時の戦略）

- `SEQUENTIAL`: primary engine の結果を secondary engine が rerank
  （実質的に交差）。`searchOrder` で順序を決定
  - `FULL_TEXT_FIRST`: 全文検索が primary
  - `VECTOR_FIRST`: ベクトル検索が primary
- `PARALLEL`: 両エンジンを並列実行 → Reciprocal Rank Fusion (RRF) で
  union ランキング

## 4. 検索API

### ベクトル検索

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "意味的に似た文書を探す",
    "namespaceIds": ["knowledge-base"],
    "searchType": "VECTOR",
    "options": { "maxResults": 10 }
  }'
```

### ハイブリッド検索

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "全文検索 と 意味検索 の組み合わせ",
    "namespaceIds": ["knowledge-base"],
    "searchType": "HYBRID"
  }'
```

### 型省略時の挙動

`searchType` を省略した場合、対象 Namespace の `architecture`
設定が使われる。

### セクション anchor (`SubResult`) の対象範囲

`SubResult` および `SubResult.anchorUrl` は **full-text 検索でのみ生成**
される。ベクトル検索(`VECTOR`)単体の結果には `subResults` が含まれない
(`SubResult` のセクション分割はベクトル空間では意味付けが薄いため、明示的に
全文検索限定としている)。ハイブリッド検索(`HYBRID`)の結果は内部で
full-text とベクトルをマージするため、full-text 経由で見つかった文書には
`subResults` が付き、ベクトル経由で見つかった文書には付かない。詳細は
[docs/devel/design/architecture/overview.md §5.7](architecture.md) 参照。

## 5. インデックス構築

ベクトル付きインデックスは通常通り `/api/v1/index/documents` または
`/api/v1/index/batch` で構築できる。`EmbeddingProvider` が有効な
場合、登録時に自動でベクトルが計算・保存される。

```bash
curl -X POST http://localhost:8080/api/v1/index/batch \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "knowledge-base",
    "documents": [
      {"id": "d1", "title": "...", "content": "..."},
      {"id": "d2", "title": "...", "content": "..."}
    ]
  }'
```

## 6. 性能特性

| 件数 | 検索 p99 | インデックス構築 |
| --- | --- | --- |
| 10万 | 1 ms（ベクトル単体） | 約88秒（HNSW構築含む） |
| 10万 | 1 ms（全文単体） | 約6秒 |

詳細は以下を参照:

- `docs/devel/work/investigations/003-performance.md`（全文検索）
- `docs/devel/work/investigations/123-vector-performance.md`（ベクトル検索）

## 7. チューニング

- **次元数**: 大きいほど精度向上だがインデックス容量・検索時間が増加。
  multilingual-e5-small=384, multilingual-e5-base=768 が代表値
- **HNSW パラメータ**: Lucene のデフォルト設定で十分。必要に応じて
  `LuceneIndexProvider` を拡張して `IndexWriterConfig` をカスタマイズ
- **バッチサイズ**: インデックス構築時は `RAMBufferSizeMB` を大きくする
  （デフォルト64MB → 256MB等）と一括投入が高速化
- **並列ハイブリッド**: Virtual Thread executor を使用するため
  CPU・I/O のオーバーヘッドは少ないが、両エンジンの最大レイテンシが
  応答時間になることに注意

## 8. トラブルシューティング

### 「ベクトル検索の結果が常に同じ」

- Hash プロバイダ使用時は意味的検索は機能しない。Onnx プロバイダ + 実モデル
  に切り替える必要あり

### 「ベクトル検索でドキュメントが0件」

- 該当 Namespace の文書登録時に `EmbeddingProvider` が有効だったか確認
- ベクトル無しで登録された文書は KNN クエリに引っかからない。
  `/api/v1/index/rebuild` で再構築

### 「ベクトル次元数の不一致エラー」

- 同一 Namespace 内で異なる次元のベクトルは混在不可
- 次元を変更する場合は Namespace を削除して再構築

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 2
