# TASKS - M2 (Review-202606)

Milestone: M2 (Review-202606)
Goal: 2026-06 の Claude レビュー指摘 (P0-P3) への対応と、それから派生したドキュメント管理体系 (ガイドライン v1.0) 再編・仕様整備を完遂する
Status: 完了 (2026-06-07)
Note: 後続マイルストーン (M3 = AI 統合) は `work/tasks/task.md` (旧 `m3.md`) に集約

## 前提

- 本タスクは 2026-06-07 のレビュー受領内容を起点とし、事前の事実確認で確定した不整合の解消、
  および対応中に発見した派生作業 (docs 再編・specs 整備・CLAUDE.md 最新化) を統合管理する
- レビュー指摘のうち、事実確認の結果コード調査が追加で必要なもの (パフォーマンス計測・Multi-tenant 設計・プラグイン拡張点など) は「現状調査 → 判断 → 反映」の3段で分割する
- 削除・別リポジトリ化など破壊的判断を含むタスクは、判断段階でユーザー合意を取り、合意後に実行する
- 完了済みタスク (✅) と取消タスク (🚫) は履歴として残し、archive へは本マイルストーン完了時に一括移動する

## ワークフロールール

- タスク開始時にステータスを 🚧 に更新する
- タスク完了時にステータスを ✅ に更新する
- DependsOn のタスクがすべて ✅ になるまで開始しない
- タスク着手時にまず該当箇所の現状を確認し、すでに解消されていれば 🚫 にして根拠を記録する

## ステータス表記

| ステータス | 意味 |
| ---- | ---- |
| ⏳ | TODO |
| 🚧 | IN_PROGRESS |
| 🧪 | REVIEW |
| ✅ | DONE |
| 🚫 | CANCELLED |

