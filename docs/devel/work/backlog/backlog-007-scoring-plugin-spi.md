# BACKLOG-007: カスタムスコアリングプラグイン SPI

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 未定 |
| 依存関係 | - |

## 概要

検索結果のスコアリングロジック（BM25 / ベクトル類似度 / ハイブリッド融合）を
プラグインで差し替えられるよう SPI を新設する。

## 補足

- Lucene の `Similarity` SPI とは別レイヤーの「最終ランキング」フックを想定。
- ハイブリッド検索のスコア融合（RRF / Reciprocal Rank Fusion など）の差替もここで吸収する。

---

**Last Updated**: 2026-06-10
