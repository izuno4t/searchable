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

### ONNX モデルの配布・取得・キャッシュ戦略

Searchable は ONNX モデルファイルを JAR に同梱しない。
`OnnxEmbeddingProvider` は `Path modelPath` を受け取るだけで、
ダウンロードもキャッシュも行わないため、利用者がローカルに
配置する必要がある。

**推奨モデル**:

| モデル | 次元 | 約サイズ (ONNX量子化前) | 取得元 |
| --- | --- | --- | --- |
| `intfloat/multilingual-e5-small` | 384 | 約140MB | <https://huggingface.co/intfloat/multilingual-e5-small> |
| `intfloat/multilingual-e5-base` | 768 | 約470MB | <https://huggingface.co/intfloat/multilingual-e5-base> |

**取得方法** (Hugging Face からの典型例):

```bash
# huggingface-cli (推奨, git lfs 不要)
pip install -U "huggingface_hub[cli]"
huggingface-cli download intfloat/multilingual-e5-small \
  --include "*.onnx" "tokenizer.json" "sentencepiece.bpe.model" \
  --local-dir ~/.cache/searchable/models/multilingual-e5-small
```

`git lfs` を使う場合:

```bash
git lfs install
git clone https://huggingface.co/intfloat/multilingual-e5-small \
  ~/.cache/searchable/models/multilingual-e5-small
```

**配置パスの規約**: ライブラリは特定のパスを強制しない。
チーム内で統一する場合は `~/.cache/searchable/models/<model-id>/`
を出発点として、`application.properties` の
`searchable.embedding.model-path` を絶対パスで指定する。

**embedded (Java API) と examples の両経路**:

- Java API で直接利用する場合は `SearchableLibrary.Builder.embeddingProvider(...)`
  でアプリ側が解決済みの `OnnxEmbeddingProvider` を渡す。
- examples (REST API / webapp / MCP) では `application.properties` に
  `searchable.embedding.model-path=/absolute/path/to/model.onnx` を設定。
  起動時にファイル不在ならアプリは起動失敗 (`IllegalStateException`)。
- CI / Docker イメージにモデルファイルを含めるかは利用者判断。
  巨大なバイナリを CI artifact に含めるのが許容できる場合のみ。
  そうでない場合は `init container` / `entrypoint script` で取得する。

**ライセンス確認**: multilingual-e5 系は MIT、それ以外のモデルを
使う場合はそれぞれのライセンスを必ず確認すること。

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

> **計測単位の注意**: 上記 p99 は PoC コードが ms 単位で `long` 丸めしているため
> "1 ms 未満" を意味する。サブミリ秒の精度は JMH ベースのベンチに置き換える
> TASK-008 完了後に再計測予定 (参照: `docs/devel/work/tasks/task.md` TASK-007 結果)。

詳細は以下を参照:

- `docs/devel/work/investigations/003-performance.md`（全文検索）
- `docs/devel/work/investigations/123-vector-performance.md`（ベクトル検索）

### ベクトル初期インデックス構築 88s/100k の再現条件

上記の "約88秒" は
`docs/devel/work/poc/task-123-vector-perf/src/main/java/poc/VectorSearchPerformanceTest.java`
で計測した値。再現には以下を揃える必要がある:

| 項目 | 値 |
| --- | --- |
| ドキュメント数 | 100,000 |
| ベクトル次元 | 384 |
| 類似度関数 | `VectorSimilarityFunction.DOT_PRODUCT` |
| Storage | `MMapDirectory` (一時ディレクトリ) |
| 並列度 | シングルスレッド (`for` ループによるシリアル `addDocument`) |
| `IndexWriterConfig.RAMBufferSizeMB` | 512 |
| HNSW パラメーター | Lucene デフォルト (M=16, efConstruction=100) |
| Analyzer | `JapaneseAnalyzer` (Kuromoji) |
| 埋め込み | SHA-256 ベースのハッシュ (384次元、L2 正規化) — **実モデル非依存** |
| ハードウェア | macOS / Apple Silicon (PoC README 記載) |
| Java | 21 |
| Lucene | 10.4 (旧 PoC は 10.2; 数値の桁感は同等) |
| 出力 | `[index] %,d docs (dim=%d) indexed in %,d ms` の3番目 |

