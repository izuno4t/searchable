# ドキュメントメタデータ仕様

Searchable がインデックス対象ドキュメントに対して予約しているメタデータキーの意味・型・正規化規則を定義する。

ユーザー定義の任意メタデータ (予約外のキー) との衝突回避規則と、Lucene への保存形式も含む。

## 1. 予約キー一覧

`Document.metadata`
(`searchable-core/src/main/java/io/searchable/core/domain/document/Document.java:21`)
で予約されているキーは以下のとおり。値はすべて `Map<String, Object>` に格納される。

| キー | 型 | 必須 | デフォルト | 意味 |
| --- | --- | --- | --- | --- |
| `url` | string (URI) | 推奨 | なし | 元ドキュメントへのオリジン参照 (RFC 3986、スキーム必須) |
| `contentType` | string (MIME type) | 推奨 | `application/octet-stream` | 元ドキュメントのフォーマット (RFC 2046 / IANA media types) |
| `category` | string | 任意 | なし | facet・絞り込み用の単一分類 |
| `lang` | string | 任意 | なし | facet・絞り込み用の言語コード |
| `tags` | string または string[] | 任意 | なし | facet・絞り込み用のタグ集合 |

予約キー以外の任意キーは、値をそのまま保存・返却する
(`searchable-core/src/main/java/io/searchable/core/domain/document/DocumentMetadataRecord.java:46` の
`metadata` フィールドは `Map<String, Object>` として読み取り専用ビューでラップされ、内容は変換されない)。

`title` および `indexedAt` は `metadata` の配下ではなく、`Document` および
`DocumentMetadataRecord` のトップレベルフィールドとして別管理される
(`Document.java:19`, `DocumentMetadataRecord.java:49,51`)。これらは予約キーには含めない。

## 2. 各予約キーの詳細仕様

### 2.1 `url`

オリジンドキュメントへの参照 URI。

- **型**: string
- **必須**: 推奨 (検索結果から元ドキュメントへの直リンクと、`SubResult.anchorUrl`
  の base URL に用いられるため)
- **形式**: RFC 3986 形式の URI で、**スキームを必須とする**
  - 許容例: `file:///abs/path/doc.md`, `https://docs.example.com/page`,
    `s3://bucket/key`, `ftp://host/path`
  - 不許容: スキームなしの生パス (`/abs/path/doc.md`,
    `./relative/path` など)。可搬性がないため、`file://` で包んで渡す
- **正規化**: ライブラリ側では URL のエスケープや正規化を行わず、与えられた
  文字列をそのまま保存・返却する
- **派生する挙動**:
  - 検索結果 (`SearchHit.metadata.url`) として返却される
  - `SubResult.anchorUrl` の base URL となり、見出しから生成した slug が
    `#` 区切りで連結される
    (`searchable-core/src/main/java/io/searchable/core/application/AnchorUrls.java:53`)
  - `url` が未設定の場合、`SubResult.anchorUrl` は `null` になる
    (`searchable-core/src/main/java/io/searchable/core/application/SearchResultEnricher.java:78`)
- **CLI 既定値**: `searchable-cli` のファイル取込では絶対パスを `file://`
  形式に変換して自動付与する
  (`searchable-cli/src/main/java/io/searchable/cli/command/IngestCommand.java:109`)

### 2.2 `contentType`

元ドキュメントの MIME type。

- **型**: string
- **必須**: 推奨
- **形式**: RFC 2046 / IANA media types に準拠した標準形 MIME type
  - 独自拡張が必要な場合は `application/vnd.searchable.xxx` 名前空間を使用する
- **デフォルト**: `application/octet-stream`
  (`searchable-core/src/main/java/io/searchable/core/domain/parser/DocumentParser.java:30`)
- **値の一覧** (組み込みパーサーが自動付与する値):

  | フォーマット | MIME type | 備考 |
  | --- | --- | --- |
  | プレーンテキスト | `text/plain` | `.txt` / `.text` / `.log` |
  | Markdown | `text/markdown` | `.md` / `.markdown` (RFC 7763) |
  | HTML | `text/html` | `.html` / `.htm` / `.xhtml` |
  | AsciiDoc | `text/asciidoc` | `.adoc` / `.asciidoc` |
  | PDF | `application/pdf` | `.pdf` |
  | Word (.docx) | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | Apache POI (XWPF) |
  | Word (.doc) | `application/msword` | Apache POI (HWPF) |
  | Excel (.xlsx) | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | Apache POI (XSSF) |
  | Excel (.xls) | `application/vnd.ms-excel` | Apache POI (HSSF) |
  | PowerPoint (.pptx) | `application/vnd.openxmlformats-officedocument.presentationml.presentation` | Apache POI (XSLF) |
  | PowerPoint (.ppt) | `application/vnd.ms-powerpoint` | Apache POI (HSLF) |
  | 不明・パーサー未登録 | `application/octet-stream` | デフォルト |

