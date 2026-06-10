# scripts/

リポジトリ運用補助スクリプトの置き場。目的別にサブディレクトリで分割する。

| ディレクトリ | 用途 |
| --- | --- |
| `release/` | リリース手順補助（バージョン bump 等） |

## なぜここに e2e 検証スクリプトが無いか

以前は `scripts/verify/` に shell + curl の e2e 検証スクリプトを置いていたが、
**`examples/api` の Maven failsafe IT** に置き換えた。Maven 化により:

- `release.yml` の deploy 前ゲートに自然に組み込める
- packaged JAR を別 JVM で起動する真の e2e のまま、Failsafe + JUnit で構造化
- ステップごとの assertion を Java で書ける（jq + awk を使った bash パイプ不要）

詳細は [docs/devel/testing/verify.ja.md](../docs/devel/testing/verify.ja.md) と
[docs/devel/testing/README.md#テスト階層](../docs/devel/testing/README.md#テスト階層)
を参照。
