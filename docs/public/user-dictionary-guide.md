# Searchable user dictionary (custom tokenization) guide

This guide explains how to register user-defined words with the
Kuromoji morphological analyzer to customize tokenization behavior.

## 1. How it works

- When the Analyzer is built for each Namespace, `UserDictionaryResolver`
  merges the global dictionary with that Namespace's dictionary to
  construct Kuromoji's `UserDictionary`.
- For the same surface form, **a Namespace-specific entry overrides the
  global one**.
- Dictionary changes **take effect on newly created or rebuilt indexes**.
  If existing documents need to be re-tokenized, run
  `POST /api/v1/index/rebuild`.

## 2. Entry format

The entry format follows Kuromoji's `UserDictionary` format (one CSV
line with four columns).

```text
surface,segmentation,reading,pos
```

| Column | Content | Example |
| --- | --- | --- |
| `surface` | Surface form (the searchable word) | `関西国際空港` |
| `segmentation` | Tokenized word sequence (space-separated) | `関西 国際 空港` |
| `reading` | Reading (katakana, space-separated) | `カンサイ コクサイ クウコウ` |
| `pos` | Part-of-speech label (arbitrary string) | `カスタム名詞` |

All columns are required. Commas and newlines are not allowed in any
field.

## 3. Choosing the storage

`application.properties`:

```properties
# file (default) or db
searchable.dictionary.storage=file
# Root directory when using file storage
searchable.dictionary.directory=./data/dictionaries
```

### File storage layout

```text
./data/dictionaries/
├── global.csv                # Global dictionary
└── namespaces/
    ├── project-a.csv          # Namespace-specific
    └── project-b.csv
```

### DB storage

Entries are stored in the `USER_DICTIONARY` table of the H2 metadata
DB (`scope_key` PK, `name`, `entries_csv` CLOB, `updated_at`).

## 4. REST API

### List

```bash
curl http://localhost:8080/api/v1/dictionaries
```

### Register or overwrite the global dictionary

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

### Namespace-specific dictionary

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

### Get

```bash
curl http://localhost:8080/api/v1/dictionaries/global
curl http://localhost:8080/api/v1/dictionaries/namespaces/project-a
```

### Delete

```bash
curl -X DELETE http://localhost:8080/api/v1/dictionaries/global
curl -X DELETE http://localhost:8080/api/v1/dictionaries/namespaces/project-a
```

## 5. Scope and lifecycle

- **Application target**: changes take effect the next time an
  `Analyzer` is newly built after the dictionary update.
- **Existing documents**: tokens for already-indexed documents do not
  change. Use `POST /api/v1/index/rebuild` to rebuild as needed.
- **Search queries**: because the same `Analyzer` is used at search
  time, the new dictionary is applied immediately to query
  tokenization on the search side.

## 6. Behavior example

Before registering "関西国際空港" in the dictionary:

```text
"関西国際空港" → ["関西", "国際", "空港"]  (default Kuromoji)
"関西国際空港"を含む文書 → "関西" "国際" "空港" でヒット
```

After registering
`関西国際空港,関西 国際 空港,カンサイ コクサイ クウコウ,カスタム名詞`
in the dictionary:

```text
"関西国際空港" → ["関西", "国際", "空港"]  (segmentation の通り)
かつ表層形 "関西国際空港" もインデックスに残る → 完全一致検索もヒット
```

## 7. Best practices

- Register company-wide proper nouns and technical terms in the
  **global** dictionary.
- Register project-specific jargon in the **Namespace-specific**
  dictionary.
- Combining dictionary changes with an index rebuild stabilizes search
  quality.
- For long surface forms with many characters, entries that split them
  into shorter tokens are more effective.
- The `pos` label is arbitrary, but unifying it (for example as
  `カスタム名詞`) makes management easier.

## 8. Troubleshooting

### Registered words are not tokenized

- The index may predate the registration. Run
  `POST /api/v1/index/rebuild`.
- The Analyzer is cached per Namespace. Check when the Namespace was
  created after the configuration change, or when the Analyzer was
  rebuilt.

### Validation errors

- Make sure no commas or newlines have crept into any field.
- All fields are required (blank values are errors).

### Size limits

- DB storage: practical up to a few MB because the column is a CLOB.
- File storage: bounded by the filesystem's own limits.

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 1 additional feature