**実モデルを使った場合の倍率**: ハッシュ埋め込みは ONNX 推論より桁違いに速い
ため、`OnnxEmbeddingProvider` + `multilingual-e5-small` を使うと
"埋め込み生成時間 + HNSW グラフ構築時間" となり、Apple Silicon でも
10万件で 20〜40 分程度に伸びるのが一般的 (CPU バウンド)。
プロダクションでは:

- バッチ並列度: `parallelStream()` や `ExecutorService` で並列化
- バッチサイズ: 1,000〜10,000 件ずつ `addDocument` → 周期的に `commit`
- `RAMBufferSizeMB`: 256〜1024 MB を JVM heap に合わせて設定

を組み合わせて取得時間を縮める。実装はライブラリ側で固定せず、
`SearchableLibrary.Builder.embeddingProvider(...)` に渡す前段で
利用者が制御する。

## 7. チューニング

- **次元数**: 大きいほど精度向上だがインデックス容量・検索時間が増加。
  multilingual-e5-small=384, multilingual-e5-base=768 が代表値
- **HNSW パラメータ**: Lucene のデフォルト設定で十分。必要に応じて
  `LuceneIndexProvider` を拡張して `IndexWriterConfig` をカスタマイズ
  (詳細は次節)
- **バッチサイズ**: インデックス構築時は `RAMBufferSizeMB` を大きくする
  （デフォルト64MB → 256MB等）と一括投入が高速化
- **並列ハイブリッド**: Virtual Thread executor を使用するため
  CPU・I/O のオーバーヘッドは少ないが、両エンジンの最大レイテンシが
  応答時間になることに注意

### HNSW パラメーターチューニング指針

Lucene の HNSW は `Lucene99HnswVectorsFormat` で実装されており、
構築時の `M` / `efConstruction` と検索時の `topK` が
レイテンシ・リコール・インデックスサイズを支配する。

| パラメーター | Lucene デフォルト | 動作 | 上げると | 下げると |
| --- | --- | --- | --- | --- |
| `M` | 16 | 各ノードの最大接続数 | リコール向上、インデックス容量増、構築時間増 | インデックス縮小、リコール低下 |
| `efConstruction` | 100 | 構築時の候補探索幅 | リコール向上、構築時間増 | 構築高速化、品質低下 |
| `k` (検索 `topK`) | API 指定 | 検索時の候補数 | リコール向上、検索レイテンシ増 | 高速化、リコール低下 |

> Lucene 10 では検索時の `ef` を独立に指定する API は無く、`topK` が
> そのまま探索幅の起点となる。リコール重視時は `topK` を実必要件数より
> 大きめに取り、アプリ側で post-rerank する。

**カスタマイズ方法** (`LuceneIndexProvider` のサブクラスで
`IndexWriterConfig` を作る箇所を差し替える):

```java
final var hnswFormat = new Lucene99HnswVectorsFormat(
    32,    // M (default 16) — リコール優先で 24〜32
    200    // efConstruction (default 100) — 構築時に余裕があれば 200〜400
);
final var codec = new Lucene101Codec() {
    @Override
    public KnnVectorsFormat getKnnVectorsFormatForField(final String field) {
        if ("vector".equals(field)) {
            return hnswFormat;
        }
        return super.getKnnVectorsFormatForField(field);
    }
};
indexWriterConfig.setCodec(codec);
```

**チューニング指針** (10万〜100万件規模):

- まず Lucene デフォルト (M=16, efConstruction=100) で測定
- リコール不足を感じたら **M=24** から試す。M=32 以上は
  インデックスサイズが約 1.5〜2倍に膨らむため、運用容量と相談
- 構築時間に余裕があれば **efConstruction=200〜400** で
  リコールを底上げ。100→400 で構築時間は約 3〜4倍
- 検索レイテンシを切り詰めたいときは API 側の `topK` を下げ、
  アプリ側のフィルタリングで補う

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

**Document Version**: 1.1
**Last Updated**: 2026-06-07
**Status**: Phase 2 (TASK-016 / TASK-017 / TASK-018 反映)