- **自動推定の振る舞い**:
  - CLI 経由の取込では `ParserRegistry.resolveForFile(fileName)`
    がファイル拡張子から `DocumentParser` を選択し、選択された
    `DocumentParser.contentType()` の値を `metadata.contentType` に書き込む
    (`searchable-cli/.../IngestCommand.java:88,111`)
  - 拡張子に対応するパーサーが無い場合は、その文書はスキップされる
    (CLI の動作)。API/Java API から `contentType` を明示的に渡した場合は
    その値が保存される

### 2.3 `category`

利用者定義の単一分類。

- **型**: string
- **必須**: 任意
- **デフォルト**: なし (キー自体を省略可能)
- **正規化**: ライブラリ側での変換 (大文字小文字、空白トリム等) は行わない
- **用途**: facet 絞り込み・ソートのための単一カテゴリ。階層分類が必要な場合は
  `tags` を併用するか、ユーザー定義キーを追加する

### 2.4 `lang`

ドキュメントの主言語を表すコード。

- **型**: string
- **必須**: 任意
- **デフォルト**: なし (キー自体を省略可能)
- **形式**: 標準としては ISO 639 (例: `ja`, `en`, `zh`) または BCP 47
  (`ja-JP`, `zh-Hant`) を推奨する。ライブラリは値の検証を行わず、
  受け取った文字列をそのまま保存・返却する
- **用途**: facet 絞り込み・言語別フィルタ

### 2.5 `tags`

ドキュメントに付与する任意のタグ集合。

- **型**: string または string[]
  - 単一タグの場合は string、複数タグの場合は string[] を許容する
- **必須**: 任意
- **デフォルト**: なし (キー自体を省略可能)
- **正規化**: ライブラリ側での重複排除・大文字小文字統一・ソートは行わない。
  与えられた配列順をそのまま保存・返却する
- **用途**: facet 絞り込み・タグクラウド表示

## 3. ユーザー定義メタデータ (予約外キー) の扱い

予約キー以外のキーはすべて利用者が自由に使用できる。

- **保存**: `metadata` マップに含めて渡せば、そのまま `DocumentMetadataRecord`
  に保存される
  (`DocumentMetadataRecord.java:66`)
- **返却**: 検索結果の `SearchHit.metadata` にそのまま含まれる
  (`SearchResultEnricher.java:77,95`)
- **衝突回避規則**:
  - 予約キー (`url` / `contentType` / `category` / `lang` / `tags`) を
    別用途で再定義することは禁止
  - ライブラリが将来予約する可能性のあるキーとの衝突を避けるため、
    アプリケーション固有のキーには名前空間プレフィックス
    (例: `app.author`, `crm.customerId`) を付けることを推奨
- **値の型**: Jackson でシリアライズ可能な値であること
  (string / number / boolean / 配列 / オブジェクト)。
  シリアライズに失敗した場合は `IllegalStateException` を送出する
  (`LuceneDocumentMapper.java:117` — チャンクメタデータ側だが同様の制約)

## 4. パーサーごとの自動付与挙動

ファイル取込時、各 `DocumentParser` 実装は固有の `format`
キーを `ParsedDocument.metadata` に付与する。これは `Document.metadata`
には自動転記されないが、パーサーが返す追加情報の一覧として記載する。
`contentType` は前述のとおり CLI 取込時に `Document.metadata.contentType`
へ書き込まれる。

| パーサー | `contentType()` 戻り値 | `ParsedDocument.metadata` に付与する追加キー |
| --- | --- | --- |
| Plain Text (`PlainTextParser.java:29`) | `text/plain` | `format=plain` |
| Markdown (`MarkdownParser.java:53`) | `text/markdown` | `format=markdown` (さらに `ParsedDocument.sections` に見出し階層を抽出) |
| HTML (`HtmlParser.java:35`) | `text/html` | `format=html`、`meta.description` (`<meta name="description">`)、`meta.charset` (`<meta charset>`) |
| AsciiDoc (`AsciiDocParser.java:44`) | `text/asciidoc` | `format=asciidoc` |
| PDF (`PdfParser.java:39`) | `application/pdf` | `format=pdf`、`pageCount`、`pdfInfo.author` / `pdfInfo.subject` / `pdfInfo.creator` (PDF 文書情報から取得可能なもののみ) |
| Office (Word/Excel/PowerPoint) (`OfficeDocumentParser.java:62`) | 拡張子ごとに別 MIME (上表参照) | `format=<name>` (例: `word-docx`, `excel-xlsx`) |
| パーサー未登録 | `application/octet-stream` (`DocumentParser.java:30` デフォルト実装) | なし |

