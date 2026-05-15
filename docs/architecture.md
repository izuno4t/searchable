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

### 1.3 データストア（調査・選定が必要）

以下から選定（複数対応も検討）:

- H2 Database
- SQLite
- RocksDB

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

### 6.1 Mavenマルチモジュール構成

```text
searchable/
├── pom.xml                          # 親POM
├── searchable-core/                 # コアライブラリ
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   └── com/searchable/core/
│       │       ├── domain/          # ドメイン層
│       │       ├── application/     # アプリケーション層
│       │       └── infrastructure/  # インフラ層
│       └── test/java/
├── searchable-api/                  # REST API
│   ├── pom.xml
│   └── src/
│       └── main/java/
│           └── com/searchable/api/
│               └── controller/
├── searchable-mcp/                  # MCPサーバー
│   ├── pom.xml
│   └── src/
│       └── main/java/
│           └── com/searchable/mcp/
├── searchable-ui/                   # 管理UI
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   └── com/searchable/ui/
│       └── main/resources/
│           └── templates/
└── searchable-plugins/              # プラグインAPI
    ├── pom.xml
    └── src/
        └── main/java/
            └── com/searchable/plugin/
```

### 6.2 モジュール依存関係

```text
searchable-ui
    ↓ depends on
searchable-api
    ↓ depends on
searchable-core ← searchable-mcp
    ↓ depends on
searchable-plugins
```

---

## 7. デプロイメント構成

### 7.1 組み込みライブラリモード

```text
User Application
    ↓
searchable-core.jar ← 組み込み
    ↓
File System / DB
```

### 7.2 スタンドアロンサーバーモード

```text
┌─────────────────────┐
│   searchable-ui     │ ← 管理UI + REST API
│   (Spring Boot)     │
└─────────────────────┘
         ↓
┌─────────────────────┐
│  searchable-core    │
└─────────────────────┘
         ↓
┌─────────────────────┐
│  File System / DB   │
└─────────────────────┘
```

### 7.3 MCPサーバーモード

```text
AI Tool (Claude Desktop)
    ↓ MCP Protocol
┌─────────────────────┐
│  searchable-mcp     │
└─────────────────────┘
         ↓
┌─────────────────────┐
│  searchable-core    │
└─────────────────────┘
         ↓
┌─────────────────────┐
│  File System / DB   │
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

**Document Version**: 1.0
**Last Updated**: 2026-01-15
**Status**: Draft

## 改訂履歴

- v1.0 (2026-01-15): 初版作成（requirements.mdから分離）
