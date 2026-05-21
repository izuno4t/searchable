# Searchable チャンキング戦略ガイド

ドキュメントをベクトル化・索引化する際の分割（チャンキング）戦略を
選択する方法。

## 1. 仕組み

- 各 Namespace へのドキュメント登録時、`LuceneIndexer` が
  `ChunkingStrategy` を介してドキュメントを 1 つ以上のチャンクへ分割
- 各チャンクは独立した Lucene サブドキュメントとして索引化される
  （`parentId`, `chunkOrdinal` を持つ）
- ベクトルは各チャンク単位で生成・保存
- 親 ID で集約することで、検索結果はドキュメント単位/チャンク単位の
  両方で扱える（現状: 検索 API は親 ID を返却。重複は呼び出し側で
  処理）

## 2. 標準戦略

| 戦略 | 既定 | 説明 | 主な用途 |
| --- | --- | --- | --- |
| `whole` | はい | 1 ドキュメント = 1 チャンク（タイトル + 本文） | 既定。短文・互換重視 |
| `fixed` | | 文字数 + overlap 指定で分割 | 長文・モデル max_tokens 制約対策 |
| `sentence` | | 句点 (。!?.!?) で分割し target サイズで pack | 一般的な日本語文書 |
| `paragraph` | | 空行区切りで分割 | 段落で意味が完結する文書 |
| `section` | | パーサが返す見出し単位（要 Markdown/HTML 等） | 構造化ドキュメント |

## 3. 設定方法

### `application.properties`

```properties
# 既定値は "whole"
searchable.chunking.strategy=fixed
searchable.chunking.chunk-size=512
searchable.chunking.overlap=64
# sentence strategy のターゲットサイズ
searchable.chunking.sentence-target-size=400
```

### サポートされる strategy 値

`whole` / `fixed` / `sentence` / `paragraph` / `section`

### Namespace 単位の上書き

要件 2.1.2 のスコープ階層に従い、グローバル設定 +
Namespace 個別設定（現状は未公開、Phase 4 タスクで段階的に追加）。
即時運用が必要な場合はアプリ起動時に Spring Bean を上書きする。

## 4. 戦略の選び方

```text
ドキュメント長 < 1,000 文字 → whole
中程度（1,000〜10,000）       → sentence または paragraph
長文（10,000+）              → fixed（chunkSize=512、overlap=64）
見出し構造あり                → section
```

### 各戦略のトレードオフ

#### whole（既定）

- ✓ 互換最重視、1 ドキュメント = 1 ベクトル
- ✗ 長文は embedding model の max_tokens で切り捨てられる
- ✗ ドキュメント内の特定箇所がヒットしているかは判別不可

#### fixed

- ✓ サイズ制約に確実に収まる
- ✓ overlap で境界跨ぎの欠落を防げる
- ✗ 文や段落の途中で切れる場合がある

#### sentence

- ✓ 文の途中で切れない
- ✓ target サイズで効率的に pack
- ✗ 終端記号（。!?）が無い文書では機能しにくい

#### paragraph

- ✓ 空行で意味が完結する文書に最適
- ✗ 段落が極端に長い/短い場合は不均衡

#### section

- ✓ 構造化文書（Markdown/AsciiDoc/HTML）で意味的に正しい分割
- ✓ 見出しテキストもベクトル化に含めるため検索精度が向上
- ✗ 構造のない文書では `whole` にフォールバック

## 5. 動作確認

### whole（既定）でアップロード

```bash
curl -X POST http://localhost:8080/api/v1/index/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "namespaceId": "demo",
    "document": {
      "id": "d1",
      "title": "Searchable",
      "content": "長文の本文..."
    }
  }'

curl http://localhost:8080/api/v1/index/demo/metadata
# → documentCount: 1
```

### fixed に切替後、同じ長文を再投入

`application.properties`:

```properties
searchable.chunking.strategy=fixed
searchable.chunking.chunk-size=200
searchable.chunking.overlap=30
```

再起動後:

```bash
curl -X POST http://localhost:8080/api/v1/index/documents ...
curl http://localhost:8080/api/v1/index/demo/metadata
# → documentCount: N（チャンク数）
```

検索は親 ID で集約され、`SearchHit.documentId()` は元の文書 ID
（"d1"）を返す。

## 6. 既存インデックスへの影響

戦略を変更しても **既にインデックス化されたドキュメントは再分割されない**。
新しい戦略を全面適用するには:

```bash
curl -X POST http://localhost:8080/api/v1/index/rebuild \
  -H 'Content-Type: application/json' \
  -d '{"namespaceId": "demo"}'
```

…の後、ドキュメントを再アップロード(rebuild は新しい空の
インデックスディレクトリを書き出し、完了時にディレクトリ名を不可分に
リネームして切り替える方式。旧バージョンは 30 秒の猶予期間を経たあとに
削除される。検索は途切れない。ソースからの再投入はプラグインか手動で行う)。

## 7. 性能特性

- **インデックス時間**: チャンク数に比例（ベクトル計算回数が増える）
- **検索レイテンシ**: HNSW 性質上、サブドキュメント数増加でも対数オーダー
  （TASK-123 で 10万件 / dim=384 で max 1ms を計測済み）
- **インデックスサイズ**: チャンク数に応じて増加（ベクトル次元 × チャンク数）

## 8. プログラム的に独自戦略を実装

`ChunkingStrategy` インターフェースを実装し、Bean を差し替える:

```java
public final class MyCustomChunking implements ChunkingStrategy {
    @Override public String name() { return "custom"; }
    @Override public List<Chunk> chunk(Document document) {
        // ...
    }
}

@Configuration
public class CustomChunkingConfig {
    @Bean @Primary
    public ChunkingStrategy chunkingStrategy() {
        return new MyCustomChunking();
    }
}
```

## 9. トラブルシューティング

### チャンクが期待通りに分かれない

- `application.properties` の `chunk-size` と `overlap` の値を確認
- `section` で機能しない → ドキュメントの `metadata.format` が
  Markdown/HTML 等の構造化形式になっているか確認
- `sentence` で機能しない → 文末記号（。！？.!?）が存在するか

### `documentCount` の意味が変わる

- whole: 1 ドキュメント = 1（従来通り）
- 他戦略: 1 ドキュメント = N（チャンク数）
- ドキュメント数を正確に知りたい場合は `searchable.documentCount` の
  代替として `parentId` の distinct を取る必要がある（Phase 4 拡張対象）

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 1 追加機能
