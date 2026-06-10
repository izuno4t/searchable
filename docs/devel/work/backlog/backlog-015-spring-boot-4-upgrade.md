# BACKLOG-015: Spring Boot 3.4.1 → 4.0.x メジャーアップグレード

| 項目 | 内容 |
| --- | --- |
| ステータス | ⏳ TODO |
| 起票元 | M3（`docs/devel/work/tasks/closed/tasks.m3.md`） |
| 想定マイルストーン | 独立マイルストーン候補（M4 以降の独立移行プロジェクト） |
| 依存関係 | - |

## 背景

依存脆弱性スキャン（Red Hat Dependency Analytics）で Spring Boot 3.4.1 の
各スターターに多数の推移的脆弱性（spring-boot-starter-web で critical 6 /
high 15 等）を検出した。最新 GA は 4.0.6（2026-05 時点、spring.io で確認）。
リリースは年 2 回・マイナーは最低 12 か月 OSS サポートの方針から、
3.4.x / 3.5.x は OSS サポート終了または終了間際と推定（正確な EOL 日は移行
計画時に要確認）。

## 影響範囲

- `searchable-admin`
- `examples/*`（webapp / api / mcp）
- `searchable-core` は Spring 非依存のため無関係

## 注意

- 3.x → 4.0 はメジャーアップで Spring Framework 7 ベース、破壊的変更を伴う。
- Java baseline・削除 / 変更 API・`jakarta` 系の差分を移行前に一次情報で確認すること。
- 段階移行（まず最新 3.x → 4.0）も検討する。

## 進め方

独立した移行プロジェクトとして
brainstorming → plan → 実装 → 全モジュールのテスト / 起動確認のサイクルで実施する。

---

**Last Updated**: 2026-06-10
