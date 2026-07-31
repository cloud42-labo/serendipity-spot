# ついでにスポット (serendipity spot)

**行ってみたいが、そこ自体は目的地ではない場所**をピン留めしておくアプリ。別の用事で
たまたま近くに来たときに「ついでに寄れる」と教える。

[cloud42-labo/experimental](https://github.com/cloud42-labo/experimental) で試作・検証した
アプリが正式リリースの判断に至ったため、専用リポジトリとして切り出した。

## 構成

プラットフォームごとにディレクトリを分ける。

```
serendipity-spot/
└── android/   # Kotlin + Jetpack Compose によるネイティブAndroidアプリ
```

現時点では `android/` のみ。詳細は [android/README.md](android/README.md) を参照。

## 開発運用

このリポジトリは [Claude Code](https://claude.com/claude-code) が開発・保守する。
運用ルールは [CLAUDE.md](CLAUDE.md)、AIレビュー規約は [AGENTS.md](AGENTS.md) を参照。

### PRレビュー・マージ

このリポジトリ自体のGitHub Actionsではなく、**ChatGPT側の2つの仕組み**でPRのレビュー・
マージを行う（API課金の発生する自前のワークフローは廃止した。経緯は
[cloud42-labo/brain の decisions/0008 追記](https://github.com/cloud42-labo/brain/blob/main/decisions/0008-ai-review-loop-codex-vs-claude.md) 参照）。

1. Claude が `claude/*` ブランチでPRを作り、**そこで止める（自分ではマージしない）**
2. **Codex の Automatic reviews**（ChatGPT Plus/Pro契約の範囲）が、このリポジトリの
   PRを自動レビューする。レビュー規約は [AGENTS.md](AGENTS.md) の `## Code Review Rules`
   （Codexが自動で読む）。重大度の高い指摘（P0/P1）のみをGitHubの通常のレビューとして投稿する
3. **ChatGPT側の毎時タスク**が、Codexのレビュー結果とCIの状態を確認し、指摘が無く
   CIも問題なければGitHub連携でsquashマージする。指摘があればマージせず待つ

このリポジトリ側で必要な設定は無い（Secretsの登録もWorkflow permissionsの変更も不要。
Androidアプリのビルド・署名に使う `SERENDIPITY_*` シークレットは別物で、
[android/README.md](android/README.md) 参照）。ChatGPT側の設定（このリポジトリへの
Codex Automatic reviewsの有効化、毎時タスクのGitHub連携）は別途ChatGPTのCodex設定画面で行う。
