# TASKS

マイルストーン: Review-202606
ゴール: 2026-06 の Claude レビュー指摘 (P0-P3) への対応と、それから派生したドキュメント管理体系 (ガイドライン v1.0) 再編・仕様整備を完遂する

## 前提

- 本タスクは 2026-06-07 のレビュー受領内容を起点とし、事前の事実確認で確定した不整合の解消、および対応中に発見した派生作業 (docs 再編・specs 整備・CLAUDE.md 最新化) を統合管理する
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
| TASK-001 | ⏳ | CLAUDE.md の "no source code yet" 記述と旧モジュール構成を最新の実装状態に書き直す | - |
| TASK-002 | ⏳ | README の Lucene バージョン表記を pom.xml の 10.4.0 に統一する | - |
| TASK-003 | ⏳ | README の "Sudachi" 言及を pom.xml の依存状況に合わせて整理する | - |
| TASK-004 | ⏳ | README の "AsciiDoc" を含む対応フォーマット一覧を実装状況に合わせて修正する | - |
| TASK-005 | ⏳ | ルート直下の旧 searchable-api/mcp/ui ディレクトリの扱いを決定し処置する | - |
| TASK-006 | ⏳ | pom.xml の `<modules>` に examples/ 配下を登録するかの方針を決定し反映する | TASK-005 |
| TASK-007 | ⏳ | ベンチコード (task-003/task-123) の計測単位と warm/cold 区分の現状を点検しレポートする | - |
| TASK-008 | ⏳ | ベンチコードを JMH ベースに置き換え warm/cold 両方の数値を出力する | TASK-007 |
| TASK-009 | ⏳ | README Performance セクションの数値表記を JMH 出力に基づき更新する | TASK-008 |
| TASK-010 | ⏳ | README の "in-memory" 表現を mmap 実態に合わせて修正する | - |
| TASK-011 | ⏳ | README の "Embeddable, not infrastructure" 表現を Spring Boot 依存実態に合わせて緩和する | - |
| TASK-012 | ⏳ | README の "Multi-tenant by design" 表現を JVM 内論理分離の実態に合わせて緩和する | - |
| TASK-013 | ⏳ | Multi-tenant の制約 (OOM・ノイジーネイバー・QoS・暗号化) を docs に明記する | TASK-012 |
| TASK-014 | ⏳ | searchable-admin の embeddable 性との整合方針を決定し README/docs に反映する | TASK-011 |
| TASK-015 | ⏳ | examples/webapp と examples/search-ui の位置付け方針を決定し README に反映する | TASK-006 |
| TASK-016 | ⏳ | ONNX モデル (multilingual-e5) の配布・取得・キャッシュ戦略を docs/public/vector-search-guide.md に追記する | - |
| TASK-017 | ⏳ | HNSW パラメーター (M・efConstruction・efSearch) のチューニング指針を docs/public/vector-search-guide.md に追記する | - |
| TASK-018 | ⏳ | ベクトル初期インデックス構築 (88s/100k) の再現条件 (CPU・並列度・バッチサイズ) を docs に明記する | TASK-007 |
| TASK-019 | ⏳ | README の MCP プロトコルバージョン badge と最新仕様追従方針を更新する | - |
| TASK-020 | ⏳ | MCP server を searchable-mcp モジュールとして昇格するか examples 維持かを決定し反映する | TASK-005,TASK-006 |
| TASK-021 | ⏳ | プラグイン API で差し替え可能な拡張点 (DataSource・Analyzer・Embedder ほか) を README に明示する | - |
| TASK-022 | ⏳ | pom.xml の Lucene/Jackson 依存を BOM import に置き換えるかを判断し反映する | - |
| TASK-023 | ⏳ | CI で検証する JDK バージョン一覧を README に明記する | - |
| TASK-024 | ✅ | ガイドライン v1.0 準拠のディレクトリ構成へ docs/ 配下を全面移行する | - |
| TASK-025 | ✅ | 旧パスへの参照を新パスへ書き換える (README・CLAUDE.md・examples・Java コメントほか) | TASK-024 |
| TASK-026 | ✅ | 入口 README を新設する (`docs/README.md` / `docs/devel/README.md` / `docs/devel/design/README.md`) | TASK-024 |
| TASK-027 | ✅ | CLAUDE.md を実装現状 (Pre-1.0・実モジュール構成・実依存) に最新化する | TASK-025 |
| TASK-028 | ⏳ | 新設した入口 README と統合 task.md・specs/ 配下を `git add` で staging する | TASK-026,TASK-032 |
| TASK-029 | ⏳ | 再編後のファイル全体を markdownlint-cli2 で検証し、警告を解消する | TASK-024,TASK-026 |
| TASK-030 | ⏳ | `examples/filesystem-plugin/` の壊れた残骸 (src なし・README なし・pom なし) の処置を決定し実施する | - |
| TASK-031 | ✅ | カレントタスク識別ルール (「`work/tasks/` 配下 = 進行中」「`task.md` が常設の最優先」) を `docs/devel/README.md` に追記する | TASK-026 |
| TASK-032 | ✅ | `docs/devel/specs/` を新設し、仕様の所在マップ README を整備する | TASK-024 |
| TASK-033 | 🚫 | ~~`specs/java-api.md` を書き起こす~~ — Javadoc と二重管理になるため取り止め。Javadoc を正本とする | TASK-032 |
| TASK-034 | 🚫 | ~~`specs/spi-data-source.md` を書き起こす~~ — 同上、Javadoc を正本とする | TASK-032 |
| TASK-035 | 🚫 | ~~`specs/spi-ai-provider.md` を書き起こす~~ — 同上、Javadoc を正本とする | TASK-032 |
| TASK-036 | ✅ | `specs/cli-commands.md` を書き起こす (CLI 各サブコマンドの引数・終了コード・出力契約) | TASK-032 |
| TASK-037 | ✅ | `specs/config-yaml.md` を書き起こす (`searchable.yaml` のスキーマ・必須項目・デフォルト) | TASK-032 |
| TASK-038 | ✅ | `specs/document-metadata.md` を書き起こす (予約キー: url・contentType・category・lang・tags) | TASK-032 |
| TASK-039 | ✅ | `specs/search-behavior.md` を書き起こす (クエリ構文・ハイブリッド戦略・スコア融合) | TASK-032 |
| TASK-040 | ⏳ | Javadoc サイトを生成・公開するか方針を決定する (`maven-javadoc-plugin` 導入の要否) | - |