## タスク一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| TASK-001 | 🚫 | CLAUDE.md の "no source code yet" 記述と旧モジュール構成を最新の実装状態に書き直す | - |
| TASK-002 | ✅ | README の Lucene バージョン表記を pom.xml の 10.4.0 に統一する | - |
| TASK-003 | ✅ | README の "Sudachi" 言及を pom.xml の依存状況に合わせて整理する | - |
| TASK-004 | ✅ | README の "AsciiDoc" を含む対応フォーマット一覧を実装状況に合わせて修正する | - |
| TASK-005 | ✅ | ルート直下の旧 searchable-api/mcp/ui ディレクトリの扱いを決定し処置する | - |
| TASK-006 | ✅ | pom.xml の `<modules>` に examples/ 配下を登録するかの方針を決定し反映する | TASK-005 |
| TASK-007 | ✅ | ベンチコード (task-003/task-123) の計測単位と warm/cold 区分の現状を点検しレポートする | - |
| TASK-008 | ✅ | ベンチコードを JMH ベースに置き換え warm/cold 両方の数値を出力する | TASK-007 |
| TASK-009 | ✅ | README Performance セクションの数値表記を JMH 出力に基づき更新する | TASK-008 |
| TASK-010 | ✅ | README の "in-memory" 表現を mmap 実態に合わせて修正する | - |
| TASK-011 | ✅ | README の "Embeddable, not infrastructure" 表現を Spring Boot 依存実態に合わせて緩和する | - |
| TASK-012 | ✅ | README の "Multi-tenant by design" 表現を JVM 内論理分離の実態に合わせて緩和する | - |
| TASK-013 | ✅ | Multi-tenant の制約 (OOM・ノイジーネイバー・QoS・暗号化) を docs に明記する | TASK-012 |
| TASK-014 | ✅ | searchable-admin の embeddable 性との整合方針を決定し README/docs に反映する | TASK-011 |
| TASK-015 | ✅ | examples/webapp と examples/search-ui の位置付け方針を決定し README に反映する | TASK-006 |
| TASK-016 | ✅ | ONNX モデル (multilingual-e5) の配布・取得・キャッシュ戦略を docs/public/vector-search-guide.md に追記する | - |
| TASK-017 | ✅ | HNSW パラメーター (M・efConstruction・efSearch) のチューニング指針を docs/public/vector-search-guide.md に追記する | - |
| TASK-018 | ✅ | ベクトル初期インデックス構築 (88s/100k) の再現条件 (CPU・並列度・バッチサイズ) を docs に明記する | TASK-007 |
| TASK-019 | ✅ | README の MCP プロトコルバージョン badge と最新仕様追従方針を更新する | - |
| TASK-020 | ✅ | MCP server を searchable-mcp モジュールとして昇格するか examples 維持かを決定し反映する | TASK-005,TASK-006 |
| TASK-021 | ✅ | プラグイン API で差し替え可能な拡張点 (DataSource・Analyzer・Embedder ほか) を README に明示する | - |
| TASK-022 | ✅ | pom.xml の Lucene/Jackson 依存を BOM import に置き換えるかを判断し反映する | - |
| TASK-023 | ✅ | CI で検証する JDK バージョン一覧を README に明記する | - |
| TASK-024 | ✅ | ガイドライン v1.0 準拠のディレクトリ構成へ docs/ 配下を全面移行する | - |
| TASK-025 | ✅ | 旧パスへの参照を新パスへ書き換える (README・CLAUDE.md・examples・Java コメントほか) | TASK-024 |
| TASK-026 | ✅ | 入口 README を新設する (`docs/README.md` / `docs/devel/README.md` / `docs/devel/design/README.md`) | TASK-024 |
| TASK-027 | ✅ | CLAUDE.md を実装現状 (Pre-1.0・実モジュール構成・実依存) に最新化する | TASK-025 |
| TASK-028 | ✅ | 新設した入口 README と統合 task.md・specs/ 配下を `git add` で staging する | TASK-026,TASK-032 |
| TASK-029 | ✅ | 再編後のファイル全体を markdownlint-cli2 で検証し、警告を解消する | TASK-024,TASK-026 |
| TASK-030 | ✅ | `examples/filesystem-plugin/` の壊れた残骸 (src なし・README なし・pom なし) の処置を決定し実施する | - |
| TASK-031 | ✅ | カレントタスク識別ルール (「`work/tasks/` 配下 = 進行中」「`task.md` が常設の最優先」) を `docs/devel/README.md` に追記する | TASK-026 |
| TASK-032 | ✅ | `docs/devel/specs/` を新設し、仕様の所在マップ README を整備する | TASK-024 |
| TASK-033 | 🚫 | ~~`specs/java-api.md` を書き起こす~~ — Javadoc と二重管理になるため取り止め。Javadoc を正本とする | TASK-032 |
| TASK-034 | 🚫 | ~~`specs/spi-data-source.md` を書き起こす~~ — 同上、Javadoc を正本とする | TASK-032 |
| TASK-035 | 🚫 | ~~`specs/spi-ai-provider.md` を書き起こす~~ — 同上、Javadoc を正本とする | TASK-032 |
| TASK-036 | ✅ | `specs/cli-commands.md` を書き起こす (CLI 各サブコマンドの引数・終了コード・出力契約) | TASK-032 |
| TASK-037 | ✅ | `specs/config-yaml.md` を書き起こす (`searchable.yaml` のスキーマ・必須項目・デフォルト) | TASK-032 |
| TASK-038 | ✅ | `specs/document-metadata.md` を書き起こす (予約キー: url・contentType・category・lang・tags) | TASK-032 |
| TASK-039 | ✅ | `specs/search-behavior.md` を書き起こす (クエリ構文・ハイブリッド戦略・スコア融合) | TASK-032 |
| TASK-040 | ✅ | Javadoc サイトを生成・公開するか方針を決定する (`maven-javadoc-plugin` 導入の要否) | - |

## タスク詳細

### TASK-001

- 補足: 「Phase 1 planning - documentation only, no source code yet」の記述と「searchable-api・searchable-mcp」モジュール記載が現状と乖離している
- 注意: docs/ の整理状況 (archives への移設) と pom.xml の `<modules>` 構成を反映する。
  TASK-027 でパス追従のみ実施済み、本タスクは Status / Architecture セクションの本格修正を扱う
- 結果 (2026-06-07): commit `6c83baa` までに CLAUDE.md は最新状態へ書き換え済み
  (`Phases 1–5 implementation is complete` 表記、`Architecture` ブロックの module 一覧が
  pom.xml の `<modules>` と一致、`examples/` 配下も最新)。
  本タスクで追加変更は不要のため 🚫

### TASK-003

