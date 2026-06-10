# Searchable ユーザー辞書（カスタム分かち書き）ガイド

Kuromoji 形態素解析にユーザー定義の単語を登録して、分かち書き挙動を
カスタマイズする方法を説明します。

## 1. 仕組み

- 各 Namespace の Analyzer 生成時に `UserDictionaryResolver` が
  グローバル辞書 + その Namespace の辞書をマージして Kuromoji の
  `UserDictionary` を構築します
- 同一の表層形は **Namespace 個別エントリがグローバルを上書きします**
- 辞書変更後は **新規インデックス・再構築時に反映されます**（既存ドキュメントの
  再分かち書きが必要な場合は `POST /api/v1/index/rebuild` を実行してください）

## 2. エントリのフォーマット

Kuromoji の `UserDictionary` 形式（CSV 1 行に 4 列）です。

```text
surface,segmentation,reading,pos
```

| 列 | 内容 | 例 |
| --- | --- | --- |
| `surface` | 表層形（検索対象の単語） | `関西国際空港` |
| `segmentation` | 分かち書きされた単語列（空白区切り） | `関西 国際 空港` |
| `reading` | 読み（カタカナ、空白区切り） | `カンサイ コクサイ クウコウ` |
| `pos` | 品詞ラベル（任意の文字列） | `カスタム名詞` |

全カラムが必須です。カンマや改行を含めることはできません。

## 3. ストレージの選択

`application.properties`:

```properties
# file（デフォルト）または db
searchable.dictionary.storage=file
# file storage を使う場合のルートディレクトリ
searchable.dictionary.directory=./data/dictionaries
```

### file ストレージのレイアウト

```text
./data/dictionaries/
├── global.csv                # グローバル辞書
└── namespaces/
    ├── project-a.csv          # Namespace 個別
    └── project-b.csv
```

### db ストレージ

H2 メタデータ DB の `USER_DICTIONARY` テーブルに保存されます
（scope_key PK、name、entries_csv CLOB、updated_at）。

## 4. REST API

### 一覧取得

```bash
curl http://localhost:8080/api/v1/dictionaries
```

### グローバル辞書の登録/上書き

```bash
curl -X PUT http://localhost:8080/api/v1/dictionaries/global \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "全社共通辞書",
    "entries": [
      {
        "surface": "関西国際空港",
        "segmentation": "関西 国際 空港",
        "reading": "カンサイ コクサイ クウコウ",
        "pos": "カスタム名詞"
      },
      {
        "surface": "朝青龍",
        "segmentation": "朝青龍",
        "reading": "アサショウリュウ",
        "pos": "カスタム名詞"
      }
    ]
  }'
```

### Namespace 個別辞書

```bash
curl -X PUT http://localhost:8080/api/v1/dictionaries/namespaces/project-a \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Project A 専用",
    "entries": [
      {
        "surface": "社内用語ABC",
        "segmentation": "社内 用語 ABC",
        "reading": "シャナイ ヨウゴ エービーシー",
        "pos": "カスタム名詞"
      }
    ]
  }'
```

### 取得

```bash
curl http://localhost:8080/api/v1/dictionaries/global
curl http://localhost:8080/api/v1/dictionaries/namespaces/project-a
```

### 削除

```bash
curl -X DELETE http://localhost:8080/api/v1/dictionaries/global
curl -X DELETE http://localhost:8080/api/v1/dictionaries/namespaces/project-a
```

## 5. 適用範囲とライフサイクル

- **適用対象**: 辞書変更後に `Analyzer` を新規生成するタイミングで反映されます
- **既存ドキュメント**: 既に索引化された文書のトークンは変化しません。
  必要に応じて `POST /api/v1/index/rebuild` で再構築してください
- **検索クエリ**: 検索時にも同じ `Analyzer` が使われるため、新しい
  辞書は即時に検索側のクエリ分かち書きに反映されます

## 6. 動作例

「関西国際空港」を辞書に登録する前:

```text
"関西国際空港" → ["関西", "国際", "空港"]  (default Kuromoji)
"関西国際空港"を含む文書 → "関西" "国際" "空港" でヒット
```

辞書に `関西国際空港,関西 国際 空港,カンサイ コクサイ クウコウ,カスタム名詞`
を登録した後:

```text
"関西国際空港" → ["関西", "国際", "空港"]  (segmentation の通り)
かつ表層形 "関西国際空港" もインデックスに残る → 完全一致検索もヒット
```

## 7. ベストプラクティス

- 全社共通の固有名詞・専門用語は **グローバル** に登録します
- プロジェクト固有のジャーゴンは **Namespace 個別** に登録します
- 辞書変更後はインデックス再構築を併用すると検索精度が安定します
- 文字数の多い長い表層形は、短く分割するエントリを使うほど効果的です
- `pos` ラベルは任意ですが、`カスタム名詞` 等で統一しておくと管理が
  しやすくなります

## 8. トラブルシューティング

### 登録した単語が分かち書きされない

- インデックスが登録前のものである可能性があります。`POST /api/v1/index/rebuild`
  を実施してください
- Analyzer は Namespace 単位でキャッシュされています。設定後の Namespace
  作成 or Analyzer 再生成タイミングを確認してください

### バリデーションエラー

- 各フィールドにカンマ・改行が混入していないかを確認してください
- すべてのフィールドが必須です（空白はエラーになります）

### サイズ上限

- DB ストレージ: CLOB のため数 MB 単位まで実用的です
- file ストレージ: ファイルシステム制限によります

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 1 追加機能
