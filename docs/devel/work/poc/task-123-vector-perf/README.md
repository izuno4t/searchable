# PoC: Lucene ベクトル検索性能テスト（10万件・JMH）

TASK-123 用の性能検証。10万件のベクトル付きドキュメントを Lucene HNSW
（`KnnFloatVectorField` + `DOT_PRODUCT` 類似度）に投入し、KNN 検索の
warm/cold レイテンシ分布を **OpenJDK JMH** で計測する。
TASK-008 で旧 `VectorSearchPerformanceTest` (整数 ms 丸めのシングルファイル main)
から JMH ベースの `VectorSearchBenchmark` に置き換え済み。

## 実行

```bash
# ビルド (shade で benchmarks.jar を生成)
./mvnw -q -DskipTests package

# 既定設定で warm / cold 両方を実行
java -jar target/benchmarks.jar

# 個別実行
java -jar target/benchmarks.jar VectorSearchBenchmark.warmQuery
java -jar target/benchmarks.jar VectorSearchBenchmark.coldQuery

# JSON で結果保存
java -jar target/benchmarks.jar -rf json -rff result.json

# Cold fork 数を増やす場合 (1 fork ≒ 88 秒で index 再構築するため要注意)
java -jar target/benchmarks.jar VectorSearchBenchmark.coldQuery -f 5
```

## 計測モード

JMH ハーネスは `warmQuery` と `coldQuery` の 2 ベンチを単一クラス内に
持つ。`@Setup(Level.Trial)` で 10 万件 × 384 次元の HNSW インデックスを
`MMapDirectory` (temp dir) 上に構築し、`@TearDown` でクリーンアップする。

| ベンチ | モード | warmup | measurement | fork | 計測する状態 |
| --- | --- | --- | --- | --- | --- |
| `warmQuery` | `Mode.SampleTime` | 5 iter × 1 s | 10 iter × 5 s | 1 | 定常状態 (JIT 暖機後) のレイテンシ分布 |
| `coldQuery` | `Mode.SingleShotTime` | なし | 1 iter × 1 shot | 3 | 新規 JVM 初回投入のレイテンシ |

クエリ生成:

- `warmQuery` — トピック語にカウンタを連結 (`"<topic> query #N"`) し
  ハッシュ埋め込みで動的にベクトル化
- `coldQuery` — `"形態素解析 query #0"` を全 fork で同一に投入

埋め込みはハッシュベースの決定的 384 次元ベクトル (SHA-256 → 正規化)。
意味的類似度は反映しないが、HNSW 探索コスト自体は実モデル使用時と概ね同等。

## 実測結果

> 計測環境: macOS / Apple Silicon, Java 21, Lucene 10.4.0, JMH 1.37,
> HNSW (`DOT_PRODUCT`), `MMapDirectory`, JVM `-Xms3g -Xmx3g`
> 計測日: 2026-06-07

### Warm (定常状態、SampleTime)

| 指標 | 値 |
| --- | --- |
| サンプル数 | 304,905 |
| mean | 164.0 µs |
| p50 | 158.2 µs |
| p90 | 207.6 µs |
| p95 | 224.8 µs |
| p99 | 262.7 µs |
| p99.9 | 330.3 µs |
| p99.99 | 579.1 µs |
| max | 3,715 µs |

### Cold (新規 JVM 初回、SingleShotTime, 3 fork)

| 指標 | 値 |
| --- | --- |
| N | 3 |
| mean | 7,566.6 µs (≈ 7.6 ms) |
| min (p0) | 7,367.7 µs |
| p50 | 7,507.7 µs |
| max (p100) | 7,824.4 µs |

### インデックス構築 (Setup 内、計測対象外)

| 指標 | 値 |
| --- | --- |
| インデックス作成（10万件、HNSW構築含む） | ≈ 80–90 秒 / fork |

## 観察

- **warm の p99 は約 0.26 ms**。旧 PoC ハーネス (整数 ms 丸め) が報告していた
  「p99 = 0 ms」は ms 解像度における 0 桁丸めであり、µs 単位では 0.2 ms 台が
  実態であることが本計測で裏付けられた。
- **cold は warm の約 46 倍** (7.6 ms vs 0.16 ms)。HNSW グラフの初回探索で
  発生するページキャッシュ読み込み・JIT 未完了・classload コストの合算。
  全文検索の cold (9.2 ms) と概ね同等オーダー。
- warm の max (3.7 ms) は warm 全文検索の max (3.0 ms) と同程度。500 ms 目標に
  対して 2 桁以上のマージンを維持。
- JMH 起動時に `Java vector incubator module is not readable` 警告が出る。
  Lucene 10.4 はスカラー fallback で動作。Vector API を有効化するには
  `@Fork(jvmArgs = {... "--add-modules", "jdk.incubator.vector"})` を追加する。
  プロダクション設定に揃える際は調整する。
- インデックス構築コストは依然として 80 秒/10万件オーダー。運用では差分追加で
  amortize される想定。

## 結論

- KNN 検索レイテンシは目標値 500 ms に対して **warm で 3 桁、cold でも 2 桁の
  マージン**。全文検索と同オーダーで HNSW がボトルネックにならない。
- 旧 PoC ハーネスが報告した「p99 = 0 ms」は ms 整数丸めによるアーチファクトで
  あり、µs 解像度の分布を取りたい場合は本 JMH ハーネスを正本とする。
- 埋め込み計算時間 (モデル推論) は本ベンチには含まれない。実モデル
  (multilingual-e5 等) ではエンドツーエンドのレイテンシは増加する。

## 注意点

- ハッシュベース埋め込みでの計測。実モデルでも HNSW 検索速度は同程度だが、
  クエリ側の埋め込み計算が別途必要 (モデル/ハードウェア依存)。
- HNSW パラメーター (M / efConstruction / efSearch) は Lucene のデフォルトで
  実行。チューニング指針は `docs/public/vector-search-guide.md` 参照。
- 書き込み混在ワークロード (write-while-search) は本 PoC のスコープ外。
  BACKLOG-005 で別途扱う。
