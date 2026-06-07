# TASK-002: 軽量DB選定レポート

## 1. 調査概要

- **目的**: メタデータ永続化用の軽量組み込みDBを選定する
- **対象**: H2 Database, SQLite, RocksDB
- **DBの用途**:
  - Namespaceのメタデータ・設定
  - Indexメタデータ（ドキュメント数、最終更新等）
  - ドキュメントメタデータ（遅延ロード結果に必要）
  - 検索履歴
  - 永続化が必要な構造化データ全般
- **DBの用途外**: 全文インデックスは Apache Lucene の独自フォーマット
  （`Directory`）に保存するため、本DBは対象外

## 2. 候補比較

| 観点 | H2 | SQLite | RocksDB |
| --- | --- | --- | --- |
| 種別 | RDBMS | RDBMS | K-Vストア |
| 言語 | Pure Java | C（JDBC経由JNI） | C++（JNI） |
| JDBC | 標準 | xerial-sqlite-jdbc | 非対応 |
| トランザクション | あり | あり | バッチWrite |
| クエリ | 標準SQL | 標準SQL | キー操作のみ |
| 同時アクセス | MVCC | DBレベルロック | スレッドセーフ |
| ライセンス | MPL 2.0 / EPL 1.0 | Public Domain | Apache 2.0 |
| ネイティブ依存 | なし | あり | あり |
| 配布サイズ | 小（〜3MB） | 中 | 大 |
| 学習コスト | 低 | 低 | 中〜高 |

## 3. 評価基準

| 基準 | 重み | 重要視する理由 |
| --- | --- | --- |
| Pure Javaであること | 高 | 「Javaのみで完結」要件（要求仕様2.1.2） |
| SQL利用可能性 | 高 | リレーショナルなメタデータに最適 |
| JDBC互換 | 高 | 既存ライブラリ・ORMが利用可能 |
| トランザクション | 高 | Namespace/Index同時更新の整合性 |
| 配布の容易さ | 中 | 組み込みライブラリとして配布する都合 |
| ライセンス | 高 | OSS配布可能 |
| 性能（10万件規模） | 中 | 要件500ms以内に寄与 |

## 4. 評価結果

### 4.1 採用: H2 Database

- **理由**:
  - Pure Javaのみで動作（要求仕様の「Javaのみで完結」と整合）
  - JDBCドライバ標準搭載、Spring Data JDBC等との統合が容易
  - MVStoreエンジンにより高速、トランザクション・MVCC対応
  - 配布パッケージにJARを同梱するだけで動作
  - MPL 2.0/EPL 1.0は商用配布可能なOSSライセンス
- **採用バージョン**: H2 2.3.x（Java 21対応版）

### 4.2 不採用の理由

- **SQLite**: 機能は十分だがJNI/ネイティブライブラリ依存のため
  クロスプラットフォーム配布が複雑になる
- **RocksDB**: 高速だがK-Vストアであり、リレーショナルな
  メタデータ管理に向かない。JNI依存。MVPには過剰

## 5. ベンチマーク

### 5.1 PoC概要

- 場所: `docs/devel/work/poc/task-002-h2-benchmark/`
- 内容: H2に10万件のNamespaceメタデータを挿入・検索・更新する
- 実行: `mvn -q -f docs/devel/work/poc/task-002-h2-benchmark/pom.xml compile exec:java`

### 5.2 計測項目

- INSERT 100,000件（バッチ）
- SELECT 全件（COUNTのみ）
- SELECT BY PRIMARY KEY 1,000回
- UPDATE 1,000回

### 5.3 結果

詳細は `docs/devel/work/poc/task-002-h2-benchmark/README.md` に記載。
組み込みモード・MVStore下で要件を満たす性能を確認。

## 6. 結論

| 採用項目 | 選定 | バージョン |
| --- | --- | --- |
| メタデータDB | H2 Database | 2.3.x |
| 接続モード | 組み込み（embedded） | - |
| エンジン | MVStore | - |

- Phase 1 では H2 を採用
- 接続抽象化（JDBC + Spring JDBC Template 想定）により
  将来別DBへの移行余地を残す

## 7. 参考資料

- H2 Database: <https://h2database.com/>
- SQLite JDBC: <https://github.com/xerial/sqlite-jdbc>
- RocksDB: <https://rocksdb.org/>

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Approved
