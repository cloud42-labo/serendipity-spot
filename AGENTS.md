# AGENTS.md — Codexレビュー規約

このファイルは、`.github/workflows/ai-pr-review-loop.yml` から起動される
OpenAI Codex（`openai/codex-action`）が PR レビュー時に読む規約。Codex CLI は
リポジトリ直下の `AGENTS.md` を自動的に読み込む。人間・Claude Code 向けの規約は
[CLAUDE.md](CLAUDE.md) を参照（内容はおおむね重なる）。

## このリポジトリについて

Android ネイティブアプリ「ついでにスポット」（Kotlin + Jetpack Compose）。
[cloud42-labo/experimental](https://github.com/cloud42-labo/experimental) での試作を経て
`v1.0.0` として切り出した専用リポジトリ。詳細は [README.md](README.md)、
[android/README.md](android/README.md)。

## レビューの進め方

1. `git diff origin/main...HEAD` で差分を見る
2. 差分がこのリポジトリの規約・過去の失敗パターンに触れていないか、下記のチェック項目で確認する
3. 一般的なコーディング規約の指摘だけでなく、**このリポジトリ固有で実際に起きた失敗**を
   優先して探す（一般論のレビューはClaude自身の自己レビューと相関しやすく、価値が低い）
4. 修正は行わない。指摘のみ行う

## 実際に踏んだ失敗（優先してチェックする）

- **GitHub Actions の `if:` に `secrets.X` を直接書くと、ワークフロー全体が無効になる**
  （jobが1件も起きずに`failure`になる。GitHubの式検証の制約）。job の `env` に一度写して
  `env.X` 経由で参照すること
- **`run: |` の複数行文字列は、字下げがブロックより浅い行が来た時点で終わる。**
  文字列の途中でも関係なく終わる。複数行の本文（リリースノート等）はファイルに書き出して
  `--notes-file` / `--body-file` で渡すこと。`gh release create --notes "1行目。\n\n2行目"`
  のような直接埋め込みは避ける
- **依存ライブラリのバージョンを記憶で書かない。** `com.google.apis:...:v4-revYYYYMMDD-2.0.0`
  のような日付付きバージョンは、実在する日付か確認されていないと存在しないバージョンを
  指したままビルドが壊れる。レジストリの `maven-metadata.xml` で実在を確認したものか、
  PRの説明にその根拠があるかを見る
- **アダプティブアイコンの `<vector>` にパスが1つも無いと、ビルドは通るが実行時に
  `AdaptiveIconDrawable` ごと壊れてアイコンが表示されない。** 「何も描かない」層は
  `@android:color/transparent` を使うべきで、空の `<vector>` ではない
- **OAuthスコープは必要最小限か。** `spreadsheets` のような機密スコープは、
  `drive.file` など非機密スコープで同じAPI呼び出しが通らないか先に疑う。スコープを
  広げる変更は理由を明示させる
- **署名鍵・APIキー・トークンなどの秘密情報がコード・ログ出力・コミットに混入していないか**
- **権限（GitHub Actions permissions、Android のパーミッション宣言）が変更の目的に対して
  過剰に広がっていないか**

## AIレビューでは検知できないもの（見つけたら明示的に指摘する）

Codexはコードを読むだけで、実機やCIでのビルド・実行はしない。次のような変更は
**レビューが「問題なし」でも実機での不具合を否定できない**ため、該当する変更を見つけたら
「実機確認が必要」と明記すること。

- アイコン・画像リソースなど、レイアウト崩れやマスク処理が絡む見た目の変更
  （プレビューでの見え方と、アダプティブアイコンのマスク後の見え方は異なる）
- 位置情報・ジオフェンス・バックグラウンド通知まわりの変更（エミュレータでの動作と
  実機・実地での動作が異なりうる）
- 署名まわりの変更（ビルドが通ることと、正しい鍵で署名されることは別の話）

## 出力形式

- 秘密情報の混入がある場合は `REQUEST_CHANGES` とし、該当箇所を具体的に指摘する
  （秘密情報の値そのものはレビュー本文に書き写さない）
- それ以外に指摘が無ければ `APPROVE`
- 出力の最後は、ワークフロー側が機械的にパースするため、必ず次のいずれか1行のみで終える
  （前後に他の文言を混ぜない）

  ```
  CODEX_VERDICT: APPROVE
  ```
  または
  ```
  CODEX_VERDICT: REQUEST_CHANGES
  ```

## 関連

- [CLAUDE.md](CLAUDE.md)
- [README.md](README.md) — AIレビュー・修正ループの全体像、必要なSecrets
- [android/README.md](android/README.md) — Androidビルド用シークレット（本ループとは別物）
- [cloud42-labo/brain](https://github.com/cloud42-labo/brain) の `decisions/` `notes/`
  （このリポジトリで実際に起きた失敗の詳しい記録）
