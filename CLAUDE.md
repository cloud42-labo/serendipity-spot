# serendipity-spot — Claude Code 向け運用ルール

「ついでにスポット」の正式リリース版リポジトリ。
[cloud42-labo/experimental](https://github.com/cloud42-labo/experimental) での試作を経て
`v1.0.0` として切り出した（2026-07-31）。詳細は [README.md](README.md)。

## セッションの始め方

作業を始める前に、`cloud42-labo/brain` がまだセッションに無ければ `add_repo` で追加する。
過去の決定・教訓（`decisions/` `notes/`、特にこのアプリ関連のもの）が入っており、
参照せずに作業すると同じ失敗を繰り返しやすい
（詳細: [brain/notes/attach-brain-every-session](https://github.com/cloud42-labo/brain/blob/main/notes/attach-brain-every-session.md)）。

エージェント編成（`pm` / `architect` / `qa` / `security`）は [.claude/agents/README.md](.claude/agents/README.md) 参照。

## 構成

```
serendipity-spot/
├── android/   # Kotlin + Jetpack Compose によるネイティブAndroidアプリ
├── docs/      # GitHub Pages（プライバシーポリシー等）
└── .claude/agents/   # エージェント編成の定義
```

現時点では `android/` のみ。詳細は [android/README.md](android/README.md)。

## GitHub操作

**git の操作・コミット・PR作成・マージはすべて Claude が行う。** ユーザーに git コマンドを
打たせない。PRを作ったら、そこで止めずマージまで行う（承認待ちのまま放置しない）。
main へ直接pushしない。

これは `cloud42-labo` 配下のリポジトリに共通する基本ルール
（詳細: [brain/notes/github-pr-workflow](https://github.com/cloud42-labo/brain/blob/main/notes/github-pr-workflow.md)）。

## AIレビュー・修正ループ

Claude が作った PR（`claude/*` ブランチ）は、`.github/workflows/ai-pr-review-loop.yml` により
OpenAI Codex が自動レビューする。指摘があれば Claude が自動修正して再レビュー、CI成功かつ
指摘なしで自動マージする。**最大3ラウンドで収束しなければ `needs-human` ラベルが付いて停止する。**

- レビュー規約は [AGENTS.md](AGENTS.md)（Codexが自動で読む）
- 必要なSecrets・リポジトリ設定は [README.md](README.md) の「AIレビュー・修正ループ」節
- **セッション開始時、このリポジトリに `needs-human` ラベルの付いたPRが無いか確認し、
  あれば優先的に拾う。** 3ラウンドで収束しなかった＝AIループでは解けなかった問題
- これは `cloud42-labo` 配下のリポジトリに共通する基本ルール
  （詳細・設計根拠: [brain/decisions/0008](https://github.com/cloud42-labo/brain/blob/main/decisions/0008-ai-review-loop-codex-vs-claude.md)、
  [brain/notes/ai-pr-review-loop](https://github.com/cloud42-labo/brain/blob/main/notes/ai-pr-review-loop.md)）

## バージョニング

セマンティックバージョニング（`MAJOR.MINOR.PATCH`）を適用する。`v1.0.0`（正式リリース）以降は
このリポジトリで開発を続ける。

| 桁 | 上げるタイミング | 例 |
| :--- | :--- | :--- |
| MAJOR | 別ゲームレベルの破壊的変更 | `1.x.x` → `2.0.0` |
| MINOR | 機能追加・新画面・新指標の追加 | `1.0.x` → `1.1.0` |
| PATCH | バグ修正・文言修正・UIの微調整 | `1.0.0` → `1.0.1` |

変更後は `android/app/build.gradle.kts` の `versionName`（と `versionCode`）を必ず更新する。
詳細: [brain/notes/semver-and-release-deliverables](https://github.com/cloud42-labo/brain/blob/main/notes/semver-and-release-deliverables.md)。

## 実機確認が要る変更

アイコン・画像リソース、位置情報・ジオフェンス・バックグラウンド通知、署名まわりの変更は、
CIが緑でもAIレビューが通っても実機不具合を否定できない（過去に3回踏んだ:
[brain/notes/android-adaptive-icon-pitfalls](https://github.com/cloud42-labo/brain/blob/main/notes/android-adaptive-icon-pitfalls.md)、
[brain/notes/mock-location-broke-the-real-test](https://github.com/cloud42-labo/brain/blob/main/notes/mock-location-broke-the-real-test.md)）。
このような変更をマージ後は、`qa` エージェントまたは実機での確認を挟む。

## やらないこと

- 秘密情報（APIキー、パスワード、トークン、署名鍵）はコードにもコミットにも書かない。
  GitHub Secretsに置く
- 大きなバイナリをコミットしない