- 補足: pom.xml には Kuromoji 依存のみで Sudachi 依存は存在しない
- 注意: 将来導入予定であれば README ではなく docs/devel/work/plans/project-plan.md または backlog に記載する
- 結果 (2026-06-07): README の Sudachi 言及2箇所 (Why セクション / Features 表) を削除。将来導入は BACKLOG-003 で別途管理

### TASK-004

- 補足: README の "Plain Text / Markdown / AsciiDoc / PDF / HTML" のうち AsciiDoctor 依存は pom.xml にない
- 注意: PDFBox・jsoup・POI で実際にカバーしているフォーマットに表記を寄せる
- 結果 (2026-06-07): `AsciiDocParser.java` が正規表現ベースの軽量実装として既に存在し
  `ParserRegistry.defaults()` に登録済みと判明。AsciiDoc 表記は維持し、
  漏れていた Office 6種 (docx/doc/xlsx/xls/pptx/ppt) を追加。
  BACKLOG-004 (AsciiDoc 本格実装) は前提が崩れたため取り扱い再検討の余地あり

### TASK-005

- 補足: ルート直下の searchable-{api,mcp,ui}/ は pom.xml の `<modules>` に未登録で src/ と target/ が残存している
- 注意: 削除前に git history と examples/ への移設状況を確認し、未移設のコード・設定がないことを保証する。TASK-030 (filesystem-plugin 残骸) と方針を揃える
- 結果 (2026-06-07): 3 ディレクトリすべて `git ls-files` 結果ゼロ (tracked file なし)、
  配下は `target/` `build/` と空の `src/` のみで実体ファイルゼロを確認後、
  `rm -rf searchable-api searchable-mcp searchable-ui` で物理削除。
  git 履歴・コミットへの影響なし。

### TASK-006

- 補足: 現状 README は examples の個別 pom.xml を別途 package する手順を案内している
- 注意: examples を `<modules>` に含める場合、quality・security プロファイルとの兼ね合いを検証する
- 結果 (2026-06-07): 現状維持を採用。コアビルドとサンプルのスコープを分離し、
  quality / security プロファイルもコアモジュールに限定したまま運用する。
  README の Quick Start は既に `./mvnw -f examples/api/pom.xml package` を案内しており
  追加修正は不要。`<modules>` には examples を含めない。

### TASK-007

- 補足: README 記載値「p99 = 0ms」は計測単位 (ms 丸め) かウォームアップ済みの可能性が高い
- 注意: 現状コードでの計測タイミング・ウォームアップ有無・コールド計測の有無を確認するのみとし、コード改修は TASK-008 で行う
- 結果 (2026-06-07): `docs/devel/work/poc/task-003-search-perf/.../SearchPerformanceTest.java` および
  `task-123-vector-perf/.../VectorSearchPerformanceTest.java` を点検。両者で以下が共通:
  - 計測単位: `(System.nanoTime() - start) / 1_000_000` で **ms 整数に丸めて long に格納**。
    サブミリ秒のレイテンシは 0 ms として記録されるため、README の「p99 = 0ms」は
    「p99 < 1ms」を意味するに過ぎない。
  - ウォームアップ: WARMUP_QUERIES=100 → MEASURED_QUERIES=1,000 のシリアル実行。
    **warm 計測のみで cold 計測は無い**。JIT は warmup 中にコンパイル完了している前提。
  - JMH 非使用のため fork / iteration / dead code elimination 制御は無し。
  - GC や safepoint の影響は集約しておらず、最大値・p99 への計上は最小限。
  - シングルスレッドのため、書き込み混在ワークロード (write-while-search) や
    並行検索のレイテンシは取得不能。
  - 差分: task-003 は `TextField + QueryParser`、task-123 は `KnnFloatVectorField + KnnFloatVectorQuery`
    で SHA-256 ベースのハッシュ埋め込み (実モデル非依存)。
- 判断: README の数値は「warm・整数 ms 丸め下の上限」であり「定量精度」を持たない。
  TASK-008 で JMH 化し、warm/cold・µs 精度・並行ケースを別々に出すまで、TASK-009 の
  数値更新は保留が妥当。

### TASK-009

