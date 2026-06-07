# Searchable Admin UI 利用ガイド

Searchable Admin UI（searchable-ui）の機能と操作手順。

## 1. 起動

```bash
mvn -B clean package
java -jar searchable-ui/target/searchable-ui-1.0.0-SNAPSHOT.jar
```

デフォルトでは `http://localhost:8080` で UI と REST API の両方が
提供される。

設定ファイルを上書きする場合:

```bash
java -jar searchable-ui-1.0.0-SNAPSHOT.jar \
  --spring.config.location=/path/to/application.properties
```

## 2. 画面構成

| ナビ | パス | 内容 |
| --- | --- | --- |
| Dashboard | `/` | 統計サマリ + 検索レイテンシグラフ |
| Namespaces | `/namespaces` | Namespace の一覧・作成・編集・削除 |
| Indexes | `/indexes` | インデックス状態とドキュメント管理 |
| Upload | `/documents/upload` | ファイルアップロード（自動 parser 選択） |
| Settings | `/settings` | グローバル設定（新規 Namespace 既定値） |

## 3. ダッシュボード

`GET /` で表示される。

- **Namespaces**: 登録済み Namespace 数
- **Documents**: 全 Namespace の合計ドキュメント数
- **Index Size**: 全 Namespace の合計サイズ（MB）
- **Search p95**: 直近の検索レイテンシの95パーセンタイル
- **Search Latency グラフ**: Chart.js による時系列ラインチャート
  （最大 1,024 サンプル）

検索 API (`/api/v1/search`) が呼ばれるたびにレイテンシを記録し、
ダッシュボードに反映される。

## 4. Namespace 管理

### 一覧 (`/namespaces`)

- ID をクリックすると編集画面へ遷移
- 各行に Edit / Delete ボタン
- Delete はブラウザの確認ダイアログ付き

### 新規作成 (`/namespaces/new`)

| 項目 | 必須 | 制約 |
| --- | --- | --- |
| ID | はい | `[a-z0-9][a-z0-9_-]{0,63}` |
| Name | はい | 256文字以下 |
| Architecture | いいえ | FULL_TEXT / VECTOR / HYBRID |
| Search Strategy | いいえ | SEQUENTIAL / PARALLEL |
| Search Order | いいえ | FULL_TEXT_FIRST / VECTOR_FIRST |

選択肢を空（"(global default)"）にするとグローバル設定値を使用。

### 編集 (`/namespaces/{id}/edit`)

- ID は変更不可
- Name と config を同時更新可能
- 同じ画面から削除も可能

## 5. インデックス管理

### 一覧 (`/indexes`)

各 Namespace の以下を表示:

- Documents（件数）
- Size（バイト）
- Status pill（READY/INDEXING/EMPTY/ERROR、色分け）
- Last Updated タイムスタンプ
- View / Rebuild ボタン

### 詳細 (`/indexes/{namespaceId}`)

- メトリクスカード(Documents/Size/Status/Last Updated)
- ドキュメント一覧(最大20件/ページ) — `DocumentMetadataRepository`
  ベースで取得しており、チャンク分割による重複表示は発生しない
  - ID、タイトル、indexed_at(本文スニペットは新スキーマでは非表示)
  - Delete ボタン(確認ダイアログ付き)
- ページネーション
- Rebuild ボタン

> **再構築の挙動**: 検索を停止せず切り替える方式。新しい空のインデックス
> ディレクトリを用意し、書き込み完了時にディレクトリ名を不可分に
> リネームして切り替える。旧ディレクトリは 30 秒の猶予期間を置いてから
> 削除する。再構築の実行中も検索 API は旧バージョンで結果を返し続ける。
> 詳細は [docs/devel/design/architecture/overview.md §5.7](architecture.md) を参照。

### ドキュメントアップロード (`/documents/upload`)

対応形式:

- テキスト系: `.txt`, `.text`, `.log`
- Markdown: `.md`, `.markdown`
- AsciiDoc: `.adoc`, `.asciidoc`
- HTML: `.html`, `.htm`, `.xhtml`
- PDF: `.pdf`
- Word: `.docx`, `.doc`
- Excel: `.xlsx`, `.xls`
- PowerPoint: `.pptx`, `.ppt`

最大ファイルサイズ: 64MB（`application.properties`で変更可）。

アップロード成功後は対象 Namespace の詳細画面にリダイレクトし、
flash メッセージで indexing 結果を表示する。

## 6. グローバル設定 (`/settings`)

新規に作成される Namespace のデフォルト値を設定する画面。

- Default Architecture
- Default Search Strategy
- Default Search Order

> **注意**: 既存の Namespace の設定は変更されない。各 Namespace の
> 設定変更は `/namespaces/{id}/edit` で個別に行う。

## 7. エラーページ

リソース未存在等のエラーは `templates/error.html` で表示される。

| 状況 | HTTP | 表示 |
| --- | --- | --- |
| Namespace未存在 | 404 | Not Found |
| バリデーション失敗（ID等） | 400 | Bad Request |
| 重複ID | 409 | Conflict |
| その他 | 500 | Internal Server Error |

## 8. キーボード操作

- `Tab` でフォーム要素の遷移
- フォームは標準HTML5、特殊な操作なし

## 9. トラブルシューティング

### グラフが描画されない

- ブラウザコンソールでChart.js読み込みエラーを確認
- CDN（jsdelivr.net）への接続を確認

### Bootstrap が崩れる

- ブラウザがBootstrap 5サポートバージョンか確認
  （Chrome/Firefox/Edge 最新2バージョン、Safari 13.1+）

### アップロードでサイズエラー

- `searchable-ui` の `application.properties`
- `spring.servlet.multipart.max-file-size` と `max-request-size` を増やす

### REST API も使えるか

- 同一ポート(8080)で `/api/v1/*` が利用可能
- OpenAPI 仕様は `examples/api/openapi.yaml` 参照

## 10. デモ環境

`docker/` 配下に Docker Compose ベースのデモ環境を提供。
詳細は `docs/public/demo-setup.md`。

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 3
