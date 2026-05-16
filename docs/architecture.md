# Searchable - アーキテクチャ設計書

## 1. 技術スタック

### 1.1 コア技術

- **言語**: Java 21
- **ビルドツール**: Maven
- **ロギング**: SLF4J + Logback

### 1.2 検索エンジン（調査・選定が必要）

- **全文検索**: Apache Lucene + 日本語形態素解析器
  - Kuromoji（標準、辞書ベース）
  - Sudachi（高精度、辞書カスタマイズ可能）
- **ベクトル検索**:
  - 埋め込みモデル: Onnx Runtime + 日本語対応多言語モデル
    - 候補: multilingual-e5-small, multilingual-e5-base
    - 候補: paraphrase-multilingual-MiniLM-L12-v2
  - ベクトル検索エンジン: Lucene HNSW等から選定

### 1.3 ストレージ

Lucene インデックスとメタデータ DB はそれぞれ複数バックエンドから
選択可能(詳細は 7.1 節を参照)。

- **Lucene Directory**: ローカル FS / インメモリ / S3 互換
- **メタデータ DB**: H2 (ファイル / インメモリ) / RDB (TCP, 例 PostgreSQL)

### 1.4 UI技術

- **管理UI**: Thymeleaf（Java側にバンドル）
- **検索UI**: React（ライブラリ利用のサンプル実装、将来対応）

---

## 2. システムアーキテクチャ

### 2.1 レイヤー構成

```text
┌─────────────────────────────────────────┐
│          Presentation Layer             │
│  (REST API / Java API / MCP Server)     │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│          Application Layer              │
│  (Search Service / Index Service)       │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│           Domain Layer                  │
│  (Search Engine / Namespace Manager)    │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│       Infrastructure Layer              │
│  (DB / File System / Plugin Loader)     │
└─────────────────────────────────────────┘
```

### 2.2 レイヤー詳細

#### 2.2.1 Presentation Layer（プレゼンテーション層）

**責務**: 外部インターフェースの提供

- REST APIコントローラー
- Java API Facade
- MCPサーバーエンドポイント
- 管理UIコントローラー

**技術**:

- Spring Boot (REST API)
- Thymeleaf (管理UI)

#### 2.2.2 Application Layer（アプリケーション層）

**責務**: ビジネスロジックの調整

- SearchService: 検索処理の調整
- IndexService: インデックス管理の調整
- NamespaceService: Namespace管理の調整
- AdminService: 管理機能の調整

**パターン**:

- Service層パターン
- DTOパターン

#### 2.2.3 Domain Layer（ドメイン層）

**責務**: コアビジネスロジック

- 検索エンジン
- ベクトル検索エンジン
- ハイブリッド検索オーケストレーター
- Namespaceマネージャー
- インデックスマネージャー
- ドキュメントパーサー

**パターン**:

- ドメインモデルパターン
- Strategyパターン（検索戦略）
- Factoryパターン（パーサー生成）

#### 2.2.4 Infrastructure Layer（インフラストラクチャ層）

**責務**: 技術的基盤の提供

- データベースアクセス
- ファイルシステムアクセス
- プラグインローダー
- 設定管理
- ログ出力

**パターン**:

- Repositoryパターン
- Pluginパターン

---

## 3. 主要コンポーネント

### 3.1 検索エンジン

#### 3.1.1 全文検索エンジン

```text
FullTextSearchEngine
├─ LuceneIndexManager
├─ QueryBuilder
├─ JapaneseAnalyzer (Kuromoji/Sudachi)
│  ├─ Tokenizer（形態素解析）
│  ├─ TokenFilter（ストップワード、正規化）
│  └─ CharFilter（文字正規化）
└─ HighlightService
```

**責務**:

- Luceneインデックスの管理
- クエリの構築と実行
- 日本語形態素解析とトークン化
- 検索結果のスコアリング
- ハイライト処理（日本語テキスト対応）

#### 3.1.2 ベクトル検索エンジン

```text
VectorSearchEngine
├─ EmbeddingModel (Onnx Runtime)
│  └─ Japanese-Multilingual Model
│     (multilingual-e5-small等)
├─ VectorIndexManager (Lucene HNSW)
├─ TextVectorizer
└─ SimilarityCalculator
```

**責務**:

- 日本語テキストのベクトル化
- ベクトルインデックスの管理
- 類似度計算
- 検索結果のスコアリング

#### 3.1.3 ハイブリッド検索オーケストレーター

```text
HybridSearchOrchestrator
├─ SequentialSearchStrategy
├─ ParallelSearchStrategy
└─ ResultMerger
```

**責務**:

- 検索戦略の選択
- 複数検索エンジンの調整
- 検索結果のマージ

### 3.2 Namespace マネージャー

```text
NamespaceManager
├─ NamespaceRepository
├─ NamespaceConfigManager
└─ IndexIsolationManager
```

