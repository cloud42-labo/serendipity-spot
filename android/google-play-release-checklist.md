# Google Play 一般公開 準備チェックリスト（SPOT-06-S01）

[SPOT-EPIC-06｜Google Play一般公開をE2Eで完走する](https://app.notion.com/p/3c4fbd826f3b8127ad60c42d683431ac)
の最初のStory。**このタスクの範囲は「準備を完了する」まで。実際の審査提出は
`SPOT-06-S03`（Backlog）で別途行う。**

現状の配布方式（`docs/index.html`）は「作者と、その家族・友人が使うためのアプリ」として
GitHub Releasesから直接APKを配る形。Google Play一般公開は、それとは別の公開チャネルを
新設する位置づけで、既存の直接配布を置き換えるものではない（継続するかはHuman判断）。

- 更新日: 2026-08-23 JST
- 対応PR: （このファイルを追加したPRを参照）

## サマリー

| 項目 | 状態 |
| :--- | :--- |
| 1. アプリ署名 | 🟡 CI側の仕組みは既存。**release鍵の生成だけHuman未実施** |
| 2. AAB (Android App Bundle) | ✅ 完了（このタスクで実機確認済み） |
| 3. ストア掲載情報 | 🟡 文言は下書き済み。**画像素材はHuman未着手** |
| 4. プライバシー/データ安全性 | 🟡 プライバシーポリシーは既存。**Data Safetyフォーム入力はHuman未実施** |
| 5. 対象APIレベル | ✅ 完了（このタスクで対応。36へ引き上げ済み） |

以下、項目ごとに詳細と、Human Requestとして切り出したタスクへのリンクを記載する。

---

## 1. アプリ署名

**CI側の自動署名の仕組みは既に存在する**
（[.github/workflows/serendipity-spot-android.yml](../.github/workflows/serendipity-spot-android.yml)、
`SERENDIPITY_RELEASE_KEYSTORE_BASE64`等のGitHub Secretsを設定すればrelease署名ビルドが動く）。

**未実施なのはrelease鍵そのものの生成。** [android/README.md の「release鍵を用意する」節](README.md#release鍵を用意する正式配布する場合のみpc側の作業)
に手順が既にドキュメント化されている。要点:

- release鍵は**このプロジェクトのクラウドセッションでは生成しない**（失うと取り返しがつかない鍵のため、必ずHumanが自分のPCで生成する）
- `keytool -genkeypair` で生成 → パスワード・鍵ファイルをこのリポジトリの外へバックアップ
- SHA-1をGoogle Cloud ConsoleのAndroid型OAuthクライアントに登録
- `base64 -w0 release.keystore` の出力等をGitHub Secretsへ設定

**Google Play自体は「Play App Signing」を使うのが現在の標準**（Google Play Console側がPlay配布用の署名鍵を管理し、開発者はアップロード鍵で署名したAABを提出する）。上記のrelease鍵は
そのアップロード鍵として使う想定でよい。

→ Human Request: **SPOT-06-S01-H01**（下記「Humanタスク一覧」参照）

## 2. AAB (Android App Bundle)

**完了。** このタスクで実際にビルドして確認した。

```sh
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab が生成される
```

現状はrelease鍵未設定のためdebug署名のままバンドルされる（ビルド自体は成功する）。
release鍵設定後、同じコマンドで正式な署名付きAABが得られる。

`isMinifyEnabled = false`（コード圧縮なし）のため、圧縮関連のProGuard起因の不具合は無い。
将来 `true` に変える場合は改めて実機確認が要る。

## 3. ストア掲載情報

以下は下書き。**最終的な文言の承認はHuman（オーナー）が行う。**

### アプリ名（30文字以内）

```
ついでにスポット
```

### 簡単な説明（80文字以内）

```
行ってみたいけど目的地ではない場所を、別の用事でたまたま近づいたときだけ知らせる地図アプリ
```

（79文字。Google Play側の実際のカウント方法で再確認すること）

### 詳しい説明（4000文字以内、下書き）

```
「今度あの店に行ってみたい」と思っても、そこを目的に出かけるほどではない場所があります。
そういう場所は、たいてい忘れたままになります。

「ついでにスポット」は、そうした場所を地図にピン留めしておくと、別の用事でたまたま
近くに来たときに通知するアプリです。目的地なら自分で行くので通知は要りません。
目的地ではないからこそ通知が要る、というのがこのアプリの成り立ちです。

■ できること
・地図をタップ、または検索して、行ってみたい場所を登録
・登録した場所の半径150m以内に外から入ると、アプリを閉じていても通知
・通知をタップすると、現在地からその場所までの徒歩ルートと距離を表示
・登録した場所は、自分のGoogleドライブのスプレッドシートに保存

達成の記録も、催促もしません。行かなかったことを責められることはありません。

■ データの扱い
・運営サーバーはありません。処理は端末上と、利用者自身のGoogleアカウントとの間で完結します
・位置情報は端末上でのみ使われ、どこにも送信されません
・登録した場所は、利用者自身のGoogleドライブのスプレッドシート1つに保存されます。開発者は閲覧できません
・広告・アクセス解析は入っていません
```

出典: `docs/index.html`（既存の紹介ページ）をベースに、Play掲載用の文体へ調整した。

### カテゴリ・タグ

案: 「地図とナビ」（Maps & Navigation）、または「ツール」。**最終決定はHuman判断**
（アプリの主機能はジオフェンス通知だが、地図操作が主要な操作導線でもあるため、
どちらが発見されやすいかはストア側の相対競合にも依存する）。

### 画像素材（未着手・Human Request）

- **アプリアイコン（512×512、高解像度）**
- **フィーチャーグラフィック（1024×500）**
- **スクリーンショット（スマートフォン、最低2枚、推奨4〜8枚）**

このアプリは過去に**アダプティブアイコンで実機確認なしに気づけない不具合を2回踏んでいる**
（[brain/notes/android-adaptive-icon-pitfalls](https://github.com/cloud42-labo/brain/blob/main/notes/android-adaptive-icon-pitfalls.md)）。
512×512アイコンを既存の432×432レイヤー（`ic_launcher_background.png`/`ic_launcher_foreground.png`）
から機械的に合成することもできるが、**同じ理由（マスク後の見え方は実際に確認しないと分からない）
でここでは生成していない**。スクリーンショットも実際にアプリを操作した画面が要る。

→ Human Request: **SPOT-06-S01-H02**

## 4. プライバシー / データ安全性

**プライバシーポリシーは既に公開済み。** [docs/privacy-policy.html](../docs/privacy-policy.html)
（GitHub Pages、カスタムドメイン設定あり、`docs/CNAME`）。Play Console
の「アプリのコンテンツ」→「プライバシーポリシー」にこのURLをそのまま設定できる。

**未実施なのは Play Console の Data Safety フォーム入力**（Play Console画面上の質問形式で、
APIでは埋められずHumanの操作が必要）。回答の下書き:

| 質問 | 回答（下書き） |
| :--- | :--- |
| 位置情報を収集するか | 収集する（正確な位置情報） |
| 位置情報を第三者と共有するか | 共有しない |
| 位置情報の用途 | アプリの中核機能（ジオフェンス通知） |
| 位置情報は端末外に送信されるか | 送信されない（端末上でのみ判定） |
| 個人情報（メールアドレス等）を収集するか | 収集する（Googleサインインのアカウント情報。認証のみに使用、外部送信なし） |
| ユーザー作成データ（登録スポット）の保存場所 | 開発者のサーバーではなく、利用者自身のGoogleドライブ |
| 広告ID・アクセス解析SDKの有無 | 無し |
| データの暗号化（転送時） | Google Sheets/Drive APIへのHTTPS通信のみ。アプリ独自サーバーへの送信は無い |
| データの削除要求への対応 | 利用者自身がGoogleドライブ上のスプレッドシートを削除すれば全データが消える（アプリ・開発者側に別途保持するデータは無い） |

この表は`android/README.md`の「データについて」節と`docs/privacy-policy.html`の記載に基づく。
**Play Consoleでの実際の選択肢文言は変更されることがあるため、回答時に現物のフォームと
突き合わせて確認すること。**

→ Human Request: **SPOT-06-S01-H03**

## 5. 対象APIレベル

**完了。** このタスクで対応した。

Google Playの target API level要件（2026-08-31、新規アプリ・更新はAndroid 16 / API 36以上が
必須。延長申請でHuman Request経由なら2026-11-01まで猶予可）に対応するため:

- `compileSdk` / `targetSdk`: `35` → `36`
- CI（`.github/workflows/serendipity-spot-android.yml`）のSDKインストール手順に
  `platforms;android-36` を追加
- `gradle.properties` に `android.suppressUnsupportedCompileSdk=36` を追加
  （AGP 8.7.2はcompileSdk=36を正式サポート範囲外として警告を出すが、ビルド・テストは
  問題なく成功することを確認済み。AGP/Gradleの本格アップグレードは別タスク）
- `./gradlew testDebugUnitTest` / `assembleDebug` / `bundleRelease` すべて成功を確認
- `versionCode` 40 / `versionName` 1.5.1

**この変更（ビルド設定・SDKレベル）自体は実機での挙動確認が必須ではないが**、
Android 15/16では通知・位置情報・バックグラウンド動作まわりのデフォルト挙動が
変わることがあるため、次回の実機確認（Human Request）で target API 36 ビルドを
一度確認しておくことを推奨する。

---

## Humanタスク一覧

このStoryの範囲でAIが完了できない項目。Notionへ個別タスクとして登録済み。

| ID | 内容 | 参照 |
| :--- | :--- | :--- |
| SPOT-06-S01-H01 | release署名鍵の生成・Secrets登録 | 本ファイル「1. アプリ署名」、`android/README.md` |
| SPOT-06-S01-H02 | ストア掲載用画像素材（アイコン512×512・フィーチャーグラフィック・スクリーンショット）の作成 | 本ファイル「3. ストア掲載情報」 |
| SPOT-06-S01-H03 | Play Console Data Safetyフォームの入力、カテゴリ最終決定、Play Consoleアカウント自体のセットアップ（開発者登録・$25登録料等） | 本ファイル「4. プライバシー / データ安全性」 |

Play Consoleの開発者アカウント登録自体（本人確認・登録料の支払い）もHumanのみが行える。
上記3件のいずれかに含めるのではなく、着手時にH03と合わせて確認すること。
