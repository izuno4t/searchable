# ベクトル検索の利用

意味的に類似した文書を検索する機能。

## 仕組み

1. EmbeddingProvider がテキストをベクトル化（既定: ハッシュ生成）
2. Lucene HNSW が k 近傍探索を実行
3. DOT_PRODUCT 類似度でランキング

## 設定

`application.properties`:

```properties
searchable.embedding.provider=hash
searchable.embedding.dimension=384
```

ONNX モデルを利用する場合は `OnnxEmbeddingProvider` Bean をオーバライド。

## ハイブリッド検索

`searchType=HYBRID` で全文 + ベクトルを組み合わせる。`searchStrategy`
により逐次（交差再ランク）または並列（RRF マージ）を選択できる。