## タスク詳細

### TASK-001

- 補足: 「Phase 1 planning - documentation only, no source code yet」の記述と「searchable-api・searchable-mcp」モジュール記載が現状と乖離している
- 注意: docs/ の整理状況 (archives への移設) と pom.xml の `<modules>` 構成を反映する。TASK-027 でパス追従のみ実施済み、本タスクは Status / Architecture セクションの本格修正を扱う

### TASK-003

- 補足: pom.xml には Kuromoji 依存のみで Sudachi 依存は存在しない
- 注意: 将来導入予定であれば README ではなく docs/devel/work/plans/project-plan.md または backlog に記載する

### TASK-004

- 補足: README の "Plain Text / Markdown / AsciiDoc / PDF / HTML" のうち AsciiDoctor 依存は pom.xml にない
- 注意: PDFBox・jsoup・POI で実際にカバーしているフォーマットに表記を寄せる

### TASK-005

- 補足: ルート直下の searchable-{api,mcp,ui}/ は pom.xml の `<modules>` に未登録で src/ と target/ が残存している
- 注意: 削除前に git history と examples/ への移設状況を確認し、未移設のコード・設定がないことを保証する。TASK-030 (filesystem-plugin 残骸) と方針を揃える

### TASK-006

- 補足: 現状 README は examples の個別 pom.xml を別途 package する手順を案内している
- 注意: examples を `<modules>` に含める場合、quality・security プロファイルとの兼ね合いを検証する

