# 検索仕様

Searchable が提供する検索 (全文検索・ベクトル検索・ハイブリッド検索) の振る舞いを正本として定義する。

クエリ構文・実行戦略・スコア融合・ハイライトの契約を明示する。

## 1. 検索リクエストの構造

検索リクエストは `SearchRequest`
(`searchable-core/src/main/java/io/searchable/core/domain/search/SearchRequest.java:15`)
で表現する不変オブジェクトとする。

| フィールド | 型 | 必須 | 既定値 | 意味 |
| --- | --- | --- | --- | --- |
| `query` | `String` | 必須 | なし | 検索クエリ文字列。`null` または空白のみは禁止 |
| `namespaceIds` | `List<String>` | 任意 | `List.of()` (= 全 Namespace) | 検索対象 Namespace ID の列。空の場合は全 Namespace を対象とする |
| `searchType` | `SearchType` | 任意 | `null` (= Namespace 設定に従う) | `FULL_TEXT` / `VECTOR` / `HYBRID` のいずれか |
| `options` | `SearchOptions` | 任意 | `SearchOptions.defaults()` | ハイライト・BM25 等のオプション (§4 参照) |
| `pagination` | `PaginationParams` | 任意 | `PaginationParams(0, 10)` | offset/limit (§5 参照) |
| `filters` | `Map<String, Object>` | 任意 | `Map.of()` | メタデータフィルタ (§7 参照) |

`query` が `null` のとき `NullPointerException`、空白のみのとき
`IllegalArgumentException` が
`SearchRequest` のコンストラクタで送出される
(`SearchRequest.java:25-28`)。

## 2. クエリ構文

### 2.1 全文検索のクエリ構文

全文検索のクエリは Lucene の `QueryParser` で解析する
(`searchable-core/src/main/java/io/searchable/core/infrastructure/lucene/LuceneFullTextSearcher.java:103-121`)。