- 補足: 旧 README は ms 整数丸めの天井値 (p99=0/1ms) を載せていた
- 注意: warm / cold の二段構成は新規読者の混乱を招かないよう列を明示する
- 結果 (2026-06-07): メイン `README.md` の Performance セクションを以下の JMH 1.37
  実測値で更新し、warm / cold 双方の数値を表に併記した。
  - Full-text (`SearchBenchmark`): warm p99 = **0.36 ms** (358 µs)、warm max = 3.0 ms、
    cold mean = **9.2 ms** (5 fork)
  - Vector HNSW (`VectorSearchBenchmark`): warm p99 = **0.26 ms** (263 µs)、
    warm max = 3.7 ms、cold mean = **7.6 ms** (3 fork)
  - REST API (TASK-034) と初期インデックス構築の行は JMH 範囲外のため凡例を維持。
    投稿元として PoC ディレクトリ (`task-003-search-perf` / `task-123-vector-perf`)
    へのリンクを追加し、旧 `investigations/` 配下は「pre-JMH の原本」として並記。
  - 投資/再現性として「3 orders of magnitude (warm) / 2 orders of magnitude (cold)」
    の言い回しに更新。bench environment 行に JMH 1.37 と計測日 (2026-06-07) を明記。
  - PoC 側 README (`task-003-search-perf/README.md`, `task-123-vector-perf/README.md`)
    も併せて JMH ベースに刷新済み。
  - `investigations/003-performance.md` / `123-vector-performance.md` は M1 当時の
    「Approved」記録のため改変せず、README 側で「original (pre-JMH) reports」として
    参照する形に統一。

### TASK-008

- 補足: JMH に置き換え warm-up・measurement・fork を明示する
- 注意: 書き込み混在ワークロード (write-while-search) を含めるかは別途判断する
- 結果 (2026-06-07):
  - `task-003-search-perf` と `task-123-vector-perf` の両 PoC を **JMH 1.37** ベースに
    全面置き換え。旧 `SearchPerformanceTest` / `VectorSearchPerformanceTest` (整数 ms 丸めの
    シングルファイル main) を削除し、それぞれ `SearchBenchmark` / `VectorSearchBenchmark`
    に差し替え。
  - 両ベンチで `warmQuery` と `coldQuery` の 2 メソッドを単一クラスに共存させた。
    - `warmQuery`: `Mode.SampleTime` + `@Warmup(5×1s)` + `@Measurement(10×5s)` + `@Fork(1)`
      → p50/p95/p99/p99.9/max を µs 解像度で取得
    - `coldQuery`: `Mode.SingleShotTime` + `@Warmup(0)` + `@Measurement(1×1batch)` +
      `@Fork(5)` (search) / `@Fork(3)` (vector) → fresh JVM 初回投入レイテンシ
  - pom.xml は Lucene 10.4.0 + JMH 1.37 + annotation processor + shade plugin
    (`benchmarks.jar` を生成) の構成で再編。`mvnw -DskipTests package` 後
    `java -jar target/benchmarks.jar` で実行できる。
  - 書き込み混在ワークロードは BACKLOG-005 維持で本タスクのスコープ外。
  - 動作検証: 両 jar で `java -jar target/benchmarks.jar -l` が warm/cold の
    2 ベンチを認識することを確認。実機 full run (Apple Silicon / Java 21.0.9) で
    `SearchBenchmark` (約 1:29) と `VectorSearchBenchmark` (約 6:26) の双方が
    正常完了し、JSON 出力 (`-rf json -rff result.json`) も生成されることを確認。
    計測値は TASK-009 で README 群に反映。

### TASK-010

- 補足: README の "in-memory Lucene-based architecture" は MMapDirectory 利用の実態と乖離している
- 注意: 「single-process・mmap-backed」など実態に即した表現に置き換える

### TASK-014

- 補足: searchable-admin は Spring Boot + Thymeleaf を引きずるため、README の "Embeddable, not infrastructure" と整合しない
- 注意: experimental 降格・別リポジトリ化・据え置き＋表現変更の3案を比較してからユーザー合意を取る
- 結果 (2026-06-07): ユーザー判断は「core (`searchable-core` 等) は確かに embeddable で、
  管理機能 (`searchable-admin`) は別物」。これを反映し:
  - Why セクションを `Embeddable core, not infrastructure` に書き換え、Spring Boot 例 / admin
    は「separate, optional artifacts that *use* the embeddable core」と明示。
  - Modules 表を「Embeddable core / Standalone tools / Reference apps」の3グループに整理し、
    `searchable-admin` は Standalone tools 側に Operator-facing として配置。
  - experimental バッジは付与せず (ユーザー回答に沿って core/管理機能を分離する方針)。