### TASK-007

- 補足: README 記載値「p99 = 0ms」は計測単位 (ms 丸め) かウォームアップ済みの可能性が高い
- 注意: 現状コードでの計測タイミング・ウォームアップ有無・コールド計測の有無を確認するのみとし、コード改修は TASK-008 で行う

### TASK-008

- 補足: JMH に置き換え warm-up・measurement・fork を明示する
- 注意: 書き込み混在ワークロード (write-while-search) を含めるかは別途判断する

### TASK-010

- 補足: README の "in-memory Lucene-based architecture" は MMapDirectory 利用の実態と乖離している
- 注意: 「single-process・mmap-backed」など実態に即した表現に置き換える

### TASK-014

- 補足: searchable-admin は Spring Boot + Thymeleaf を引きずるため、README の "Embeddable, not infrastructure" と整合しない
- 注意: experimental 降格・別リポジトリ化・据え置き＋表現変更の3案を比較してからユーザー合意を取る

### TASK-016

- 補足: multilingual-e5 のモデルファイル (約 470MB) を JAR 同梱しない場合、取得手順とキャッシュパスをドキュメント化する必要がある
- 注意: examples/ と Java API embedded 利用の両方の経路をカバーする

### TASK-020

- 補足: 現状 examples/mcp 配下にあるが README badge は MCP プロトコルを前面に出している
- 注意: TASK-005 で旧 searchable-mcp/ を削除する判断と整合させる

### TASK-022

- 補足: lucene-bom・jackson-bom を import すれば個別バージョン指定を集約できる
- 注意: 既存の `${lucene.version}`・`${jackson.version}` プロパティ参照箇所を BOM 化後に整理する

### TASK-028

- 補足: 統合後の task.md・新設 README 群・specs/ 配下を staging する
- 注意: コミット作成はユーザーが行うため、staging のみで止める

### TASK-029

- 補足: `markdownlint-cli2` の実行は本セッションでは権限ルールによりブロックされている
- 注意: ユーザー手元での実行と、出た警告の追修正を想定する

### TASK-030

- 補足: `examples/filesystem-plugin/` には旧 `searchable-core/` ディレクトリと `target/` のみが残存し、`src/` も `README.md` も `pom.xml` も存在しない
- 注意: TASK-005 (旧 searchable-api/mcp/ui の処置) と方針を揃える

### TASK-040

- 補足: 現状 pom.xml に `maven-javadoc-plugin` の active な設定はない
- 注意: Javadoc を正本とする方針 (TASK-033〜035 取り消しの根拠) と整合するため、優先度は中以上で扱う

## Backlog一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| BACKLOG-001 | ⏳ | テナント毎のリソースクォータ (index size 上限・QPS 制限) を実装する | - |
| BACKLOG-002 | ⏳ | テナント毎の at-rest 暗号化機構を設計・実装する | - |
| BACKLOG-003 | ⏳ | Sudachi 形態素解析エンジンを Analyzer プラグインとして実装する | - |
| BACKLOG-004 | ⏳ | AsciiDoc ドキュメントパーサーを実装する | - |
| BACKLOG-005 | ⏳ | 書き込み混在ワークロード (write-while-search) のベンチを追加する | - |

## Backlog詳細

### BACKLOG-001

- 補足: TASK-013 で制約として明示した内容のうち、コード実装を伴う部分を切り出す
- 注意: M4 以降の検討対象として project-plan.md に転記する想定

### BACKLOG-003

- 補足: TASK-003 で README から除去した Sudachi 対応を将来導入する場合の受け皿
- 注意: Lucene Analyzer SPI として実装し既存 Kuromoji 構成と切替可能にする
