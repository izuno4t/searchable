# TASK-001: 全文検索エンジン選定レポート

## 1. 調査概要

- **目的**: 本プロジェクトで利用する全文検索エンジンと日本語形態素解析器の選定
- **対象**: Apache Lucene + 形態素解析器（Kuromoji / Sudachi）
- **判定基準**: 機能要件の充足、性能、ライセンス、保守性、コミュニティ活性度
- **PoC範囲**: 日本語サンプル文書のインデックス作成と検索（Kuromoji利用）

## 2. 候補

### 2.1 全文検索エンジン

| エンジン | 種別 | 特徴 |
| --- | --- | --- |
| Apache Lucene | 組み込みJavaライブラリ | 高機能・高速・標準的、HNSWベクトル検索も同梱 |
| Elasticsearch | サーバー型（Lucene上に構築） | Phase 1 では過剰、運用負担が大きい |
| OpenSearch | サーバー型 | 同上 |
| Tantivy (Rust) | 組み込み | Java統合に難。JNI/外部プロセスが必要 |

組み込みライブラリ要件・Java 21単一スタック要件から **Apache Lucene** が唯一の現実解。

### 2.2 日本語形態素解析器

| 形態素解析器 | Lucene統合 | 辞書 | 強み | 弱み |
| --- | --- | --- | --- | --- |
| Kuromoji | `lucene-analysis-kuromoji`（標準同梱） | IPAdic | 設定不要、軽量、十分な精度 | 新語追従はやや弱い |
| Sudachi | `sudachi-lucene` 別途追加 | SudachiDict | 高精度、辞書カスタマイズ容易、A/B/C分割粒度 | 設定が必要、辞書サイズ大 |
| MeCab (Java) | 非標準 | IPAdic等 | 軽量・実績 | JNI必要、保守状況不明 |

## 3. 評価基準

| 基準 | 重み | 備考 |
| --- | --- | --- |
| Lucene統合の容易さ | 高 | 公式モジュールが利用できるか |
| 検索精度 | 高 | 一般的な日本語文書で実用的か |
| 設定・運用負担 | 中 | デフォルトで動作するか |
| 辞書カスタマイズ性 | 中 | 同義語・固有名詞追加の容易さ |
| ライセンス | 高 | Apache 2.0等のOSSライセンス |
| メモリフットプリント | 中 | 組み込み想定のため軽量さを評価 |

## 4. 評価結果

### 4.1 全文検索エンジン: Apache Lucene

- **採用**: Apache Lucene 10.x
- **理由**:
  - 組み込みJava利用に最適、設定なしで動作
  - ベクトル検索（HNSW）も同一APIで利用可能、Phase 2移行が容易
  - BM25標準搭載、ランキングチューニングAPIが豊富
  - Apache License 2.0
  - 圧倒的な実績と活発な開発（2025年も継続的にリリース）

### 4.2 形態素解析器: Kuromoji（標準採用） + Sudachi（拡張オプション）

- **MVP採用**: Kuromoji（`lucene-analysis-kuromoji`）
- **理由**:
  - Lucene公式モジュールとして同梱、追加設定不要
  - `JapaneseAnalyzer` で標準的なトークナイズ・ストップワード・正規化を一括提供
  - 一般文書・技術文書での精度は実用十分
- **将来拡張**: Sudachi
  - 辞書カスタマイズ・分割粒度制御が必要になった段階で `JapaneseAnalyzer` を差し替え可能な設計とする
  - `Analyzer` を Namespace 設定で切り替え可能にしておけば、PoCを経て切り替え可能

## 5. PoC結果

### 5.1 PoC概要

- 場所: `poc/task-001-lucene-japanese/`
- 内容: 日本語サンプル文書 5 件を `JapaneseAnalyzer` でインデックス化し、複数クエリで検索
- 実行: `mvn -q -f poc/task-001-lucene-japanese/pom.xml compile exec:java`

### 5.2 確認した動作

- 助詞・助動詞の適切な除去（ストップワード）
- 全角・半角の正規化
- 漢字・ひらがな・カタカナ混在文の形態素分割
- BM25によるスコアリング
- ハイライト機能の動作

### 5.3 PoCログ抜粋

詳細は `poc/task-001-lucene-japanese/README.md` に記載。
Kuromoji の基本動作は全項目正常に確認済み。

## 6. 結論

| 採用項目 | 選定 | バージョン |
| --- | --- | --- |
| 全文検索エンジン | Apache Lucene | 10.2.x |
| 日本語形態素解析（MVP） | Kuromoji | Lucene同梱版 |
| 日本語形態素解析（将来） | Sudachi | 別途検討 |

- Phase 1 では `JapaneseAnalyzer`（Kuromoji）を採用
- `Analyzer` を Namespace 単位で切り替え可能な設計とし、将来 Sudachi への差し替えを容易にする
- 辞書カスタマイズ要件が出た時点で Sudachi を再評価する

## 7. 参考資料

- Apache Lucene: <https://lucene.apache.org/>
- Lucene Kuromoji Analyzer:
  <https://lucene.apache.org/core/10_2_0/analysis/kuromoji/index.html>
- Sudachi: <https://github.com/WorksApplications/Sudachi>
- Sudachi Lucene Integration:
  <https://github.com/WorksApplications/elasticsearch-sudachi>

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Approved
