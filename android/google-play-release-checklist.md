# Google Play 一般公開 準備チェックリスト（SPOT-06-S01）

[SPOT-EPIC-06｜Google Play一般公開をE2Eで完走する](https://app.notion.com/p/3c4fbd826f3b8127ad60c42d683431ac)
の最初のStory。**このタスクの範囲は「準備を完了する」まで。実際の審査提出は
`SPOT-06-S03`（Backlog）で別途行う。**

現状の配布方式（`docs/index.html`）は「作者と、その家族・友人が使うためのアプリ」として
GitHub Releasesから直接APKを配る形。Google Play一般公開は、それとは別の公開チャネルを
新設する位置づけで、既存の直接配布を置き換えるものではない（継続するかはHuman判断）。

- 更新日: 2026-09-22 JST（[BUG-SPOT-06-01](https://app.notion.com/p/3e3fbd826f3b816c8488d9cdcadbf109)対応）
- 対応PR: #25, #35（クローズ・役割分離のため再作成）

## サマリー

| 項目 | 状態 |
| :--- | :--- |
| 1. アプリ署名 | 🔴 **Play配布版の実機確認でGoogleログイン不能・地図未描画を検出（`BUG-SPOT-06-01`）**。upload/release鍵のSHA-1登録は完了していたが、Play App Signingが配布時に再署名する**アプリ署名鍵**のSHA-1がGoogle CloudのOAuth / Maps APIキー制限へ未登録だった。詳細は下記「Play App Signingのアプリ署名鍵をAPIプロバイダへ登録する」節 |
| 2. AAB (Android App Bundle) | ✅ CIでrelease鍵署名済みAABの生成・署名検証・配布まで完了（`SPOT-06-S02-T01`） |
| 3. ストア掲載情報 | 🟡 文言は下書き済み。**アイコン・フィーチャーグラフィックはAIで作成済みだがPlay Console/実機での最終検証は未実施。スクリーンショットは未着手（いずれも実機/エミュレータ必須）** |
| 4. プライバシー/データ安全性 | 🟡 プライバシーポリシー・バックグラウンド位置情報の初回開示・Play申告文・デモ動画手順を整備済み。**Data Safetyフォームの実際の入力のみHuman未実施** |
| 5. 対象APIレベル | ✅ 完了（36へ引き上げ済み） |

以下、項目ごとに詳細と、Human Requestとして切り出したタスクへのリンクを記載する。

---

## 1. アプリ署名

**CI側の自動署名の仕組みは既に存在する**
（[.github/workflows/serendipity-spot-android.yml](../.github/workflows/serendipity-spot-android.yml)、
`SERENDIPITY_RELEASE_KEYSTORE_BASE64`等のGitHub Secretsを設定すればrelease署名ビルドが動く）。

`SPOT-06-S01-H01`として、2026-08-23にHuman作業を完了した。

- release keystoreをオーナーPCで生成
- 鍵ファイルをリポジトリ外の非共有Google Driveへバックアップ
- パスワードをパスワード管理ツールへ保存
- SHA-1をGoogle Cloud ConsoleのAndroid型OAuthクライアントへ登録
- `SERENDIPITY_RELEASE_KEYSTORE_BASE64`を今回の鍵で更新
- 既存の`STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`は従来値を維持し、CIで整合性を検証する

**Google Play自体は「Play App Signing」を使うのが現在の標準**。Google Play Console側がPlay配布用の署名鍵を管理し、開発者はアップロード鍵で署名したAABを提出する。上記のrelease鍵はそのアップロード鍵として使う。**AAB形式でのアップロードはPlay App Signingへの登録が必須**（オプトアウトできない）。

### Play App Signingのアプリ署名鍵をAPIプロバイダへ登録する（必須・未実施）

Play App Signingでは、AABをアップロードするときの**upload/release鍵**と、
ユーザー端末へ実際に配布されるAPKを署名する**アプリ署名鍵**が別物になる。
Google Playはアップロードされたupload鍵署名のAABを受け取り、内部で自前のアプリ署名鍵で
**再署名したAPK**を各端末に配信する。

Google Sign-In（Credential Manager）はCredential Manager経由でも内部的に
「呼び出し元アプリの署名証明書（パッケージ名+SHA-1）が、同じGoogle Cloudプロジェクトに
登録されたAndroid型OAuthクライアントと一致するか」を検証する。Google Maps SDKのAPIキーも
Androidアプリ制限で同様にパッケージ名+SHA-1を照合する。**どちらも「今インストールされている
APKの実際の署名証明書」を見る**ため、Play App Signingで再署名されたAPKのSHA-1が
未登録だと、ログインとMapsの両方が同時に、かつGitHub直接配布のAPK（upload/release鍵で
そのまま配布、再署名を経ない）では再現しない形で失敗する。これがBUG-SPOT-06-01の症状と一致する。

これまでのAI検証（`SPOT-06-S01-T02`のCI署名検証、`SPOT-06-S02-T01`のAAB署名検証）は
いずれも「upload/release鍵で署名した成果物」までしか確認しておらず、**Play Consoleが
生成するアプリ署名鍵の存在・SHA-1はAIのサンドボックスから取得できない**（Play Console
アカウントが無いため）。取得・登録はHuman-onlyの操作として`HUMAN-BUG-SPOT-06-01-1`へ切り出した。

実施手順（`HUMAN-BUG-SPOT-06-01-1`参照）:

1. Play Console → 対象アプリ → **アプリの整合性**（旧:リリース → セットアップ →
   アプリの署名）→ **Play アプリ署名** タブを開く。
2. **アプリ署名鍵証明書**の欄に表示されるSHA-1証明書フィンガープリントをコピーする。
3. Google Cloud Console → 対象プロジェクト → 「APIとサービス」→「認証情報」→
   「認証情報を作成」→「OAuthクライアントID」→ 種類は **Android** で新規作成し、
   パッケージ名 `com.cloud42labo.serendipityspot` + 上記SHA-1を登録する。
   （既存のupload/release鍵用・CI鍵用のAndroid型クライアントは削除しない。同じ
   パッケージ名で複数登録してよい。）
4. Maps/Directions用APIキーの「アプリケーションの制限」（Androidアプリ）に、
   同じパッケージ名 + 上記SHA-1の組を追加する。
5. Play Consoleのクローズドテストからインストールした端末（Play配布版）で、
   Googleログイン・地図描画・スポット登録（Drive/Sheetsアクセス）を再確認する。

→ Human Request: **HUMAN-BUG-SPOT-06-01-1**（Play ConsoleとGoogle Cloud Consoleの
実操作のみ。AIはここまでの原因確定・手順の言語化・正本更新を担当した）

→ Human Request: **SPOT-06-S01-H01 完了**（upload/release鍵自体の生成・登録）
→ AI検証: **SPOT-06-S01-T02**（release署名CIの検証）・**SPOT-06-S02-T01**（AAB署名CIの検証）

## 2. AAB (Android App Bundle)

**完了（`SPOT-06-S02-T01`）。** CIで`bundleRelease`により署名済みAABを生成し、AABの
署名証明書SHA-1がrelease keystoreと一致することを機械検証したうえで、Actions artifact
および`latest` GitHub Releaseの固定URLから取得できる。

```sh
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab が生成される
```

`isMinifyEnabled = false`（コード圧縮なし）のため、圧縮関連のProGuard起因の不具合は無い。将来`true`に変える場合は改めて実機確認する。

**注意**: このAAB署名検証は「upload/release鍵で正しく署名されているか」のみを保証する。
Play Consoleがアップロード後に生成する**アプリ署名鍵**での再署名結果は、AI側のCIからは
検証できない（Play Consoleアカウントが必要）。上記「1. アプリ署名」の
Play App Signing節を参照。

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
・通知の「寄った」をタップすれば、その場で立ち寄りを記録できる
・登録した場所は、自分のGoogleドライブのスプレッドシートに保存

立ち寄りの記録は任意です。記録しなくても、催促されたり行かなかったことを
責められたりすることはありません。

■ データの扱い
・運営サーバーはありません。処理は端末上と、利用者自身のGoogleアカウント・Googleの地図サービスとの間で完結します
・登録スポットへの接近通知のため、アプリを閉じている／使用していないときにも位置情報を利用します
・「近くに来た」の判定（ジオフェンス）は端末上でのみ行われ、この判定のための位置情報を開発者へ送信しません
・徒歩ルートを表示する操作をしたときだけ、その時点の現在地とスポットの座標をGoogleのルート計算サービスへ送信します
・登録した場所は、利用者自身のGoogleドライブのスプレッドシート1つに保存されます。開発者は閲覧できません
・立ち寄りの記録（いつ・どこに寄ったか）は端末内にのみ保存されます。開発者には送信されません
・広告・アクセス解析は入っていません
```

出典: `docs/index.html`（既存の紹介ページ）をベースに、Play掲載用の文体へ調整した。SPOT-04-S01の立ち寄り履歴と、SPOT-06-S01-T01のバックグラウンド位置情報開示を反映済み。

### カテゴリ・タグ

案: 「地図とナビ」（Maps & Navigation）、または「ツール」。**最終決定はHuman判断**。

### 画像素材（一部完了・最終検証は未実施）

- 🟡 **アプリアイコン（512×512、高解像度）** — [`store-assets/icon-512.png`](store-assets/icon-512.png)作成済み。
  形式（32-bit RGBA）・角丸余白の除去は差分から確認できるが、**Play Consoleへの実際の
  アップロード可否と、Play側マスク適用後の最終的な見え方は未検証**（このサンドボックスに
  Play Consoleアカウントも実機も無い）。
- 🟡 **フィーチャーグラフィック（1024×500）** — [`store-assets/feature-graphic-1024x500.png`](store-assets/feature-graphic-1024x500.png)作成済み。
  形式・寸法は差分から確認できるが、同様にPlay Console上でのプレビュー表示は未検証。
- 🟡 **スクリーンショット（スマートフォン、最低2枚、推奨4〜8枚）** — 未着手。
  Maps SDK・Googleサインイン・位置情報権限に依存するため、エミュレータ/実機が無い
  サンドボックス環境では撮影できなかった（詳細: [`store-assets/README.md`](store-assets/README.md)）

このアプリは過去にアダプティブアイコンで実機確認なしに気づけない不具合を経験しているため、最終的なマスク後の見え方と実画面スクリーンショットは実機確認を伴う。アイコン・フィーチャーグラフィックはアダプティブアイコンのマスク処理を経由しない平面画像のためAIで**作成**はできたが、Play Console上での**アップロード可否・プレビュー表示・実機での最終的な見え方の検証**はまだ済んでいない（Codexレビュー指摘、PR #28）。スクリーンショット撮影と合わせて、3点ともHuman側の実機/Play Console確認が必要な残作業として扱う。

→ Human Request: **SPOT-06-S01-H02**（スクリーンショット撮影に加え、アイコン・フィーチャーグラフィックの
Play Consoleアップロード確認・マスク後の最終見た目確認も残作業）

## 4. プライバシー / データ安全性

**プライバシーポリシーは公開済み。** [docs/privacy-policy.html](../docs/privacy-policy.html)
（GitHub Pages、カスタムドメイン設定あり）。

SPOT-06-S01-T01で、位置情報節を「アプリを閉じている／利用していないときにも位置情報を利用する」ことまで明示する形へ更新した。ジオフェンスの接近判定は端末内、徒歩ルート表示時のみGoogle Directions APIへ座標を送る、という実装上の区別も維持している。

### Data Safety回答下書き

| 質問 | 回答（下書き） |
| :--- | :--- |
| 位置情報を収集するか | 収集する（正確な位置情報） |
| 位置情報を第三者と共有するか | **共有する（Google Directions API）**。徒歩ルートを表示する操作をしたときだけ、その時点の現在地と目的地であるスポット座標をルート計算のためGoogleへ送信する。広告・分析目的ではない。近接判定（ジオフェンス）自体は端末上で完結し送信しない |
| 位置情報の用途 | アプリの中核機能（バックグラウンドでのジオフェンス接近通知、徒歩ルート表示） |
| 位置情報は端末外に送信されるか | **徒歩ルート表示操作時のみ送信される**。ジオフェンス近接判定の位置情報は端末外へ送信しない |
| 個人情報（メールアドレス等）を収集するか | 収集する（Googleサインインのアカウント情報。認証のみに使用、端末内保存） |
| ユーザー作成データ（登録スポット）の保存場所 | 開発者のサーバーではなく、利用者自身のGoogle Drive |
| ユーザー作成データ（立ち寄り履歴）の保存場所 | 開発者のサーバーにもGoogle Driveにも送信せず、端末内のみ（SharedPreferences） |
| 広告ID・アクセス解析SDKの有無 | 無し |
| データの暗号化（転送時） | Google Sheets/Drive APIおよびGoogle Directions APIへのHTTPS通信のみ。アプリ独自サーバーへの送信は無い |
| データの削除要求への対応 | 登録スポットは利用者自身のGoogle Drive上のスプレッドシート削除、立ち寄り履歴は通知の「取り消す」またはアプリデータ消去・アンインストールで削除できる |

Play Consoleでの実際の選択肢文言は変更されることがあるため、回答時に現物のフォームと突き合わせて確認する。

### バックグラウンド位置情報のprominent disclosure

OSの位置情報権限要求より前に、初回オンボーディングで次の内容を表示する。

> 登録したスポットへの接近を検知して通知するため、アプリを閉じているときや使用していないときにも位置情報を利用します。近接判定は端末上で行われ、この判定のための位置情報が開発者へ送信されることはありません。
>
> 次に、位置情報（バックグラウンド利用を含む）と通知の許可を確認します。許可しない場合、近づいたときの通知は利用できません。

実装: `android/app/src/main/java/com/cloud42labo/serendipityspot/ui/OnboardingIntro.kt`

### Play Console「位置情報の権限」申告文（下書き）

**なぜバックグラウンド位置情報が必要か**

利用者が「行ってみたいが、それ自体を目的地にはしない場所」を登録しておき、別の用事で偶然その場所の近くへ来たときに通知することが本アプリの中核機能である。この通知は、アプリを画面に表示していない時間にも登録スポットへの接近を検知する必要があるため、バックグラウンド位置情報が必要。

**利用する機能**

登録スポットの半径150mへの進入をAndroid Geofencing APIで検知し、接近通知を表示する。ジオフェンス近接判定は端末上で行い、この判定のための現在地を開発者へ送信・保存しない。登録スポット座標は利用者自身のGoogle Drive上のスプレッドシートに保存される。

詳細版: [google-play-background-location-declaration.md](google-play-background-location-declaration.md)

### デモ動画 撮影手順

Play Consoleでバックグラウンド位置情報のデモ動画を求められた場合は、次の一連の操作を1本の動画で示す。

1. アプリを新規インストールまたはアプリデータを消去した状態から起動する。
2. 初回オンボーディングのprominent disclosureを全文読める状態で表示する。
3. 「はじめる」を押し、OSの位置情報権限画面へ進む。
4. バックグラウンド利用に必要な権限（「常に許可」相当）を有効化する流れを示す。
5. スポットを登録する。
6. アプリを閉じる、またはバックグラウンドへ移す。
7. 登録スポットへ外側から接近し、接近通知が表示されることを示す。
8. 可能であれば通知をタップし、登録スポットがアプリ上で開くところまで示す。

動画内では「権限を求める理由」「バックグラウンドで使われる機能」「ユーザーに見える効果」が同じ流れで分かるようにする。

→ Human Request: **SPOT-06-S01-H03**（Play Consoleへの実入力のみ）

## 5. 対象APIレベル

**完了。** このタスクで対応した。

Google Playのtarget API level要件に対応するため:

- `compileSdk` / `targetSdk`: `35` → `36`
- CI（`.github/workflows/serendipity-spot-android.yml`）のSDKインストール手順に`platforms;android-36`を追加
- `gradle.properties` に `android.suppressUnsupportedCompileSdk=36` を追加
- `./gradlew testDebugUnitTest` / `assembleDebug` / `bundleRelease` の生成確認済み
- `versionCode` 40 / `versionName` 1.5.1

Android 15/16では通知・位置情報・バックグラウンド動作まわりのデフォルト挙動が変わることがあるため、次回の実機確認でtarget API 36ビルドを確認する。

---

## Humanタスク一覧

このStoryの範囲でAIが完了できない項目。Notionへ個別タスクとして登録済み。

| ID | 内容 | 状態 |
| :--- | :--- | :--- |
| SPOT-06-S01-H01 | release署名鍵の生成・Secrets登録 | ✅ Human作業完了。CI検証はT02へ移管 |
| SPOT-06-S01-H02 | ストア掲載用画像素材（アイコン512×512・フィーチャーグラフィック・スクリーンショット）の最終実機確認 | 🟡 アイコン・フィーチャーグラフィックはAIで作成済み（`store-assets/`、形式・角丸は差分検証済み）。**Play Consoleアップロード確認・マスク後の最終見た目・スクリーンショット撮影がすべて残作業** |
| SPOT-06-S01-H03 | Play Console Data Safetyフォーム入力、カテゴリ最終決定、Play Consoleアカウントセットアップ | 🟡 未完了 |
| HUMAN-BUG-SPOT-06-01-1 | Play App Signingのアプリ署名鍵SHA-1を取得し、Google CloudのOAuthクライアント・Maps APIキー制限へ登録する | 🔴 未完了（`BUG-SPOT-06-01`、P0、クローズドテスト継続のブロッカー） |

Play Consoleの開発者アカウント登録自体（本人確認・登録料の支払い）はHumanのみが行える。
