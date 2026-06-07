# PoC: Lucene 検索性能テスト（10万件・JMH）

TASK-003 用の性能検証。10万件の合成日本語コーパスを Lucene + Kuromoji で
インデックス化し、検索レイテンシ分布 (warm / cold) を **OpenJDK JMH** で計測する。
TASK-008 で旧シングルファイル `main` ハーネスから JMH ベースの
`SearchBenchmark` に置き換え済み。

## 実行

```bash
# ビルド (shade で benchmarks.jar を生成)
./mvnw -q -DskipTests package

# 既定設定で warm / cold 両方を実行
java -jar target/benchmarks.jar

# 個別実行
java -jar target/benchmarks.jar SearchBenchmark.warmQuery
java -jar target/benchmarks.jar SearchBenchmark.coldQuery

# JSON で結果保存 (CI / 比較用途)
java -jar target/benchmarks.jar -rf json -rff result.json

# Fork 数の差し替え (cold の母数を増やす)
java -jar target/benchmarks.jar SearchBenchmark.coldQuery -f 10
```

## 計測モード

JMH ハーネスは `warmQuery` と `coldQuery` の 2 ベンチを単一クラス内に
持つ。`@Setup(Level.Trial)` で 10 万件のインデックスを `MMapDirectory`
（temp dir）上に構築し、`@TearDown` でクリーンアップする。

| ベンチ | モード | warmup | measurement | fork | 計測する状態 |
| --- | --- | --- | --- | --- | --- |
| `warmQuery` | `Mode.SampleTime` | 5 iter × 1 s | 10 iter × 5 s | 1 | 定常状態 (JIT 暖機後) のレイテンシ分布 |
| `coldQuery` | `Mode.SingleShotTime` | なし | 1 iter × 1 shot | 5 | 新規 JVM 初回投入のレイテンシ |

クエリ生成:

- `warmQuery` — 14 種類のクエリ語をローテーション (`(counter++) % 14`)
- `coldQuery` — 単一クエリ (`"Lucene"`) を fresh JVM 各 fork で 1 回ずつ

## 実測結果

> 計測環境: macOS / Apple Silicon, Java 21, Lucene 10.4.0, JMH 1.37,
> `MMapDirectory`, `JapaneseAnalyzer`, JVM `-Xms2g -Xmx2g`
> 計測日: 2026-06-07

### Warm (定常状態、SampleTime)

| 指標 | 値 |
| --- | --- |
| サンプル数 | 432,614 |
| mean | 115.5 µs |
| p50 | 78.0 µs |
| p90 | 326.7 µs |
| p95 | 338.9 µs |
| p99 | 358.4 µs |
| p99.9 | 410.6 µs |
| p99.99 | 753.8 µs |
| max | 3,039 µs |

### Cold (新規 JVM 初回、SingleShotTime, 5 fork)

| 指標 | 値 |
| --- | --- |
| N | 5 |
| mean | 9,194.5 µs (≈ 9.2 ms) |
| min (p0) | 8,833 µs |
| p50 | 9,225.7 µs |
| max (p100) | 9,467 µs |

## 観察

- **warm の p99 は約 0.36 ms**。旧 PoC ハーネス (整数 ms 丸め) が報告していた
  「p99 = 1 ms」は ms 解像度における天井値であり、µs 単位ではより緩い分布になる
  ことが本計測で裏付けられた。
- **cold は warm の約 80 倍** (9.2 ms vs 0.12 ms)。クラスロード・JIT 未完了・
  ページキャッシュ未温の合算であり、初回検索だけはこのオーダーを想定する必要がある。
- max (3.04 ms) は warm モードのテールでも `< 5 ms`。500 ms 目標に対して 2 桁以上の
  マージンを維持。
- JMH 起動時に `Java vector incubator module is not readable` 警告が出る。
  Lucene 10.4 はスカラー fallback で動作しており本計測値もこの状態。Vector API
  を有効化するには `@Fork(jvmArgs = {... "--add-modules", "jdk.incubator.vector"})`
  を追加する。プロダクション設定に揃える際は調整する。

## 結論

- 検索レイテンシは目標値 500 ms に対して **warm で 3 桁以上、cold でも 2 桁の
  マージン**。10 万件規模では搬送経路・直列処理が支配的でなければボトルネックに
  ならない。
- 旧 PoC ハーネスが報告した「p99 = 0/1 ms」は ms 整数丸めによるアーチファクトで
  あり、µs 解像度の分布を取りたい場合は本 JMH ハーネスを正本とする。

## 注意点

- 本 PoC は単純な合成文書による測定。実運用ではフィールド数・ハイライト処理・
  フィルタ等で warm 値も上振れする可能性があるが、桁単位のマージンがあるため
  500 ms 目標の達成は現実的。
- 書き込み混在ワークロード (write-while-search) は本 PoC のスコープ外。
  BACKLOG-005 で別途扱う。
