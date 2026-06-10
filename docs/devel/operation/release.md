# Searchable - リリース運用

Searchable を Maven Central (Sonatype Central Portal) に公開する際の
手順をまとめる。次の 2 点が中心。

- **バージョン番号の決め方** (SemVer + `-SNAPSHOT` 規約)
- **`v*` タグ駆動の自動公開フロー** (
  [`.github/workflows/release.yml`](../../../.github/workflows/release.yml))

実コマンドは [`scripts/release/bump-version.sh`](../../../scripts/release/bump-version.sh)
にまとめてあるので、暗記する必要はない。

## 1. 全体像

```text
[1] 開発期間 (1.X.Y-SNAPSHOT)
       │
       │  scripts/release/bump-version.sh 1.X.Y
       ▼
[2] release コミット (1.X.Y)
       │
       │  git tag v1.X.Y && git push --tags
       ▼
[3] release.yml が Sonatype Central に publish
       │
       │  scripts/release/bump-version.sh 1.X.(Y+1)-SNAPSHOT
       ▼
[4] 次の開発期間 (1.X.(Y+1)-SNAPSHOT)
```

すべての段階で **本体 (`searchable-parent` + `searchable-bom` +
6 サブモジュール) と examples (`api` / `mcp` / `webapp` /
`plugin-datasource-s3`) のバージョンを揃える**。`examples/api` 等は
ルート reactor に入っていないが、`searchable-bom` を
`<searchable.version>` プロパティ越しに import しているので、ここも
同時に追従させる必要がある。

## 2. バージョン番号の規約

SemVer に従う。`-SNAPSHOT` は開発中の非公開バージョンを表す慣例。

| 区分 | 形式 | 用途 |
| --- | --- | --- |
| release | `1.2.3` | Maven Central に publish するバージョン |
| snapshot | `1.2.3-SNAPSHOT` | 開発中のバージョン (Central snapshot リポジトリに publish 可能) |
| pre-release | `1.2.3-RC1` / `1.2.3-beta.1` | release candidate / ベータ |

[`release.yml`](../../../.github/workflows/release.yml) の
`Verify tag matches pom version` ステップが、タグ名 (`v` を除いた部分)
と root `pom.xml` の `<version>` が **完全一致** することを要求する。
すなわち `v1.2.3` タグを打つ前に、`searchable-parent` の version を
`1.2.3` にしておく必要がある。`-SNAPSHOT` 状態でリリースタグを打つと
ここで失敗する。

## 3. バージョン bump

`scripts/release/bump-version.sh <new-version>` を実行する。内部は
**本体 reactor は Maven プラグイン、examples は sed** のハイブリッド戦略を採る。

### 3.1 ハイブリッド戦略を採る理由

