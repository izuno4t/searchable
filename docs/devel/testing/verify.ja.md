# Searchable - 動作検証手順

Searchable のテスト階層における **e2e レベル** の検証手順。
[Testing Guide](README.md#テスト階層) の通り、結合レベル
(`maven-failsafe-plugin` + Testcontainers) では検出できない以下の故障を
ここで捕まえる:

- packaged JAR の正しさ（`Main-Class` / shaded manifest / `spring-boot-loader`）
- 別 JVM プロセスでの起動シーケンス
- 実 JDK 21 ランタイム + ホスト OS 境界
- パッケージ済成果物を **ユーザーが受け取る状態のまま** 動かせるか

そのため本手順は **packaged JAR を別プロセスで起動し、外部 HTTP クライアントで
叩く** ことを前提とする。同 JVM 内で動く `@SpringBootTest` 系はこの層の
代替にはならない。

## 走らせるタイミング

| タイミング | 必須 |
| --- | --- |
| release タグ push 時 (release pipeline ゲート) | ✅ 必須 |
| 大きめのリファクタ後、ローカル手動 | 推奨 |
| 各 PR / main push | 不要（結合レベルでカバー） |

実行手段(Docker / ローカル Maven / CLI / 手作業 curl)に依存しない形で
「何を、どの順で、どのモジュールに対して確かめるか」を以下に定義する。

具体的なコマンド例は [getting-started.ja.md](getting-started.ja.md) と
[cli-guide.ja.md](cli-guide.ja.md)、または [examples/](../examples/) 配下の
各 README を参照する。

## 1. 前提

| 項目 | 条件 |
| --- | --- |
| Java | 21 以上 (Docker 経路では不要) |
| ディスク | 1GB 以上の空き |
| ネットワーク | TCP 8080 が空いている(REST API / Webapp 経路時) |
| Docker | Compose v2 (Docker 経路時のみ) |

## 2. 検証ステップ一覧

各ステップは「合否判定 = 観測可能な出力」で定義する。
スクリプトでも手作業でも同じ基準で判定できる。

| # | ステップ | 起動するモジュール | 目的(何を確かめるか) | 合否判定 |
| --- | --- | --- | --- | --- |
| 0 | 前提確認 | — | 実行環境がライブラリの要求を満たす | `java -version` が 21 以上、ポートが空き |
| 1 | ビルド | `searchable-core` / `searchable-plugins` / `searchable-ai` + 起動側 (`examples/api` または `examples/webapp` または `searchable-cli`) | コアと起動成果物が生成できる | エラーなく JAR またはコンテナイメージが揃う |
| 2 | 起動 | 起動側モジュール(内部で `searchable-core` を初期化) | プロセスが Ready 状態に到達 | `GET /api/v1/namespaces` が 200、または `Started Searchable…Application` ログ |
| 3 | Namespace 作成 | 起動側 → `searchable-core` (NamespaceService + 永続化層 H2/PostgreSQL) | マルチテナント(論理インデックス)の隔離と永続化が動く | 同 ID の GET で作成時の設定が返る。再作成で 409 |
| 4 | ドキュメント登録 | 起動側 → `searchable-core` (IndexingService + Lucene IndexWriter) + `searchable-ai` (Namespace が HYBRID/VECTOR の場合のみ) | 取込パイプライン(形態素解析 + Lucene 書込 + ベクトル化)が動く | 200 が返り、メタデータ取得で `documentCount >= 1` |
| 5 | 全文検索(日本語) | `searchable-core` (SearchService + Kuromoji/Sudachi Analyzer + Lucene Query) | 日本語形態素解析が組み込まれている | 登録文と**異なる活用形/分かち書き起点**のクエリでも `hits` に登録 ID が含まれる |
| 6 | ベクトル / ハイブリッド検索 | `searchable-core` (SearchService + Score Fusion) + `searchable-ai` (ONNX Runtime + multilingual-e5) | 意味検索と融合スコアが動く | 登録文と**字面が異なる類義クエリ**で `hits` に登録 ID が含まれ、スコアが非ゼロ |
| 7 | 後始末 | 起動側 → `searchable-core` (Namespace 削除 / インデックスディレクトリ削除) | データを残さず終了できる | Namespace 削除後の検索で 404、再起動時に `documentCount == 0` |

## 3. 各ステップの設計意図

### 3.1 全文(5)とベクトル(6)を別クエリで実施する

全文だけ通っても「日本語ライブラリらしさ」が検証されず、
ベクトルだけ通っても Lucene 側の実体検証にならない。
両方を **別のクエリ表現で** 実行することで、形態素解析と
埋め込みパイプラインがそれぞれ独立に効いていることを示す。

例:

- 登録文:`Searchable は日本語形態素解析に対応した全文検索ライブラリです。`
- 全文(5):クエリ `形態素解析` で動詞や助詞を含まない基本形マッチ
- ベクトル(6):クエリ `自然言語処理エンジン` で字面が無い意味マッチ

### 3.2 Namespace 作成(3)と後始末(7)を両端に置く

Namespace の作成/削除経路は **永続化層(H2 or PostgreSQL)** と
**ファイルシステム上のインデックスディレクトリ** に同時に触れるため、
最も壊れやすい統合点をカバーできる。
0〜2 が成立しても、3 と 7 が両方通って初めて「再起動可能な状態」が
保証される。

### 3.3 3〜6 が本体、0〜2 は前置き

`./mvnw -B test` でも 0〜2 相当の確認は可能。
本ガイドの主眼は 3〜6(実際に検索が返ること)であり、
スクリプト化の際もこの 4 ステップを最低限カバーすればよい。

## 4. 起動側モジュールの選び方

ステップ 1〜2 と 7 の **起動側** をどれにするかは、検証の目的で選ぶ。
3〜6 のロジックはどれを選んでも同じ `searchable-core` を呼ぶ。

| 起動側 | 経路 | 適した場面 | 追加で確認できること |
| --- | --- | --- | --- |
| `docker compose` (内部で `examples/webapp` か `examples/api` を同梱) | HTTP / GUI | ローカルに JDK を入れずに最短で試したい | コンテナ化、ボリューム永続化 |
| `examples/api` | HTTP (REST) | curl / スクリプトで自動化したい | REST レイヤ、`searchable-core` の API |
| `examples/webapp` | HTTP + ブラウザ | 人が目で見て確認したい | Thymeleaf テンプレートとの統合 |
| `searchable-cli` | プロセス起動なし | 常駐サービスを立てたくない | CLI と永続化層の直接結合 |

## 5. オプションで検証する項目

3〜6 を通したあとに加えるなら以下。コアの動作確認には必須ではない。

| 項目 | 目的 | 関係モジュール |
| --- | --- | --- |
| バックアップ / リストア | インデックスの可搬性を確認 | `searchable-core` (BackupService / RestoreService)、`searchable-cli` |
| プラグイン検出 | `ServiceLoader` 経由のプラグイン読込が動く | `searchable-plugins`、例: `examples/plugin-datasource-s3` |
| ユーザ辞書登録 | Kuromoji/Sudachi のカスタム語が反映される | `searchable-core` (DictionaryService)、`examples/api` |
| Admin UI | 運用 UI からインデックスを操作できる | `searchable-admin` |

各項目には個別の README / ガイドがあるので、必要になった時点でそちらに進む。

## 6. 終了基準

以下がすべて満たされていれば「動作検証完了」と判断する。

- ステップ 0〜2 で「壊れていない」が確認できている
- ステップ 3〜6 がそれぞれの合否判定をパスしている
- ステップ 7 のあと再起動して `documentCount == 0` に戻る

スクリプト化する場合は 0〜7 をそれぞれ別のチェック関数にし、
任意のステップ単位で再実行できるようにしておくとよい。

## 7. 自動実行 (Maven failsafe IT)

本手順は **`examples/api` の Maven failsafe IT** として実装されている。
`spring-boot-maven-plugin` が packaged JAR を別 JVM で起動し、
`EndToEndVerificationIT` が外部 HTTP クライアントから REST API を叩く構成。

```bash
# まず reactor を local .m2 に install (examples/api が searchable-core を解決するため)
./mvnw -B -DskipTests install

# e2e ゲート実行
./mvnw -B -f examples/api/pom.xml verify
```

実行内容:

| Maven フェーズ | 動作 |
| --- | --- |
| `process-test-classes` | `build-helper-maven-plugin` が空き TCP ポートを `${searchable.test.port}` に予約 |
| `pre-integration-test` | `spring-boot-maven-plugin:start` が packaged JAR を別 JVM で起動 |
| `integration-test` | `maven-failsafe-plugin` が `EndToEndVerificationIT` を実行 (HTTP 越し) |
| `post-integration-test` | `spring-boot-maven-plugin:stop` で別 JVM を graceful shutdown |
| `verify` | failsafe が結果確定 |

CI では `.github/workflows/release.yml` の **deploy 直前のゲート** として
同コマンドが走る。失敗すれば Maven Central への publish へ進まない。

---

**Document Version**: 1.0
**Last Updated**: 2026-05-17
**Status**: Phases 1–5 complete(モジュール再構成中)