### TASK-016

- 補足: multilingual-e5 のモデルファイル (約 470MB) を JAR 同梱しない場合、取得手順とキャッシュパスをドキュメント化する必要がある
- 注意: examples/ と Java API embedded 利用の両方の経路をカバーする
- 結果 (2026-06-07): `docs/public/vector-search-guide.md` の「ONNX プロバイダ」節に
  「ONNX モデルの配布・取得・キャッシュ戦略」サブセクションを追加。
  推奨モデルとサイズ表、`huggingface-cli` / `git lfs` での取得コマンド、
  推奨キャッシュパス (`~/.cache/searchable/models/<model-id>/`)、
  Java API embedded / examples 両経路の指定方法、CI/Docker 取扱い、
  ライセンス確認の注意を明記。実装は `OnnxEmbeddingProvider` が
  `Path modelPath` を受け取るだけでダウンロード機構を持たないことを根拠としている。

### TASK-019

- 結果 (2026-06-07): `examples/mcp/src/main/java/io/searchable/example/mcp/McpServer.java:39` の
  `PROTOCOL_VERSION = "2024-11-05"` と README badge `MCP-2024--11--05` は一致しており badge 表記の
  即時修正は不要。
- 追従方針 (2026-06-07): MCP 公式仕様サイト
  (<https://modelcontextprotocol.io/specification>) を確認したところ、
  最新版は **`2025-11-25`**。`examples/mcp` を `2024-11-05` → `2025-11-25` へ
  追従させる作業は BACKLOG-006 として登録。badge は実装が `2025-11-25` に
  上がった時点で同時更新する。

### TASK-020

- 補足: 現状 examples/mcp 配下にあるが README badge は MCP プロトコルを前面に出している
- 注意: TASK-005 で旧 searchable-mcp/ を削除する判断と整合させる
- 結果 (2026-06-07): `examples/mcp` 留めを採用。MCP はクライアント連携の参照実装
  であり、core JAR への組込が前提ではない。Modules 表でも既に
  「Reference apps」グループに配置済みのため README 追加修正は不要。
  badge は MCP プロトコルバージョンの可視化が主目的のため維持。

### TASK-015

- 結果 (2026-06-07): TASK-006「現状維持」に整合させ、`examples/webapp` と
  `examples/search-ui` も examples のリファレンス位置を据え置く。
  Modules 表は本タスクで既に「Reference apps」グループへ分離済み
  (webapp = "Embedded webapp demo"、search-ui = "Static HTML / JS client")。
  追加修正は不要。

### TASK-022

- 補足: lucene-bom・jackson-bom を import すれば個別バージョン指定を集約できる
- 注意: 既存の `${lucene.version}`・`${jackson.version}` プロパティ参照箇所を BOM 化後に整理する
- 結果 (2026-06-07):
  - **Jackson BOM 化を実施**: `com.fasterxml.jackson:jackson-bom:${jackson.version}` を
    dependencyManagement に import し、`jackson-databind` / `jackson-datatype-jsr310` /
    `jackson-dataformat-yaml` の個別 `<version>` 指定を削除。Spring Boot BOM より前に
    宣言することで Jackson 2.19.0 を優先解決。`./mvnw -B -q -DskipTests -N validate` で
    解決確認済み。
  - **Lucene BOM 化は断念**: Maven Central で `org.apache.lucene:lucene-bom:10.4.0`
    の解決に失敗。Apache Lucene は公式に BOM artifact を公開していない
    (2026-06-07 時点)。`${lucene.version}` プロパティで版を集約する既存方式が
    そのまま唯一の集約手段であるため、Lucene 個別 `<version>` 指定は据え置き。
    pom.xml にも理由をコメントとして残した。

### TASK-028

- 補足: 統合後の task.md・新設 README 群・specs/ 配下を staging する
- 注意: コミット作成はユーザーが行うため、staging のみで止める
- 結果 (2026-06-07): TASK-026/032 の新設物 (`docs/devel/README.md`,
  `docs/devel/specs/`) は前セッションのコミット `3d9b530` で取り込み済み。
  本セッションの追加修正 (README.md / CLAUDE.md / pom.xml / task.md /
  specs/README.md / adr/0001-0002 / multi-tenancy-guide.md /
  vector-search-guide.md) を `git add` で staging し、ユーザーに
  コミット委譲。

### TASK-029

- 補足: `markdownlint-cli2` の実行は本セッションでは権限ルールによりブロックされている
- 注意: ユーザー手元での実行と、出た警告の追修正を想定する
- 結果 (2026-06-07): `markdownlint-cli2 "**/*.md"` を実行。本マイルストーン
  (Review-202606) で触れたファイル群 (README.md / CLAUDE.md / task.md /
  specs/README.md / adr/0001-0002 / multi-tenancy-guide.md /
  vector-search-guide.md) は 0 errors を確認。
  残る警告は別マイルストーン由来 (M1 アーカイブ `archive/m1-tasks.md`、
  M3 タスク `work/tasks/m3.md`、`requirements.md`、`searchable-cli/README.md`
  の line-length) のため、本タスクのスコープ外として据え置き。
  Review-202606 完了時の archive 移動 (前提§4) 時に M1/M3 側の
  クリーンアップを別途検討。

### TASK-030

- 補足: `examples/filesystem-plugin/` には旧 `searchable-core/` ディレクトリと `target/`
  のみが残存し、`src/` も `README.md` も `pom.xml` も存在しない
- 注意: TASK-005 (旧 searchable-api/mcp/ui の処置) と方針を揃える
- 結果 (2026-06-07): `git ls-files examples/filesystem-plugin/` は空 (tracked file ゼロ)。
  実体は `examples/filesystem-plugin/searchable-core/src/` (空ディレクトリ) と
  `examples/filesystem-plugin/target/` のみで、いずれも untracked。
  `rm -rf examples/filesystem-plugin` で TASK-005 と同タイミングで物理削除済み。

### TASK-040

- 補足: 現状 pom.xml に `maven-javadoc-plugin` の active な設定はない
- 注意: Javadoc を正本とする方針 (TASK-033〜035 取り消しの根拠) と整合するため、優先度は中以上で扱う
- 結果 (2026-06-07): 「生成のみ (ローカル)」を採用。
  - pom.xml `pluginManagement` に `maven-javadoc-plugin` を追加し
    バージョン固定と Java 21 ソース指定、`-Xdoclint:none` を設定。
    デフォルト実行 (`<execution>`) は付けず、利用者が手動で
    `./mvnw javadoc:javadoc` を実行する形に留める。
  - GitHub Pages 公開は今回は対象外。CI で site デプロイの設定も追加しない。
  - 生成手順は開発者向けの `docs/devel/specs/README.md` に追記
    (利用者向け README には載せない — 一般利用者は Javadoc 生成までは
    踏み込まないとのユーザーフィードバックを反映)。

## Backlog一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| BACKLOG-001 | ⏳ | テナント毎のリソースクォータ (index size 上限・QPS 制限) を実装する | - |
| BACKLOG-002 | ⏳ | テナント毎の at-rest 暗号化機構を設計・実装する | - |
| BACKLOG-003 | ⏳ | Sudachi 形態素解析エンジンを Analyzer プラグインとして実装する | - |
| BACKLOG-004 | ⏳ | AsciiDoc ドキュメントパーサーを実装する | - |
| BACKLOG-005 | ⏳ | 書き込み混在ワークロード (write-while-search) のベンチを追加する | - |
| BACKLOG-006 | ⏳ | examples/mcp の MCP プロトコルバージョンを 2024-11-05 → 2025-11-25 に追従する | - |

## Backlog詳細

### BACKLOG-001

- 補足: TASK-013 で制約として明示した内容のうち、コード実装を伴う部分を切り出す
- 注意: M4 以降の検討対象として project-plan.md に転記する想定

### BACKLOG-003

- 補足: TASK-003 で README から除去した Sudachi 対応を将来導入する場合の受け皿
- 注意: Lucene Analyzer SPI として実装し既存 Kuromoji 構成と切替可能にする

### BACKLOG-006

- 補足: TASK-019 で確認した MCP 公式仕様の最新版 `2025-11-25` に
  `examples/mcp` の `PROTOCOL_VERSION` を引き上げ、必要なメッセージ
  スキーマ差分 (sampling / roots / elicitation 等) を反映する作業。
- 注意: 上げる際は `McpServer.java:39` の定数と README badge
  (`![MCP Protocol](https://img.shields.io/badge/MCP-...)`) を
  同時更新し、`mcp-capabilities.yaml` のコメントも追随させる。
