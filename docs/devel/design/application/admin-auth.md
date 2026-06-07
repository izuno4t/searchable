# searchable-admin 権限管理設計

ステータス: **論理設計のみ(MVP では実装しない)**

要件 2.2.3「権限管理(設計のみ、実装は将来)」と TASK-112 を満たすための
将来実装に向けた論理設計をまとめる。MVP 期間中は API Key 認証
(TASK-126/143)のみで保護し、本ドキュメントで定義したロール/権限は
実装しない。

## 1. スコープ

- 対象: `searchable-admin`(設定・運用 Web)
- 範囲外: `examples/api`(API Key 認証)、CLI、`searchable-core`(認可フック)
- 想定ユーザー: 社内運用担当者・データオーナー・サポート要員

## 2. ロール定義

| ロール | 主な権限 | 想定担当 |
| --- | --- | --- |
| `ADMIN` | グローバル設定、Namespace の作成・削除、辞書のグローバル編集、バックアップ操作 | 運用責任者 |
| `EDITOR` | 担当 Namespace の設定/辞書編集、再構築、ドキュメント追加 | データオーナー |
| `VIEWER` | 検索、状態確認のみ。書込操作は禁止 | サポート/閲覧者 |
| `SERVICE` | API キー経由の機械アクセス(`examples/api`/`examples/mcp`) | システム |

ロール間は単純な「ADMIN ⊃ EDITOR ⊃ VIEWER」の階層を想定し、
継承される。

## 3. 認証方式

- **形式認証**: フォーム認証 + セッション Cookie(Spring Security 標準)
- **二要素**: 将来検討(MVP では計画外)
- **シングルサインオン**: OIDC/SAML 連携を想定するが MVP 外

## 4. データモデル(将来追加するテーブル)

```sql
CREATE TABLE PRINCIPAL (
    ID            VARCHAR(64) PRIMARY KEY,
    USERNAME      VARCHAR(128) NOT NULL UNIQUE,
    PASSWORD_HASH VARCHAR(255) NOT NULL,
    DISPLAY_NAME  VARCHAR(255),
    ENABLED       BOOLEAN NOT NULL DEFAULT TRUE,
    CREATED_AT    TIMESTAMP WITH TIME ZONE NOT NULL,
    UPDATED_AT    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE PRINCIPAL_ROLE (
    PRINCIPAL_ID  VARCHAR(64) NOT NULL,
    ROLE          VARCHAR(32) NOT NULL,
    SCOPE         VARCHAR(128),  -- グローバル: NULL / Namespace 限定: NS ID
    PRIMARY KEY (PRINCIPAL_ID, ROLE, SCOPE),
    CONSTRAINT FK_PR_PRINCIPAL FOREIGN KEY (PRINCIPAL_ID)
        REFERENCES PRINCIPAL (ID) ON DELETE CASCADE
);
```

スコープ列で「特定 Namespace のみ EDITOR」のような粒度も表現可能。

## 5. 認可ポイント

| 操作 | 必要ロール | 備考 |
| --- | --- | --- |
| グローバル設定の閲覧 | VIEWER | |
| グローバル設定の更新 | ADMIN | |
| Namespace 作成/削除 | ADMIN | |
| Namespace 設定編集 | EDITOR (該当 NS) または ADMIN | |
| 辞書グローバル編集 | ADMIN | |
| 辞書 Namespace 編集 | EDITOR (該当 NS) | |
| ドキュメント取込/再構築 | EDITOR (該当 NS) | |
| バックアップ/リストア | ADMIN | リストアは破壊的、確認ダイアログ必須 |
| 検索実行 | VIEWER | |

## 6. 監査ログ

将来要件: 設定変更・破壊的操作には監査ログを残す。
最低限残す情報:

- 操作日時 / 操作元 IP / 操作 Principal
- 操作種別(設定変更、再構築、削除など)
- 対象 Namespace / リソース ID
- 操作前後の差分(可能な範囲で)

## 7. MVP との関係

- 本ドキュメントは「将来計画」を凍結したものであり、現行コードでは
  Spring Security を含めない。
- 既存の `searchable-admin` は完全公開を前提とする。本番運用では
  ネットワークレベル(VPN・社内 IP 制限・リバースプロキシ Basic 認証
  など)で保護することを推奨する。
- API Key 認証(TASK-126/143)は機械間通信のみを保護する。
  ヒト用認可はこの設計を実装するまで提供されない。

## 8. 実装着手の前提条件

将来本実装する際は以下を満たしてから着手する:

1. ロール/Principal モデルのレビュー
2. パスワードハッシュアルゴリズム(Argon2id 推奨)の選定
3. セッション保管先(DB / Redis)の選定
4. 監査ログのストレージ要件確定