CLI 取込 (`IngestCommand`) では、上記に加えて以下のキーが
`Document.metadata` に自動設定される
(`searchable-cli/.../IngestCommand.java:108-111`):

- `url`: 絶対パスを `file://` 形式に変換した文字列
- `path`: 絶対パス文字列 (予約外キーだが CLI 取込で自動付与する補助情報)
- `contentType`: パーサーの `contentType()` 戻り値

## 5. Lucene への保存形式

ドキュメントレベルの `metadata` は **Lucene のチャンク stored field には
保存しない**。専用のメタデータ DB (`DocumentMetadataRepository`、
`DOCUMENT_METADATA` テーブル) に `(namespaceId, documentId)` をキーとして
1 行で保存する
(`searchable-core/src/main/java/io/searchable/core/domain/document/DocumentMetadataRepository.java:24`)。

### 5.1 メタデータ DB 側

| 保存対象 | 保存先 | 主キー |
| --- | --- | --- |
| `title` / `metadata` / `indexedAt` / `source` | `DOCUMENT_METADATA` テーブル (`DocumentMetadataRepository`) | `(namespaceId, documentId)` |

検索時は `SearchResultEnricher.enrich(...)` が
`WHERE namespace_id = ? AND document_id IN (...)`
の単発バッチクエリで全ヒット分の metadata を取得し、
`SearchHit.metadata` に注入する
(`SearchResultEnricher.java:43-74`)。

### 5.2 Lucene 側

`LuceneDocumentMapper`
(`searchable-core/src/main/java/io/searchable/core/infrastructure/lucene/LuceneDocumentMapper.java:70`)
が書き出すフィールドは以下のとおり。
ドキュメントレベルの `metadata` は **意図的に書き込まない**
(`LuceneDocumentMapper.java:58-63` の Javadoc 参照)。

| Lucene フィールド名 | 型 | stored | indexed / analyzed | 用途 |
| --- | --- | --- | --- | --- |
| `id` | `StringField` | YES | indexed (not analyzed) | チャンク識別子 (`doc-1#0` 等) |
| `parentId` | `StringField` | YES | indexed (not analyzed) | 親ドキュメント ID。`Term` 検索による削除・グルーピングに使用 |
| `chunkOrdinal` | `StoredField` + `NumericDocValuesField` | YES | DocValues (ソート用) | チャンクの並び順 (0 始まり) |
| `title` | `Field` (`ANALYZED_STORED_WITH_VECTORS`) | YES | analyzed、term vectors あり | ドキュメントタイトル (ハイライト対応) |
| `content` | `Field` (`ANALYZED_STORED_WITH_VECTORS`) | YES | analyzed、term vectors あり | チャンク本文 (ハイライト対応) |
| `chunkMetadataJson` | `StoredField` | YES | not indexed | チャンク固有メタデータ (heading / level / weight 等) を JSON 文字列で保存 |
| `indexedAtEpoch` | `StoredField` + `NumericDocValuesField` | YES | DocValues (ソート用) | `Document.indexedAt` の epoch millis |
| `vector` | `KnnFloatVectorField` | (KNN) | KNN (`DOT_PRODUCT`) | チャンクの埋め込みベクトル。`embeddingProvider` が無いときは省略 |

`namespaceId` は Lucene Directory 単位で物理分割されているため、Lucene
ドキュメント側の per-chunk フィールドとしては持たない
(`docs/devel/design/architecture/overview.md` §5.7)。

### 5.3 検索結果での enrichment

検索エンジン (Lucene) は ID・スコア・チャンク固有情報のみを返し、
`SearchHit.metadata` および `SubResult.anchorUrl` はアプリケーション層
(`SearchResultEnricher`) がメタデータ DB から取得して注入する
(`SearchResultEnricher.java:18-34`)。

- `metadata.url` が存在する場合、`SubResult.anchorUrl` は
  `metadata.url + "#" + slugify(heading)` で生成される
  (`AnchorUrls.java:53`)
- `metadata.url` が存在しない場合、`SubResult.anchorUrl` は `null` になる
- `SubResult` は full-text 検索でのみ生成される。ベクトル検索経由でヒットした
  文書には付かない (`docs/devel/design/architecture/overview.md` §5.7)