**責務**:

- Namespaceの作成・削除・更新
- 設定の管理（グローバル/個別）
- インデックスの論理分離

### 3.3 インデックスマネージャー

```text
IndexManager
├─ DocumentProcessor
├─ AsyncIndexUpdater
├─ PersistenceManager
└─ BackupService
```

**責務**:

- ドキュメントの登録・更新・削除
- 非同期インデックス更新
- インデックスの永続化
- バックアップ・リストア

### 3.4 プラグインローダー

```text
PluginLoader
├─ PluginScanner
├─ DynamicClassLoader
└─ PluginLifecycleManager
```

**責務**:

- プラグインのスキャンと検出
- 動的クラスロード
- プラグインのライフサイクル管理

---

## 4. データフロー

### 4.1 インデックス登録フロー

```text
Document Input
    ↓
[DocumentParser]  ← ファイル形式に応じたパーサー選択
    ↓
[Analyzer]  ← テキスト解析・トークン化
    ↓
┌─────────────┴─────────────┐
│                           │
[Full-Text Indexer]   [Vectorizer] ← Onnx Runtime
│                           │
[Lucene Index]        [Vector Index]
    ↓                       ↓
[Persistence Layer] ← ファイルまたはDB保存
```

**ステップ**:

1. ドキュメント受信
2. ファイル形式判定とパーサー選択
3. テキスト抽出と解析
4. 全文インデックス作成（Lucene）
5. ベクトル化と埋め込み（非同期）
6. ベクトルインデックス作成
7. インデックス永続化

### 4.2 検索フロー

```text
Search Query
    ↓
[Namespace Resolver]  ← Namespace特定と設定取得
    ↓
[Search Strategy Selector]  ← 検索戦略の決定
    ↓
┌─────────────┴─────────────┐
│                           │
[Full-Text Search]    [Vector Search]
│                           │
[Lucene Query]        [Vector Similarity]
    │                       │
    └─────────┬─────────────┘
              ↓
    [Result Merger]  ← スコア統合
              ↓
    [Pagination & Formatting]
              ↓
    Search Result
```

**ステップ**:

1. クエリ受信
2. Namespace解決と設定取得
3. 検索戦略の選択
   - シーケンシャル: 順次実行
   - パラレル: 並列実行
4. 各検索エンジンでの検索実行
5. 結果のマージとスコア統合
6. ページネーション適用
7. 検索結果返却

### 4.3 設定管理フロー

```text
Configuration Request
    ↓
[Global Config Loader]  ← グローバルデフォルト読み込み
    ↓
[Namespace Config Loader]  ← Namespace個別設定読み込み
    ↓
[Config Merger]  ← 設定のマージ（個別設定が優先）
    ↓
Effective Configuration
```

---

## 5. データモデル

### 5.1 Namespace

