# ADR-0001: searchable-cli の executable JAR 化に maven-shade-plugin を採用する

- ステータス: 採用
- 日付: 2026-06-01
- 関連: M2 着手前の配布物整理

## コンテキスト

`searchable-cli` は当初 `maven-jar-plugin` の `addClasspath` + `classpathPrefix=lib/`
で manifest に `Class-Path: lib/` を埋め、`maven-dependency-plugin` の
`copy-dependencies` で `target/lib/` に依存 jar を並べる構成だった。

成果物が「薄い jar + 隣の lib/ ディレクトリ」のペアになり、以下の問題が表面化した。

- 配布や利用説明で「jar 1 個では動かない」ことの説明コストが高い
- 同梱の起動シェル `searchable-cli/src/main/scripts/searchable` が
  `${self_dir}/..` を `module_root` として `target/classes` / `target/lib` を
  探すが、`self_dir` は `searchable-cli/src/main/scripts/` であり実際には
  `searchable-cli/target/` を指せず、開発チェックアウトでは起動できない
- `docs/public/getting-started.ja.md` / `docs/public/cli-guide.ja.md` / `examples/webapp/README.md`
  で配布物の説明が冗長になっていた

ゴール: `java -jar searchable-cli-<version>.jar <args>` だけで動く単一 jar 配布物
にする。

## 検討した選択肢

| # | 方式 | 単一 jar | SPI マージ | 設定量 | 備考 |
| --- | --- | --- | --- | --- | --- |
| 1 | **maven-shade-plugin** | ✅ | ✅ `ServicesResourceTransformer` | 中(約 30 行) | 業界標準 |
| 2 | spring-boot-maven-plugin `repackage` | ✅(ネスト jar) | ✅(各 jar 分離保持) | 小(約 10 行) | Spring 依存導入が必要 |
| 3 | maven-assembly-plugin `jar-with-dependencies` プリセット | ✅ | ❌(後勝ち上書き) | 小 | Lucene SPI が壊れる |
| 4 | maven-assembly-plugin + `metaInf-services` container handler | ✅ | ✅ | 中(別 descriptor XML 必要) | shade と同等の手間 |
| 5 | jlink / GraalVM native-image | ❌(image / native) | N/A | 大 | jar 配布の要件から外れる |

### 各選択肢を採らなかった理由

- **2. spring-boot-maven-plugin**: `searchable-cli` は思想として Spring 非依存の
  純 CLI モジュールとして設計されている。`examples/api` / `examples/webapp` /
  `searchable-admin` は Spring 利用が前提だが、CLI 側に Spring Boot loader を
  取り込むのは設計方針との不整合。
- **3. maven-assembly-plugin (jar-with-dependencies)**: Lucene が
  `META-INF/services/` に `Codec` / `PostingsFormat` / `KnnVectorsFormat` /
  `TokenizerFactory` 等の SPI 登録ファイルを **同名で複数 jar(lucene-core /
  lucene-backward-codecs / lucene-analysis-kuromoji 等) に分散して** 持つ。
  assembly のデフォルトは展開後の同名ファイルを後勝ちで 1 個だけ残すため、
  `ServiceLoader.load(Codec.class)` で空または不完全になり、`IndexWriter` 起動時に
  「An SPI class of type ... with name '...' does not exist」で落ちる。
- **4. maven-assembly-plugin + `metaInf-services` handler**: 技術的には SPI
  マージが可能だが、`<descriptor>src/assembly/uber.xml</descriptor>` を別ファイル
  として用意する必要があり inline で書ける shade より冗長。さらに署名 jar
  (PDFBox 経由の bouncycastle 等) の `META-INF/*.SF/*.DSA/*.RSA` 除外は
  `unpackOptions/excludes` で個別記述が必要で、shade の `<filters>` よりも書き味が悪い。
- **5. jlink / native-image**: 配布物が jar から外れる。jlink は依存が全て JPMS
  module 化されている前提で扱いづらい。native-image は ONNX Runtime や Lucene の
  reflection ヘビーな箇所のための reachability metadata を整備するコストが大きく、
  かつ OS/arch 固有ビルドになる。今回のスコープ外。

## 決定

**maven-shade-plugin** を採用する。

`searchable-cli/pom.xml` に以下を必須設定として組み込む。

1. `ManifestResourceTransformer`
   - `<mainClass>io.searchable.cli.SearchableCli</mainClass>`
   - `<manifestEntries><Multi-Release>true</Multi-Release></manifestEntries>`
     (Lucene 10 が MR-JAR を採用しているため)
2. `ServicesResourceTransformer`
   - `META-INF/services/` 配下のファイルを各 jar から連結する。Lucene codec /
     SLF4J プロバイダ / Jackson ServiceLoader 等を保全する目的
3. `<filters>` で `META-INF/*.SF` / `*.DSA` / `*.RSA` を除外
   - PDFBox の推移依存(bouncycastle 系)が署名 jar であり、これを残すと
     `SecurityException: Invalid signature file digest` で起動できない
4. `<createDependencyReducedPom>false</createDependencyReducedPom>`
   - リアクタ汚染回避(本モジュールが他モジュールから依存される側ではないため
     不要)

`maven-dependency-plugin` の `copy-dependencies` execution は廃止する。
`resolve-test-agent-paths` execution(surefire の byte-buddy agent パス解決用)は残す。

## 影響範囲

### 配布物の変更
- 配布物が `searchable-cli/target/searchable-cli-<version>.jar` のみになる
- `target/lib/` は生成されなくなる
- jar サイズが増える(Lucene + ONNX Runtime + Apache POI + PDFBox を含むため
  数十 MB 規模)
- パッケージビルドに数秒〜十数秒上乗せ

### 追従が必要な箇所
- `searchable-cli/src/main/scripts/searchable` の classpath 解決ロジック
  - 現状は `lib/` 同梱を前提とした 3 経路の resolver。shade 後は
    `target/searchable-cli-*.jar` を見つけて `java -jar` で起動する単純な
    スクリプトに作り替える
- `docs/public/cli-guide.ja.md` の「ビルドと配布」節
- `docs/public/getting-started.ja.md` のケース B ビルド成果物の記述
- `examples/webapp/README.md` の `searchable-cli` 起動コマンド例
- `searchable-cli/pom.xml` のテスト設定(`maven-jar-plugin` を簡素化、または除去)

### 互換性
- ライブラリ API には影響なし
- CLI のコマンド体系・サブコマンドには影響なし
- 既存利用者は単に `java -jar searchable-cli-<version>.jar ...` で同じ操作が可能

## 参考

- [maven-shade-plugin 公式ドキュメント](https://maven.apache.org/plugins/maven-shade-plugin/)
- [Lucene のサービスローダー設計(`org.apache.lucene.util.NamedSPILoader`)](https://lucene.apache.org/core/10_2_0/core/org/apache/lucene/util/NamedSPILoader.html)
- 本 ADR の議論経緯はチャットログ参照(M2 着手前のチェックポイント直前)