| 対象 | 手法 | 理由 |
| --- | --- | --- |
| 本体 reactor | [`versions:set`](https://www.mojohaus.org/versions/versions-maven-plugin/) | Maven が POM の構造を理解して `<version>` 要素のみ書き換え、`<parent><version>` を子モジュール全体で同期する。`${project.version}` 変数参照は自動伝搬する。`sed` 方式では誤爆 (コメント / SBOM 例示) や親子間の不整合が出やすい |
| examples | リテラル `sed` (2 箇所のみ) | 各 example は独立 reactor で `searchable-bom:OLD` を import する。`versions:set` が POM を読み込む段階で `OLD` の BOM が local repo にないと resolution が失敗し、bump が途中で止まる。各 example の version 出現箇所は `<version>OLD</version>` (artifact 自身) と `<searchable.version>OLD</searchable.version>` の 2 箇所だけなので、Maven を介さず直接置換しても安全 |

### 3.2 対象範囲

| 範囲 | 処理 |
| --- | --- |
| `searchable-parent` (root) | `versions:set` で `<version>` 書き換え |
| `searchable-bom` (独立 POM、ルート `<modules>` に含まれる) | 同上、root reactor で同時処理 |
| `searchable-plugins` / `-core` / `-ai` / `-testkit` / `-cli` / `-admin` | `<parent><version>` を root reactor が同時更新 |
| `examples/api` / `mcp` / `webapp` / `plugin-datasource-s3` | 各 pom の `<version>` (自身) と `<searchable.version>` プロパティを `sed -i` で 1 ファイルずつ更新 |

### 3.3 対象外 (意図的)

| 対象 | 理由 |
| --- | --- |
| `docs/devel/work/poc/**/pom.xml` | POC サンドボックス、リリース対象外 |
| `examples/ai-ollama/` / `examples/search-ui/` | `pom.xml` を持たない (設定例 / 静的ファイル) |
| third-party の `<version>` (`jackson` / `lucene` 等) | バージョン管理は `searchable-bom` で別管理 |

### 3.4 実行例

```bash
# 開発中 (1.0.1-SNAPSHOT) から release (1.0.1) に確定
./scripts/release/bump-version.sh 1.0.1

# 差分確認
git diff -- '*pom.xml'

# 反映が正しいか整合性ビルドで確認
./mvnw -B clean install -DskipTests
```

`-DgenerateBackupPoms=false` を渡しているので `.versionsBackup` は
残らない。万一巻き戻したい場合は `git restore -- '*pom.xml'` で十分。

## 4. リリース実行

### 4.1 release バージョンに bump してコミット

```bash
./scripts/release/bump-version.sh 1.0.1
```

スクリプトは pom.xml と `*.md` 内の `<version>...</version>` まで
自動で書き換える。**それ以外で version をリテラルで埋め込んでいる
箇所は機械的に判定できないので手動更新する**。下のチェックリストを
1 件ずつ確認すること(漏れがあると一見動くがバッジや起動コマンドが
古い version を指すことになる)。

#### リリース時 手動更新チェックリスト

| 種別 | パターン | 対象ファイル |
| --- | --- | --- |
| shields.io バッジ | `Version-${OLD}-brightgreen` → `Version-${NEW}-brightgreen` | `README.md`(冒頭バッジ) |
| Status 文 (英) | ``**${OLD} (stable).**`` の数字と `(stable)` ラベル | `README.md` 概要セクション |
| Status 文 | ``**Status**: \`${OLD}\` (released).`` の数字と `(released)` ラベル | `CLAUDE.md` 冒頭 |
| MCP レスポンス例 | `"version":"${OLD}"` → `"version":"${NEW}"` | `examples/mcp/README.md`(serverInfo 例示) |
| 設定例コメント | `# version: ${OLD}` → `# version: ${NEW}` | `examples/mcp/mcp-capabilities.yaml`, `examples/mcp/README.md` |
| API レスポンス例 | `"version": "${OLD}"` → `"version": "${NEW}"` | `examples/api/api-specification.ja.md` |

JAR ファイル名 (`-${OLD}.jar` → `-${NEW}.jar`) の置換対象:

- `docs/public/usage.md` / `docs/public/usage.ja.md`
- `docs/public/admin-ui-guide.md` / `docs/public/admin-ui-guide.ja.md`
- `examples/api/requirements.ja.md`
- `examples/mcp/requirements.ja.md` / `examples/mcp/guide.ja.md` / `examples/mcp/README.md`
- `examples/webapp/requirements.ja.md`

> `(stable)` / `(released)` ラベルは数字と一緒に意味が変わる。
> release → release bump (1.0.0 → 1.0.1) ならそのまま。
> release → SNAPSHOT bump (1.0.1 → 1.0.2-SNAPSHOT) のときは
> `(stable)` → `(development)`、`(released)` → `(SNAPSHOT)` 等に
> 書き換える。

#### コミット

```bash
./mvnw -B clean install -DskipTests   # 整合性ビルド
git diff                              # 全変更を確認
git add -A
git commit -m "chore: release 1.0.1"
```

> 注: コミット・タグ操作は人が行う。Claude Code 側からは
> `git commit` / `git tag` / `git push` を実行しない
> ([CLAUDE.md](../../../CLAUDE.md) の Working Rules を参照)。

### 4.2 タグ push で公開ワークフロー起動

```bash
git tag v1.0.1
git push origin main
git push origin v1.0.1
```

[`release.yml`](../../../.github/workflows/release.yml) が以下を順に
実行する。

1. `actions/setup-java@v5` で JDK 21 + Central Portal credentials + GPG
   private key を準備
2. **タグ名と pom version の一致を検証** (一致しなければ
   `::error::` で停止)
3. `./mvnw -Prelease -DskipTests deploy` で
   `central-publishing-maven-plugin` が Sonatype Central に publish
   - release タグ (`1.0.1` 等) → Maven Central
   - snapshot タグ (`1.0.1-SNAPSHOT` 等) → Central snapshot リポジトリ

公開が成功すると Sonatype Central の Deployments ページで confirm
状態になる。Maven Central への反映には数十分〜数時間かかる
(リポジトリ index の伝搬時間)。

### 4.3 必要な GitHub Secrets

| Secret | 取得元 |
| --- | --- |
| `CENTRAL_USERNAME` | <https://central.sonatype.com/account> で発行する Portal token のユーザー名 |
| `CENTRAL_TOKEN` | 同上、token パスワード |
| `GPG_PRIVATE_KEY` | ASCII armored GPG private key (`gpg --armor --export-secret-keys <key-id>`) |
| `GPG_PASSPHRASE` | 上記 GPG 鍵のパスフレーズ |

### 4.4 次の開発バージョンに戻す

release コミット直後は pom が固定 release バージョンのままなので、
次の開発サイクル用に `-SNAPSHOT` に戻しておく。

```bash
./scripts/release/bump-version.sh 1.0.2-SNAPSHOT
./mvnw -B clean install -DskipTests
git add '*pom.xml'
git commit -m "chore: bump to 1.0.2-SNAPSHOT"
git push origin main
```

## 5. トラブルシュート

### 5.1 タグ push 後 `Tag X does not match pom version` で失敗

タグ名と root `pom.xml` の `<version>` が一致していない。
release コミットを忘れて push したか、bump の `<version>` 反映漏れがある。

対処:

```bash
# 失敗したタグを削除
git tag -d v1.0.1
git push origin :refs/tags/v1.0.1

# pom を直して再コミット → タグ打ち直し
./scripts/release/bump-version.sh 1.0.1
git diff -- '*pom.xml'
git add '*pom.xml' && git commit --amend --no-edit
git tag v1.0.1
git push origin main --force-with-lease
git push origin v1.0.1
```

### 5.2 examples の `<searchable.version>` が更新されない

`bump-version.sh` は本体 → 各 example の順で実行している。途中で
コケると examples 側が古いままになる。`git diff -- '*pom.xml'` で
`<searchable.version>` を確認し、合わなければスクリプトを再実行する
(冪等)。

### 5.3 Sonatype Central で `Failed: validation`

主に GPG 署名漏れ / `pom.xml` の必須要素不足 (`<name>` / `<description>`
/ `<licenses>` / `<scm>` / `<developers>`)。
[Sonatype Central の Requirements](https://central.sonatype.org/publish/requirements/)
を確認し、`searchable-parent/pom.xml` の該当要素を補完してから再 publish。

## 6. 関連

- [`scripts/release/bump-version.sh`](../../../scripts/release/bump-version.sh)
  バージョン bump 実体
- [`.github/workflows/release.yml`](../../../.github/workflows/release.yml)
  タグ push 駆動の公開ワークフロー
- [`docs/devel/testing/verify.ja.md`](../testing/verify.ja.md)
  e2e 検証手順 (release タグ push 前の必須ゲート)
- [Sonatype Central Portal docs](https://central.sonatype.org/publish/publish-portal-maven/)
- [versions-maven-plugin](https://www.mojohaus.org/versions/versions-maven-plugin/)