```java
class Namespace {
    String id;                    // Namespace識別子
    String name;                  // 表示名
    NamespaceConfig config;       // Namespace設定
    IndexMetadata indexMetadata;  // インデックスメタデータ
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

### 5.2 NamespaceConfig

```java
class NamespaceConfig {
    SearchArchitecture architecture;  // FULL_TEXT, VECTOR, HYBRID
    SearchStrategy searchStrategy;    // SEQUENTIAL, PARALLEL
    SearchOrder searchOrder;          // FULL_TEXT_FIRST, VECTOR_FIRST
    EmbeddingConfig embeddingConfig;  // 埋め込み設定
    AIConfig aiConfig;                // AI統合設定（オプション）
    Map<String, Object> customParams; // カスタムパラメータ
}
```

### 5.3 Document

```java
class Document {
    String id;                        // ドキュメント識別子
    String namespaceId;               // 所属Namespace
    String title;                     // タイトル
    String content;                   // 本文
    Map<String, Object> metadata;     // メタデータ
    DocumentSource source;            // ソース情報
    LocalDateTime indexedAt;
}
```

### 5.4 SearchRequest

```java
class SearchRequest {
    String query;                     // 検索クエリ
    List<String> namespaceIds;        // 検索対象Namespace
    SearchOptions options;            // 検索オプション
    PaginationParams pagination;      // ページネーション
}
```

### 5.5 SearchResult

```java
class SearchResult {
    List<SearchHit> hits;             // 検索結果
    long totalHits;                   // 総ヒット数
    double maxScore;                  // 最大スコア
    Map<String, Object> aggregations; // 集計結果
    long took;                        // 検索時間（ms）
}
```

### 5.6 IndexMetadata

```java
class IndexMetadata {
    long documentCount;               // ドキュメント数
    long indexSize;                   // インデックスサイズ（bytes）
    LocalDateTime lastUpdated;        // 最終更新日時
    IndexStatus status;               // インデックス状態
    Map<String, Object> statistics;   // 統計情報
}
```

---

## 6. モジュール構成

### 6.1 モジュール一覧と役割

| カテゴリ | モジュール | 役割 |
| --- | --- | --- |
| ライブラリ | `searchable-plugins` | プラグイン SPI。データソース等の拡張インターフェース定義 |
| ライブラリ | `searchable-core` | インデクシング、検索、結果のドキュメント参照を提供するコア |
| ライブラリ | `searchable-ai` | 検索後処理(要約・統合)の AI クライアント抽象。OpenAI / Anthropic / Ollama などを切替可能な API として提供 |
| 運用ツール | `searchable-cli` | インデックス管理コマンド全般(取込・削除・再構築・バックアップ/リストア・状態確認) |
| 運用 Web | `searchable-admin` | 設定・運用 Web アプリケーション(Thymeleaf)。Namespace / ユーザー辞書 / ランキング / AI 統合 / バックアップ / モニタリングの設定と操作 |
| サンプル | `examples/api` | REST WebAPI サンプル(API Key 認証対応、軽度本番利用可) |
| サンプル | `examples/mcp` | MCP サンプル(API Key 認証対応、軽度本番利用可) |
| サンプル | `examples/search-ui` | 検索 UI サンプル(React、デバウンス検索・ファセット・ハイライト) |
| 開発支援 | `searchable-testkit` | テスト共通基盤。core / plugins / ai / ui / cli を対象としたフィクスチャ・Fake・Testcontainers ヘルパ |

### 6.2 Mavenマルチモジュール構成

```text
searchable/
├── pom.xml                          # 親POM
├── searchable-plugins/              # プラグイン SPI
│   └── src/main/java/com/searchable/plugin/
├── searchable-core/                 # コアライブラリ
│   └── src/main/java/com/searchable/core/
│       ├── domain/
│       ├── application/
│       └── infrastructure/
├── searchable-ai/                   # AI 要約・統合
│   └── src/main/java/com/searchable/ai/
├── searchable-testkit/              # テスト共通基盤(test scope)
│   └── src/main/java/com/searchable/testkit/
├── searchable-cli/                  # CLI
│   └── src/main/java/com/searchable/cli/
├── searchable-admin/                   # 設定・運用 Web(Thymeleaf)
│   ├── src/main/java/com/searchable/ui/
│   └── src/main/resources/templates/
├── examples/api/                  # REST WebAPI サンプル
│   └── src/main/java/com/searchable/api/
├── examples/mcp/                  # MCP サンプル
│   └── src/main/java/com/searchable/mcp/
└── examples/search-ui/            # 検索 UI サンプル
    └── src/main/java/com/searchable/searchui/
```

### 6.3 モジュール依存関係

```text
searchable-admin  ─────────────┐
examples/api ─────────────┤
examples/mcp ─────────────┤
searchable-cli ─────────────┼─▶ searchable-core ─▶ searchable-plugins
examples/search-ui ───────┤            ▲
searchable-ai ──────────────┘            │
                                          │
searchable-testkit (test scope) ─────────┘
   ▲
   └── api / mcp / ui / cli / ai が test scope で依存
```

- `searchable-ai` は core と独立しており、利用側 (`examples/api`, `examples/mcp`, `searchable-admin` 等) が必要に応じて依存
- `examples/search-ui` は HTTP 越しに `examples/api` を呼ぶ前提で、ビルド時の Maven 依存は持たない(または最小)

---

## 7. デプロイメント構成

### 7.1 ストレージレイヤー (共通前提)

ドキュメントとインデックスデータは **抽象化されたストレージ** に配置し、
複数のバックエンドから選択できる。バックエンドは2系統に分かれる。

#### Lucene インデックスのバックエンド (`Directory` 抽象)

| バックエンド | 用途 | 備考 |
| --- | --- | --- |
| ローカルファイルシステム | 既定、開発・小規模運用 | `FSDirectory` |
| インメモリ | テスト、超高速の使い捨て | `ByteBuffersDirectory` |
| オブジェクトストレージ (S3 互換) | 本番、複数プロセス共有運用 | カスタム Directory 実装 |

#### メタデータ DB のバックエンド (JDBC 抽象)

| バックエンド | 用途 | 接続 |
| --- | --- | --- |
| H2 組み込み(ファイル) | 既定、単一プロセス | JDBC ファイル URL |
| H2 インメモリ | テスト、使い捨て | JDBC `jdbc:h2:mem:` |
| H2 サーバー / PostgreSQL / MySQL | 複数プロセス共有・本番 | **TCP 経由 JDBC** |

書込は単一プロセス(インデクサー)のみが行い、参照系モジュール
(`examples/api` / `examples/mcp` / `examples/search-ui` 経由) は
同じストレージを **読み込み専用** で参照する。これにより1セットの
インデックスを複数の参照プロセスで共有できる。

#### 書込プロセスの一意性制約

Lucene の `IndexWriter` は **Namespace ごとのインデックスディレクトリ単位** で
`write.lock` を取得する。したがって書込の単一性も Namespace 単位で成立する。

- **同一 Namespace に対してインデックス更新を行えるプロセスは常に1つだけ**
- **異なる Namespace なら複数プロセスから並列に更新可能**
  - 例: Namespace A は `searchable-cli` が、Namespace B は
    `searchable-admin` が同時に更新できる
- 同一プロセス内で同一 Namespace への更新要求が複数来た場合は
  ライブラリ側でシリアライズ(`IndexWriter` のスレッドセーフ保証)
- 設定・運用 Web を 2 ノード起動して同じ Namespace を更新することは不可
  (書込ロックの取り合いになる)。Namespace を分けるかリーダー選出を行う
- 参照系プロセスは何台でも並列起動可(全 Namespace 横断で読込可能)

```text
            ┌─────────────────────────────────────────────────┐
            │ Storage (バックエンドを選択可)                    │
            │  - Lucene index: FS / Memory / S3 互換           │
            │  - Metadata DB : H2 file / H2 mem / RDB (TCP)    │
            │  - 元ドキュメント: FS / S3 互換                    │
            └─────────────────────────────────────────────────┘
                  ▲ (write 1 / read N)