ただし呼び出し前に `QueryParser.escape(request.query())` を必ず適用する
(`LuceneFullTextSearcher.java:105`)。
このため利用者が記述したクエリ文字列中の Lucene 演算子は **すべて** リテラル
として扱われ、`AND` / `OR` / `NOT` / `*` / `?` / `~` / `^` / `+` / `-` / `:`
/ `(` / `)` / `[` / `]` / `{` / `}` / `\` / `/` などの特殊記号で式を構成する
ことはできない。

- **演算子のエスケープ**: 利用者は意識する必要はない。すべてエスケープ済みの
  リテラルとして渡るため、たとえば `"a AND b"` は「`a AND b` という 3 トークン
  を含む」検索ではなく「`a`, `AND`, `b` の 3 語を含む」検索になる
- **既定演算子**: `OR` (`LuceneFullTextSearcher.java:108,119` で
  `QueryParser.Operator.OR` を設定)。複数語を空白区切りで指定した場合、
  どれか 1 語を含むドキュメントがヒットする
- **トークン化**: クエリ文字列は対象 Namespace の `Analyzer` を通して
  分かち書きされる (`LuceneFullTextSearcher.java:107,118`)。Phase 1 では
  Kuromoji の `JapaneseAnalyzer` が既定で、`searchable.analyzer` を
  `sudachi` に設定すると Sudachi に切り替わる
  (`searchable-core/src/main/java/io/searchable/core/infrastructure/lucene/AnalyzerFactory.java:18-28`,
  `AnalyzerType.java:14-25`)
- **検索対象フィールド**: 既定では `content` フィールドのみを対象とする
  (`LuceneFullTextSearcher.java:107` の `QueryParser(LuceneFields.CONTENT, analyzer)`)。
  `SearchOptions.metaWeights` が指定されたときは複数フィールドを対象とした
  `MultiFieldQueryParser` を構築し、フィールドごとの boost 値を適用する
  (`LuceneFullTextSearcher.java:111-120`)
- **ワイルドカード・範囲・近接などの高度演算**: エスケープ済みの結果として
  リテラル扱いになるため、Phase 1 ではサポートしない。利用者の語句がそのまま
  形態素解析されてマッチする「単純な全文検索」だけが保証された動作である

### 2.2 ベクトル検索の入力

ベクトル検索の入力は `SearchRequest.query` の文字列を、設定された
`EmbeddingProvider` で float ベクトルに変換した結果を使う
(`searchable-core/src/main/java/io/searchable/core/infrastructure/lucene/LuceneVectorSearcher.java:54`)。

- **クエリ文字列の埋め込み生成**: `EmbeddingProvider.embed(String)`
  (`searchable-core/src/main/java/io/searchable/core/domain/embedding/EmbeddingProvider.java:20`)
  でクエリ文字列を 1 度だけベクトル化する
- **事前計算ベクトルの直接渡し**: 現行 API では未サポート。クエリは必ず文字列で
  渡し、内部で `EmbeddingProvider` を経由する
- **ベクトル次元の制約**: 同一 Namespace の登録時と検索時で `EmbeddingProvider`
  の `dimension()` が一致している必要がある。次元が混在した場合は Lucene
  HNSW の制約により検索時に例外となる
- **正規化**: コサイン類似度 / 内積類似度を意図する場合、`EmbeddingProvider`
  実装は L2 正規化済みベクトルを返すことが想定されている
  (`EmbeddingProvider.java:14-15` の Javadoc)
- **検索クエリ**: `KnnFloatVectorQuery(LuceneFields.VECTOR, queryVector, topN)`
  を組み立てて投入する (`LuceneVectorSearcher.java:57-58`)。`topN` は
  `offset + limit` (最低 1) として算出する (`LuceneVectorSearcher.java:55-56`)

## 3. 検索戦略

### 3.1 検索タイプの決定

実効的な `SearchType` は以下の優先順位で決定する
(`searchable-core/src/main/java/io/searchable/core/application/SearchService.java:144-154`)。

1. `request.searchType()` が `null` でない場合はそれを採用する
2. 1 でなく、対象 Namespace のうち先頭 1 件の
   `NamespaceConfig.architecture` が解決できればその値を採用する
3. いずれも解決できない場合は `SearchType.FULL_TEXT` をフォールバック値とする

### 3.2 各タイプの実行

| `SearchType` | 実行内容 | 実装 |
| --- | --- | --- |
| `FULL_TEXT` | `LuceneFullTextSearcher.search` を呼び出す | `SearchService.java:76-78` |
| `VECTOR` | `LuceneVectorSearcher.search` を呼び出す | `SearchService.java:79-80` |
| `HYBRID` | `HybridSearchOrchestrator` 経由で全文 + ベクトルを統合する | `SearchService.java:81-82, 134-142` |

### 3.3 Namespace ごとの戦略

`HYBRID` の実行戦略は対象 Namespace の `NamespaceConfig` に従う
(`SearchService.java:134-142`)。

| `NamespaceConfig.searchStrategy` | `NamespaceConfig.searchOrder` | 振る舞い |
| --- | --- | --- |
| `PARALLEL` | (無視) | 並列実行 + Reciprocal Rank Fusion (RRF) で和集合ランキング |
| `SEQUENTIAL` | `FULL_TEXT_FIRST` | 全文検索 → ベクトル検索 で再ランクし、両方に出現したヒットのみ残す (交差) |
| `SEQUENTIAL` | `VECTOR_FIRST` | ベクトル検索 → 全文検索 で再ランクし、両方に出現したヒットのみ残す (交差) |

詳細は §4 のスコア融合に記述する。

### 3.4 複数 Namespace の集約

`namespaceIds` に複数の Namespace が指定された場合、各 Namespace ごとに
検索を実行した後、ヒットを `SearchHit.score` の降順で並べ替えて単一の
ランキングに集約する
(`SearchService.java:87-109`)。

- 各 Namespace 検索時の `pagination` は `(offset=0, limit=offset+limit)` に
  拡張される (`SearchService.java:168-178`)
- 集約後に `pagination` の `offset` / `limit` を適用する
  (`SearchService.java:104-106`)
- `totalHits` は各 Namespace の合算値とする (`SearchService.java:100`)
- `maxScore` は集約後ランキングの先頭ヒットのスコアとする (`SearchService.java:107`)

### 3.5 Namespace 単位のスコア重み付け

各 Namespace は `NamespaceConfig.indexWeight` を持ち、当該 Namespace から
返却されたヒットの `score` にこの値を乗じる
(`SearchService.java:116-132`)。

- 既定値は `1.0` (`NamespaceConfig.DEFAULT_INDEX_WEIGHT`,
  `searchable-core/src/main/java/io/searchable/core/domain/namespace/NamespaceConfig.java:30`)
- `1.0` 超で強調、`0 < w < 1.0` で抑制、`0.0` でヒットを実質的に最下位に
  落とせる
- `1.0` のときは `SearchHit` の再構築をスキップする (no-op 最適化、
  `SearchService.java:121-123`)
- 重み付けは複数 Namespace の集約用に設計された値であり、単一 Namespace の
  検索にも一律で適用される (`SearchService.java:92`)

### 3.6 Namespace 解決

`namespaceIds` が空の場合は登録済みの全 Namespace を対象とする
(`SearchService.java:156-166`)。

`namespaceIds` に指定された ID のうち 1 つでも `NamespaceRepository.exists`
で見つからない場合、`NoSuchElementException("Namespace not found: " + id)`
を投げる (`SearchService.java:160-164`)。

## 4. スコアリングとスコア融合

### 4.1 全文検索のスコア (BM25)

全文検索のスコアは Lucene の既定 `BM25Similarity` で計算する
(`LuceneFullTextSearcher.java:62, 92-101`)。

- **既定パラメータ**: Lucene の既定値 (`k1 = 1.2`, `b = 0.75`) を採用する
  (`LuceneFullTextSearcher.java:90` 注釈、`BM25Similarity` のクラス既定値)
- **リクエスト時の上書き**: `SearchOptions.bm25K1` / `SearchOptions.bm25B`
  が指定された場合、`IndexSearcher.setSimilarity(new BM25Similarity(k1, b))`
  で当該リクエストの `IndexSearcher` だけを差し替える
  (`LuceneFullTextSearcher.java:92-101`)
- **値域**: `bm25K1` は有限の非負実数、`bm25B` は `[0.0, 1.0]` の範囲に
  制限する (`SearchOptions.java:46-54`)。範囲外は
  `IllegalArgumentException`
- **片方のみ指定**: もう一方は Lucene 既定値で補完する
  (`LuceneFullTextSearcher.java:98-99`)
- **両方とも `null`**: `setSimilarity` は呼ばず、`IndexSearcher` 既定の
  `BM25Similarity` をそのまま使う (`LuceneFullTextSearcher.java:95-97`)

### 4.2 フィールド単位の boost (`metaWeights`)

`SearchOptions.metaWeights` が指定された場合、`MultiFieldQueryParser` を
用いてフィールドごとに boost を掛ける
(`LuceneFullTextSearcher.java:104, 111-120`)。

- **キー**: Lucene 上のフィールド名 (`title`, `content` など)
- **値**: 正の有限実数。0 以下や NaN/Infinity は
  `IllegalArgumentException`
  (`SearchOptions.java:58-64`)
- **未指定時**: 単一フィールド `content` のみを対象とする
- **`title` 等を含めたい場合**: `Map.of("title", 2.0, "content", 1.0)`
  のように明示する

### 4.3 ベクトル検索のスコア

ベクトル検索のスコアは Lucene の `KnnFloatVectorQuery` が返す
`ScoreDoc.score` をそのまま採用する (`LuceneVectorSearcher.java:62, 101`)。

- **類似度関数**: `VectorSimilarityFunction.DOT_PRODUCT` (内積) を採用する
  (`LuceneDocumentMapper.java:92-93`)。L2 正規化済みベクトルを前提とすれば
  コサイン類似度と等価になる
- **スコアの値域**: Lucene が内積を `[0.0, 1.0]` 相当の正規化済みスコアに
  変換した値となる (`KnnFloatVectorQuery` の標準動作)
- **`k` の決定**: `Math.max(offset + limit, 1)` を `topN` として
  `KnnFloatVectorQuery` に渡す (`LuceneVectorSearcher.java:55-58`)。ページ化
  オフセットは検索結果取得後に切り出す (`LuceneVectorSearcher.java:84-86`)

### 4.4 ハイブリッドのスコア融合

`HybridSearchOrchestrator`
(`searchable-core/src/main/java/io/searchable/core/application/HybridSearchOrchestrator.java`)
は 2 種類の融合方式を実装する。融合は `ResultMerger`
(`searchable-core/src/main/java/io/searchable/core/application/ResultMerger.java`)
に委譲する。

両方式とも、内部で各エンジンに渡すページ要求は
`(offset=0, limit=max((offset+limit)*2, 20))` に拡張する
(`HybridSearchOrchestrator.java:101-113`)。融合精度を確保するため、ページ
窓の 2 倍以上かつ最低 20 件の候補を取得してから融合する。

#### 4.4.1 並列 + Reciprocal Rank Fusion (`PARALLEL`)

`HybridSearchOrchestrator.parallel`
(`HybridSearchOrchestrator.java:81-99`) は全文検索とベクトル検索を Virtual
Thread executor 上で並列実行し、`ResultMerger.reciprocalRankFusion` で
ランキングを融合する。

融合スコアの計算式 (`ResultMerger.java:33-56`):

```text
score(doc) = sum over each ranked list L containing doc of
             1 / (k + rank_L(doc) + 1)
