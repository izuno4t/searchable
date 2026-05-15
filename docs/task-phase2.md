# TASKS - Phase 2

## タスク一覧

| ID | ステータス | 概要 | 依存関係 |
| --- | --- | --- | --- |
| TASK-101 | ✅ | Onnx Runtime Javaライブラリ統合 | - |
| TASK-102 | ✅ | 軽量埋め込みモデル組み込みと初期化処理作成 | TASK-101 |
| TASK-103 | ✅ | テキストベクトル化処理実装 | TASK-102 |
| TASK-104 | ✅ | Lucene HNSWベクトルインデックス初期化 | - |
| TASK-105 | ✅ | ベクトルインデックス作成・更新処理実装 | TASK-103,TASK-104 |
| TASK-106 | ✅ | ベクトル検索クエリ処理実装 | TASK-104 |
| TASK-107 | ✅ | PDFパーサー実装 | - |
| TASK-108 | ✅ | HTMLパーサー実装 | - |
| TASK-109 | ⏳ | シーケンシャルハイブリッド検索実装 | TASK-106 |
| TASK-110 | ⏳ | パラレルハイブリッド検索実装 | TASK-106 |
| TASK-111 | ⏳ | 検索結果マージ処理実装 | TASK-109,TASK-110 |
| TASK-112 | ⏳ | SearchServiceにベクトル検索API追加 | TASK-106 |
| TASK-113 | ⏳ | SearchServiceにハイブリッド検索API追加 | TASK-111 |
| TASK-114 | ✅ | IndexServiceにベクトルインデックス更新機能追加 | TASK-105,TASK-107,TASK-108 |
| TASK-115 | ⏳ | ベクトル検索エンドポイント実装 | TASK-112 |
| TASK-116 | ⏳ | ハイブリッド検索エンドポイント実装 | TASK-113 |
| TASK-117 | ⏳ | MCPサーバー基盤作成 | - |
| TASK-118 | ⏳ | MCP検索ツール実装 | TASK-113,TASK-117 |
| TASK-119 | ✅ | ベクトル化処理ユニットテスト作成 | TASK-103 |
| TASK-120 | ✅ | ベクトル検索処理ユニットテスト作成 | TASK-106 |
| TASK-121 | ⏳ | ハイブリッド検索統合テスト作成 | TASK-113 |
| TASK-122 | ⏳ | MCPサーバー統合テスト作成 | TASK-118 |
| TASK-123 | ⏳ | ベクトル検索性能テスト実施と目標達成確認 | TASK-121 |
| TASK-124 | ⏳ | REST API仕様書更新（ベクトル検索追加） | TASK-116 |
| TASK-125 | ⏳ | ベクトル検索利用ガイド作成 | TASK-123 |
| TASK-126 | ⏳ | MCPサーバー利用ガイド作成 | TASK-122 |

## タスク詳細

### TASK-101

- 補足: pom.xmlへの依存関係追加とネイティブライブラリ配置
- 成果物: Onnx Runtime統合設定

### TASK-102

- 補足: 日本語対応多言語埋め込みモデル（multilingual-e5-small等）をリソースに含める
- 成果物: EmbeddingModelクラス

### TASK-103

- 補足: 日本語テキスト前処理とトークン化含む
- 成果物: TextVectorizerクラス

### TASK-104

- 補足: KnnVectorFieldとHnswGraphBuilderの設定
- 成果物: VectorIndexManagerクラス

### TASK-107

- 補足: Apache PDFBox使用
- 成果物: PdfParserクラス

### TASK-108

- 補足: Jsoup使用
- 成果物: HtmlParserクラス

### TASK-109

- 補足: 全文検索→ベクトル検索、またはその逆順
- 成果物: SequentialHybridSearchクラス

### TASK-110

- 補足: ExecutorServiceで並列実行
- 成果物: ParallelHybridSearchクラス

### TASK-117

- 補足: MCPプロトコル実装（SSE/stdio対応）
- 成果物: MCPサーバー基盤

### TASK-118

- 補足: search_documentsツール実装
- 成果物: DocumentSearchToolクラス

### TASK-123

- 補足: 10万件で500ms以内のレスポンスを確認
- 成果物: 性能測定レポート