```

組み合わせ例:

- **開発**: ローカル FS + H2 ファイル
- **テスト**: インメモリ Directory + H2 インメモリ
- **本番(複数プロセス共有)**: S3 互換 + PostgreSQL (TCP)

### 7.2 組み込みライブラリモード

単一プロセスで完結するパターン。書込と参照を同じプロセスが担う。

```text
User Application
    ↓
searchable-core.jar (組み込み)
    ↓
Storage (Local FS or S3)
```

### 7.3 スタンドアロンサーバーモード(参照分離)

書込専用プロセス(`searchable-cli` または `searchable-admin`)と、
参照専用プロセス(`examples/api` / `examples/mcp` /
`examples/search-ui`) を分離。ストレージは全プロセスで共有。

```text
[書込側]                             [参照側]
┌──────────────────┐                 ┌──────────────────┐ ┌──────────────────┐
│ searchable-cli   │                 │ examples/api   │ │ examples/mcp   │
│ (バッチ取込)      │                 │ (REST sample)    │ │ (MCP sample)     │
└────────┬─────────┘                 └────────┬─────────┘ └────────┬─────────┘
         │                                    │                    │
┌────────▼─────────┐                          │                    │
│ searchable-admin    │                          │                    │
│ (設定・運用 Web)  │                          │                    │
└────────┬─────────┘                          │                    │
         │ writer 1                           │ reader N           │
         ▼                                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│        Storage (Local FS / S3 互換) — write 1 / read N                  │
└─────────────────────────────────────────────────────────────────────────┘

(任意) searchable-ai は要約・統合 API を提供。参照側から HTTP 等で呼び出す
```

### 7.4 MCP サーバーモード

AI クライアント (Claude Desktop 等) から直接読みに来る構成。
書込は別系統 (CLI / 設定 Web) で行い、MCP は参照のみ。

```text
AI Tool (Claude Desktop)
     ↓ MCP Protocol
┌─────────────────────┐
│  examples/mcp     │ ← 参照専用
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│  searchable-core    │ (read-only モード)
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│ Storage (FS / S3)   │
└─────────────────────┘
```

---

## 8. 設計パターン

### 8.1 採用パターン

#### Strategy Pattern

- **用途**: 検索戦略の切り替え
- **実装**: SequentialSearchStrategy, ParallelSearchStrategy

#### Factory Pattern

- **用途**: パーサーの生成
- **実装**: DocumentParserFactory

#### Repository Pattern

- **用途**: データアクセスの抽象化
- **実装**: NamespaceRepository, IndexRepository

#### Plugin Pattern

- **用途**: 機能の動的拡張
- **実装**: DataSourcePlugin

#### Facade Pattern

- **用途**: Java APIの単純化
- **実装**: SearchableLibrary（メインAPI）

---

**Document Version**: 2.0
**Last Updated**: 2026-05-16
**Status**: Draft

## 改訂履歴

- v2.0 (2026-05-16): モジュール構成見直し
  (searchable-ai / searchable-cli / examples/search-ui を追加、
  searchable-admin を設定・運用 Web として位置付け再定義)。
  ストレージ抽象を 7.1 節に明文化し、Lucene Directory バックエンド
  (FS/メモリ/S3) とメタデータ DB バックエンド (H2/RDB-TCP) の選択肢を
  追加。書込1+参照Nプロセスのデプロイパターンを 7.3 に追加。
- v1.0 (2026-01-15): 初版作成（requirements.mdから分離）