```

- `k` は定数。既定値は `60` (`ResultMerger.DEFAULT_RRF_K`)。`parallel()` は
  この既定値で呼び出す (`HybridSearchOrchestrator.java:93-94`)
- `rank_L(doc)` は当該リスト内の 0-based 順位 (`ResultMerger.java:43-49`)
- 各文書 id に対し全リストの貢献を合算 (`Double::sum`)
- 並べ替えは融合スコア降順 (`ResultMerger.java:52-55`)
- 文書の代表 `SearchHit` は **最初に出現したリスト** のヒットを採用し、
  以降の出現は無視する (`ResultMerger.java:46`)。`SubResult` も最初の
  ヒットのものを保持する (`ResultMerger.java:84-98`)
- `rrfK` に 0 以下を渡すと `IllegalArgumentException` (`ResultMerger.java:36-38`)

#### 4.4.2 順次 + 交差再ランク (`SEQUENTIAL`)

`HybridSearchOrchestrator.sequential`
(`HybridSearchOrchestrator.java:56-75`) は `SearchOrder` の順で 2 段階に
実行し、`ResultMerger.intersect` で交差を取る。

- `FULL_TEXT_FIRST`: primary = 全文検索、secondary = ベクトル検索
- `VECTOR_FIRST`: primary = ベクトル検索、secondary = 全文検索
- `ResultMerger.intersect` (`ResultMerger.java:63-82`) は primary に出現した
  ヒットのうち secondary にも出現するものだけを残す
- 残ったヒットのスコアは **secondary 側の値** を採用する
  (`ResultMerger.java:75-78`)。これにより「primary で候補を絞り、secondary
  で再ランクする」セマンティクスになる
- いずれか一方にしか出現しないヒットは捨てる
- 並べ替えは融合後の (secondary 由来) スコアの降順 (`ResultMerger.java:80`)

### 4.5 スコアの解釈

`SearchHit.score`
(`searchable-core/src/main/java/io/searchable/core/domain/search/SearchHit.java:24`)
は「大きいほど関連度が高い」点だけが契約である。

- 値域はエンジン依存: BM25 は理論上下限なしの非負実数、ベクトル類似度は
  `[0.0, 1.0]` 相当、RRF は通常 `0.0` 近傍の小さな実数
- 異なるエンジン由来のスコアを直接比較してはならない
- `SearchResult.maxScore` (`SearchResult.java:21`) はヒット集合内の最大値で、
  ヒットが空のときは `0.0`

## 5. ページネーション

ページネーションは `PaginationParams`
(`searchable-core/src/main/java/io/searchable/core/domain/search/PaginationParams.java`)
で `offset` (0-based) と `limit` (1 以上) を指定する。

- 既定値は `(offset=0, limit=10)` (`PaginationParams.java:21`)
- `offset < 0` は `IllegalArgumentException` (`PaginationParams.java:12-14`)
- `limit <= 0` は `IllegalArgumentException` (`PaginationParams.java:15-17`)
- カーソル方式は提供しない

### 5.1 単一 Namespace のページ切り出し

全文検索では、`offset + limit` を `topN` として Lucene に問い合わせ、
親文書単位にグルーピングしたあと
`[offset, offset+limit)` を切り出す
(`LuceneFullTextSearcher.java:65-66, 163-170`)。

ベクトル検索でも同様に `offset + limit` 件取得し、
`[offset, min(hits.length, offset+limit))` を切り出す
(`LuceneVectorSearcher.java:55-56, 84-86`)。

### 5.2 複数 Namespace の集約とページ切り出し

複数 Namespace を対象とする場合、`SearchService.aggregate` は各 Namespace
に対し拡張ページ要求 `(offset=0, limit=offset+limit)` を発行し
(`SearchService.java:168-178`)、合算したヒットを `score` 降順で並べ替えた
あと `[offset, offset+limit)` を切り出す (`SearchService.java:103-106`)。

### 5.3 ハイブリッドのページ切り出し

`HybridSearchOrchestrator` は内部で `(offset=0, limit=max((offset+limit)*2, 20))`
の候補を取得して融合し、`paginate` で
`[offset, min(merged.size(), offset+limit))` を切り出す
(`HybridSearchOrchestrator.java:115-124`)。`totalHits` は融合後の重複排除済み
ヒット件数となる (`HybridSearchOrchestrator.java:123`)。

### 5.4 `SubResult` の扱い

全文検索は、同一親文書 (`PARENT_ID`) に属するチャンクヒットを単一の
`SearchHit` に集約する (`LuceneFullTextSearcher.java:143-160`)。
1 件目を主結果として採用し、2 件目以降は `SubResult`
(`searchable-core/src/main/java/io/searchable/core/domain/search/SubResult.java`)
として `SearchHit.subResults` に格納する。ページネーションは集約後の
親文書集合に対して適用する。

ベクトル検索および純粋なベクトル経由のハイブリッドヒットには `SubResult` を
付けない (`LuceneVectorSearcher.java:101-102` で `SubResult` を生成していない)。
この設計理由は
[`../design/architecture/overview.md`](../design/architecture/overview.md) §5.7
を参照。

## 6. ハイライト

ハイライトは `SearchOptions`
(`searchable-core/src/main/java/io/searchable/core/domain/search/SearchOptions.java`)
で制御する。

| オプション | 型 | 既定値 | 意味 |
| --- | --- | --- | --- |
| `highlightEnabled` | `boolean` | `true` (defaults 経由) | ハイライトの生成可否 |
| `snippetLength` | `int` | `200` | 1 断片あたりの最大文字数。0 以下は `200` に補正 |
| `escapeMarkup` | `boolean` | `true` | 元文書中の HTML 特殊文字をエスケープして `<mark>` だけを残すか |
| `lazyLoad` | `boolean` | `false` | `true` のとき `content` とハイライトを返さず最小ペイロードにする |

### 6.1 全文検索でのハイライト

`LuceneFullTextSearcher` は `highlightEnabled == true` かつ
`lazyLoad == false` のときに Lucene の `Highlighter` を生成する
(`LuceneFullTextSearcher.java:129-138, 187-191`)。

- フォーマッタ: `SimpleHTMLFormatter("<mark>", "</mark>")`
  (`LuceneFullTextSearcher.java:37-38, 131`)
- エンコーダ: `escapeMarkup == true` のとき `SimpleHTMLEncoder`、`false` の
  ときはなし (`LuceneFullTextSearcher.java:132-134`)
- フラグメンタ: `SimpleFragmenter(snippetLength)`
  (`LuceneFullTextSearcher.java:135`)
- スコアラ: `QueryScorer(query)` を使用 (クエリと一致した語句に対して
  `<mark>` を挿入)
- トークンソース: `TokenSources.getTokenStream(...)` で stored term vectors
  から再構築する (`LuceneFullTextSearcher.java:222-238`)。`content` フィールド
  には term vector が保存されている
  (`LuceneFields.ANALYZED_STORED_WITH_VECTORS`,
  `searchable-core/src/main/java/io/searchable/core/infrastructure/lucene/LuceneFields.java:11-37`)

### 6.2 ハイライト結果の形式

`SearchHit.highlights`
(`SearchHit.java:30`) は `Map<String, List<String>>` 型で、
キーがフィールド名、値が断片の列となる。

- 全文検索は現状 `content` フィールドに対する単一断片 (`List.of(fragment)`)
  のみを返す (`LuceneFullTextSearcher.java:236-238`)
- 該当断片を生成できない場合は空マップ `Map.of()` を返す
  (`LuceneFullTextSearcher.java:232-235`)
- `lazyLoad == true` のときは `Map.of()` のみを返す
  (`LuceneFullTextSearcher.java:187-190`)
- `highlightEnabled == false` のときも `Map.of()` を返す
  (`LuceneFullTextSearcher.java:136-138, 187`)

### 6.3 ベクトル検索でのハイライト

`LuceneVectorSearcher` はハイライトを生成しない (`LuceneVectorSearcher.java:101-102`)。
ヒットの `highlights` は常に空マップ `Map.of()` となる。

### 6.4 ハイブリッドでのハイライト

ハイブリッドのヒットは融合元の代表ヒットに付与されたハイライトをそのまま
持ち越す (`ResultMerger.withScore`,
`ResultMerger.java:84-98`)。全文検索由来のヒットには `<mark>` 付き断片が
付き、ベクトル由来のヒットには付かない。

## 7. フィルタ

`SearchRequest.filters` は `Map<String, Object>` 型で、検索後にヒットを
絞り込むポストフィルタとして適用する。

ライブラリ層では `FacetFilter`
(`searchable-core/src/main/java/io/searchable/core/application/FacetFilter.java`)
が実装を提供する (現行 `SearchService.search` は `FacetFilter` を直接呼んで
いないが、後段のアプリケーションが利用する共通実装として公開されている)。

### 7.1 フィルタの意味論

`FacetFilter.apply`
(`FacetFilter.java:30-36`) のセマンティクス:

| 条件 | 結合 |
| --- | --- |
| 複数キー (`{ "category": "blog", "lang": "ja" }`) | AND |
| 単一キーの値がリスト (`{ "tags": ["a", "b"] }`) | OR |

### 7.2 予約キー

| キー | 比較対象 | 定数 |
| --- | --- | --- |
| `_namespace` | `SearchHit.namespaceId()` | `FacetFilter.NAMESPACE_KEY` (`FacetFilter.java:25`) |
| `_id` | `SearchHit.documentId()` | `FacetFilter.DOCUMENT_ID_KEY` (`FacetFilter.java:26`) |
| 上記以外 | `SearchHit.metadata().get(key)` | `FacetFilter.java:80-83` |

### 7.3 比較規則

- 文字列化比較: `actual.toString().equals(expected.toString())`
  (`FacetFilter.java:62-72`)
- `actual` がリストのとき: いずれかの要素が `expected` のいずれかと一致すれば
  ヒット (`FacetFilter.java:57-69`)
- `actual` が `null` (該当 metadata なし) のときは不一致扱い
  (`FacetFilter.java:53-55`)
- `filters` が `null` または空の場合はフィルタ無効 (入力をそのまま返す、
  `FacetFilter.java:32-34`)

### 7.4 フィルタ適用のタイミング

フィルタは **検索 (Lucene クエリ) には組み込まれない**。Lucene から取得した
ヒットに対するアプリケーション層のポストフィルタとして適用する設計である
(`FacetFilter` の Javadoc, `FacetFilter.java:8-22`)。

## 8. メタデータ enrichment

検索エンジン (Lucene) は `SearchHit.metadata` を空の `Map.of()` で返す
(`LuceneFullTextSearcher.java:201-202`, `LuceneVectorSearcher.java:101-102`)。
文書レベル属性はメタデータ DB に保存されているため、`SearchService` は
`SearchResultEnricher`
(`searchable-core/src/main/java/io/searchable/core/application/SearchResultEnricher.java`)
を介して全ヒットの `metadata` を 1 度の JDBC IN 句クエリで取得し、ヒットに
注入する (`SearchService.java:84`, `SearchResultEnricher.java:43-74`)。

- 同時に `SubResult.anchorUrl` を `metadata.url + "#" + slugify(heading)`
  で再生成する (`SearchResultEnricher.java:76-96`,
  `searchable-core/src/main/java/io/searchable/core/application/AnchorUrls.java`)
- `metadata.url` が未設定の文書では `SubResult.anchorUrl` は `null`
  のまま (`SearchResultEnricher.java:78-83`)
- 注入後の `SearchHit.metadata` は元の `DocumentMetadataRecord.metadata`
  と等価になる

詳細は [`document-metadata.md`](document-metadata.md) を参照。

## 9. 検索結果の構造

`SearchResult`
(`searchable-core/src/main/java/io/searchable/core/domain/search/SearchResult.java`)
の各フィールドの契約。

| フィールド | 型 | 契約 |
| --- | --- | --- |
| `hits` | `List<SearchHit>` | 不変リスト。サイズは `pagination.limit()` 以下 |
| `totalHits` | `long` | 0 以上。負値はコンストラクタで `IllegalArgumentException` |
| `maxScore` | `double` | `hits` が空のとき `0.0`、それ以外は先頭ヒットの `score` |
| `aggregations` | `Map<String, Object>` | 現状は `Map.of()` を返す (集計結果の搬送枠) |
| `tookMs` | `long` | 検索開始から戻り値生成までの経過ミリ秒 (0 以上) |

`totalHits` の値:

- 単一 Namespace の全文検索/ベクトル検索: Lucene の `TopDocs.totalHits.value()`
  (`LuceneFullTextSearcher.java:72`, `LuceneVectorSearcher.java:64`)
- 複数 Namespace の集約: 各 Namespace の `totalHits` の単純合算
  (`SearchService.java:100`)
- ハイブリッド: 融合後の重複排除済みヒット件数
  (`HybridSearchOrchestrator.java:123`)

`SearchResult.empty(tookMs)` は対象 Namespace が空のときのフォールバックとして
返却される (`SearchResult.java:40-42`, `SearchService.java:70-72`)。

## 10. エラー仕様

| 状況 | 例外 | 発生箇所 |
| --- | --- | --- |
| `SearchRequest` の `query` が `null` | `NullPointerException("query must not be null")` | `SearchRequest.java:25` |
| `SearchRequest` の `query` が空白のみ | `IllegalArgumentException("query must not be blank")` | `SearchRequest.java:27` |
| `SearchService.search(null)` | `NullPointerException("request must not be null")` | `SearchService.java:67` |
| `namespaceIds` 中に未登録の Namespace | `NoSuchElementException("Namespace not found: " + id)` | `SearchService.java:160-164` |
| ハイブリッド対象 Namespace が解決できない | `NoSuchElementException("Namespace not found: " + namespaceId)` | `SearchService.java:137` |
| `PaginationParams.offset < 0` | `IllegalArgumentException("offset must not be negative, ...")` | `PaginationParams.java:12-14` |
| `PaginationParams.limit <= 0` | `IllegalArgumentException("limit must be positive, ...")` | `PaginationParams.java:15-17` |
| `SearchOptions.bm25K1` が負/NaN/Infinity | `IllegalArgumentException("bm25K1 must be a finite non-negative number, ...")` | `SearchOptions.java:47-50` |
| `SearchOptions.bm25B` が `[0.0, 1.0]` 外/NaN | `IllegalArgumentException("bm25B must be in [0.0, 1.0], ...")` | `SearchOptions.java:51-54` |
| `SearchOptions.metaWeights` の値が 0 以下/NaN/Infinity | `IllegalArgumentException("metaWeights[...] must be a finite positive number, ...")` | `SearchOptions.java:58-64` |
| 全文検索の Lucene 例外 (I/O など) | `IllegalStateException("Failed to execute search on namespace " + namespaceId, cause)` | `LuceneFullTextSearcher.java:73-75` |
| ベクトル検索の Lucene 例外 (I/O など) | `IllegalStateException("Failed to execute vector search on namespace " + namespaceId, cause)` | `LuceneVectorSearcher.java:65-67` |
| ハイブリッド並列実行のタスク失敗 | `IllegalStateException("Parallel hybrid search failed", cause)` | `HybridSearchOrchestrator.java:96-98` |
| RRF の `k <= 0` | `IllegalArgumentException("rrfK must be positive")` | `ResultMerger.java:36-38` |
| ベクトル次元が登録時と不一致 | Lucene 由来の例外 (`IllegalArgumentException` 等) を `IllegalStateException` で包んで再送出 | `LuceneVectorSearcher.java:65-67` |

## 11. 動作の保証範囲

本仕様は `searchable-core` モジュールが提供する `SearchService` /
`HybridSearchOrchestrator` / `LuceneFullTextSearcher` /
`LuceneVectorSearcher` / `ResultMerger` の振る舞いを正本として定義する。

以下は本仕様の対象外:

- REST API のリクエスト/レスポンス形式 (`examples/api/openapi.yaml` 側で
  定義)
- MCP サーバーの tool 仕様 (`examples/mcp/guide.ja.md` 側で定義)
- CLI コマンドの引数体系 (`cli-commands.md` 側で定義)
- ベクトル埋め込みモデルの選定や `EmbeddingProvider` 実装の詳細
  (`docs/public/vector-search-guide.md` を参照)

関連仕様:

- [`document-metadata.md`](document-metadata.md) — `metadata` 予約キーと
  `metadata.url` の契約
- [`java-api.md`](README.md) — `SearchableLibrary` 公開 API (TODO)
- [`../design/architecture/overview.md`](../design/architecture/overview.md) §3.1,
  §4.2, §5.4-5.7, §7 — 設計の背景と分離方針
