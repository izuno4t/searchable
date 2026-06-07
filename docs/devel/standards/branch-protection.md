# Branch Protection Setup (TASK-325)

GitHub のブランチ保護設定はリポジトリ管理権限が必要で、コード化が
できないため、本ドキュメントに手順を明文化する。

`main` ブランチに対して以下を設定する。

## 必須チェック

CI ワークフロー (`.github/workflows/ci.yml`) で定義された以下のジョブを
PR マージ前に必須にする。

| ジョブ名 | 内容 |
| --- | --- |
| `Build + Unit Tests` | ビルド + ユニットテスト + JaCoCo 集計 |
| `Integration Tests (H2 + Testcontainers)` | 統合テスト全件 |
| `Static Analysis (Checkstyle + SpotBugs)` | コードスタイル + バグパターン |
| `Docs Lint (markdownlint + Spectral + cspell)` | ドキュメント Lint |

`Dependency Vulnerability Scan` と `Aggregate Coverage Report` は
informational として **必須にはしない**（前者は NVD レート制限・
ネットワーク要因で不安定、後者は集計のみ）。

## 設定手順

1. GitHub リポジトリ → **Settings** → **Branches** を開く
2. **Add branch protection rule** をクリック
3. **Branch name pattern** に `main` を入力
4. 次のチェックを ON にする:
   - **Require a pull request before merging**
     - **Require approvals**: 1 以上
     - **Dismiss stale pull request approvals when new commits are pushed**
   - **Require status checks to pass before merging**
     - **Require branches to be up to date before merging**
     - 検索欄から上記 4 ジョブを選択
   - **Require conversation resolution before merging**
   - **Require signed commits**（任意）
   - **Do not allow bypassing the above settings**

## 例外運用

ホットフィックスなど例外時は管理者権限で一時的に保護を解除する。
解除した事実と理由は `CHANGELOG` または release notes に記載すること。

## チェック名称が変わった場合

CI ジョブの `name:` フィールドを変更したらブランチ保護の必須チェック
リストも更新する。両者が一致しないと PR がマージ不可になる。
