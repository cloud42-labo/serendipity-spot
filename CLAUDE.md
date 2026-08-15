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

## 作業開始ゲート / PR作成ゲート（必須）

Cloud42 Labo / Vibe Product Development / ADP / AOD の作業では、以下を**ゲート**として
扱う。注意事項ではない。知っているだけでは足りず、毎回実行前に確認すること。

### 作業開始前

1. **Notionの対象タスクを確認する。** 存在しなければ、作業を始める前に
   `Stories & Tasks` へ新規作成する。コードに触るのはその後。
2. **Done済みタスクの範囲を再度変更する場合は、既存タスクを勝手に流用しない。**
   Reopenすべきか、新規Taskを作るべきかを先に判断する。

### タスク本文の書き方

タスク本文は単なる説明ではなく、**担当AIがそのまま実行できるプロンプト**として書く。
最低限、次の7項目を含める。

1. 目的
2. 実行指示
3. 入力・参照先
4. 制約・判断ルール
5. 成果物・記録先
6. 完了条件
7. 実行結果の記録先

### PR作成前（3点チェック）

- [ ] **Task Exists?** — 対応するNotionタスクがあるか
- [ ] **Reopen or New Task?** — Done済みの範囲を変えるなら、その判断を済ませたか
- [ ] **PR has Notion Traceability?** — PRからNotionタスクを辿れるか（本文にリンク／
      タスク側に Pull Request を記録）

**1つでも満たしていなければ、PR作成や実装継続より先にNotion側を整備する。**

逸脱した場合は、AOD-01-S05「AIルール遵守率を計測する」の実測ログ対象として記録される。

> 実例（2026-08-15）: BUG-SPOT-03-01 の作業中に実機報告を受け、対象タスクを作らないまま
> PR #22 を作成した。さらにDone済みの BUG-SPOT-02-02 / SPOT-02-S02-T03 の確定挙動を
> 変更する内容を含めており、レビューで needs-human として差し戻された。
> 上記3点を先に確認していれば防げた。

## GitHub操作

**git の操作・コミット・PR作成はすべて Claude が行う。** ユーザーに git コマンドを
打たせない。main へ直接pushしない。

**ただし、このリポジトリではPRを作ったところで止め、Claude自身はマージしない。**
レビュー・マージは下記「PRレビュー・マージ」節のChatGPT側の仕組みに委ねる
（他リポジトリの既定ルール「PRを作ったら止めずマージまで行う」
[brain/notes/github-pr-workflow](https://github.com/cloud42-labo/brain/blob/main/notes/github-pr-workflow.md)
を、このリポジトリに限って上書きするもの）。

## PRレビュー・マージ

このリポジトリ自体のGitHub Actionsではなく、**ChatGPT側の2つの仕組み**でPRのレビュー・
マージを行う。

1. Claude が `claude/*` ブランチでPRを作り、そこで止める
2. **Codex の Automatic reviews**（ChatGPT Plus/Pro契約の範囲）がPRを自動レビューする。
   レビュー規約は [AGENTS.md](AGENTS.md) の `## Code Review Rules`
3. **ChatGPT側の毎時タスク**が、Codexの指摘とCIの状態を確認し、問題なければ
   GitHub連携でsquashマージする。指摘があればマージせず待つ

- 自前のGitHub Actionsワークフロー（`openai/codex-action` / `anthropics/claude-code-action`
  をAPI課金で呼び出す方式）は一度実装したが、課金を避けるためにやめた。詳細は
  [brain/decisions/0008 の追記](https://github.com/cloud42-labo/brain/blob/main/decisions/0008-ai-review-loop-codex-vs-claude.md)
- **セッション開始時、レビュー待ち・マージ待ちのまま長く止まっているPRが無いか確認し、
  あれば状況を把握する。** ChatGPT側の毎時タスクが拾えていない可能性がある
- 詳細: [brain/notes/ai-pr-review-loop](https://github.com/cloud42-labo/brain/blob/main/notes/ai-pr-review-loop.md)

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
