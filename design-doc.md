# Design Doc: ついでにスポット (Serendipity Spot)

> `product-design-doc-standard.md`（`ADP-050`）に基づきバックフィル（`ADP-050-B`）。
> `android/README.md`から合成した要約。設定手順・トラブルシューティングの
> 詳細はそちらが正本で、ここでは重複させない。`docs/PRD.md`の要求（What）を
> 受けた設計（How）。

## 1. Purpose / User Value

「行ってみたいが目的地ではない場所」を地図に登録しておくと、近づいたときに
バックグラウンドで自動的に思い出させる。`docs/PRD.md`参照。

## 2. UX / Core Loop

1. 地図タップ、中心「＋」、または**Googleマップ等アプリ外からの共有
   （`ACTION_SEND`）** でスポットを登録する（旗アイコン。登録済みは濃い旗、
   検索候補は薄い「まだ立っていない旗」）。
2. アプリを閉じても、Geofencing APIがバックグラウンドで監視を続ける。
3. 登録スポットの半径150m以内に**外から入る**と通知＋バイブで知らせる
   （同一スポットの再通知クールダウンは既定3時間、30分〜12時間で設定可能。
   DWELL遷移で2分後に1回だけ再通知）。
4. 通知から「寄った」を選ぶと立ち寄り履歴（Serendipity Log）に記録される。
   一覧画面で新しい順に表示し、削除・空状態表示もできる（内容を書き換える
   編集機能は無く、訂正は削除して再記録する1本のみ）。
5. 下部シートでスポットの一覧・編集・削除、ストリートビュー確認ができる。

## 3. Architecture

- Kotlin + Jetpack Compose のネイティブAndroidアプリ（`android/`単一モジュール）。
- 地図: Google Maps SDK for Android。検索: Android標準`Geocoder`。
- 共有受信: `ACTION_SEND`を`MainActivity`で受け、`share/`パッケージ
  （`ShareIntentReader`・`ShareTextParser`等）が共有テキストを解析して
  登録・検索フローへつなぐ（v1.2.0で導入）。
- 位置監視: Geofencing API（OSのバッチ処理、アプリ側の常駐プロセス不要）。
- 認証: Credential Manager（Sign in with Google）でサインインし、
  `AuthorizationClient`でDrive/Sheetsアクセス用スコープを別途取得する
  （認証とスコープ取得の責務を分離）。
- 通知タップ時の徒歩ルート表示にDirections API（Web Service、Maps用APIキーを
  流用し`X-Android-Package`/`X-Android-Cert`ヘッダーで制限）。
- 主要画面: `MapScreen`（Composable単位に分割済み）、選択スポット情報カード
  `SelectedSpotCard`、`SerendipityLogScreen`（立ち寄り履歴一覧・削除・空状態、
  `SPOT-04-S02-T01`・`T02`で追加）、一覧・カード用の共通部品 `AppCard`
  `AppTextField` `SpotActionIcons`（`Product Feel v1.1`で導入）。
- CI: `.github/workflows/serendipity-spot-android.yml`が`main`更新のたびに
  APKをビルドし`dev`（debug署名）/`latest`（release署名）タグへ貼り直す。
  `versionName`変更時は`v<versionName>`の固定リリースも作成する。

## 4. Data Model

- **スポット**: バックエンドを持たず、利用者自身のGoogleドライブ上の
  スプレッドシート（固定名`Serendipity Spot`、`drive.file`スコープで
  アプリが作成・アクセス）に保存する。列: `id | lat | lng | title | memo |
  radiusMeters | createdAt`。
- **立ち寄り履歴（VisitRecord）**: スプレッドシートとは別に、**端末内の
  SharedPreferencesにのみ**保存する（Googleドライブへは送信しない）。
  通知の「取り消す」で個別削除、端末の「データを消去」/アンインストールで
  全件消える。

## 5. Major Design Decisions

- **`drive.file`スコープのみを要求し、`spreadsheets`は要求しない。** 後者は
  「機密性の高いスコープ」でGoogleの本番審査（2〜6週間）が必要になるため。
  このアプリが使うSheets APIメソッド（create/get/values.get/values.append/
  values.update/batchUpdate）はいずれも`drive.file`で足りる。
- **バックエンドを持たず利用者自身のGoogleドライブに保存する。** 運用コストを
  かけず、利用者データの主権を利用者側に残す設計。
- **地図はOpenStreetMap（HTML試作）からGoogle Maps SDK for Androidへ変更。**
  利用者の回答に基づく判断（`android/README.md`「元のHTML版からの変更点」）。
- **通知は「アプリを開いている間だけ」ではなくGeofencingによる常時監視。**
  試作段階からの明確な変更で、この製品の核となる体験を成立させる前提。
- **立ち寄り履歴はスプレッドシートと分離し端末内のみに保存。** 位置の集合
  （スポット）と行動履歴（訪問ログ）で外部送信の要否が異なるため。

## 6. Constraints / Non-goals

- ジオフェンスは1アプリ最大100件（Android/Google Playの制約）。101件目以降は
  登録・一覧表示はできるが通知対象外。
- release署名鍵はクラウドセッションで生成しない（紛失時に取り返しがつかない
  ため、必ず開発者のPCで作業する）。
- `docs/inception-deck.md`のNOT List（ナビ・SNS・常時トラッキングではない）を
  参照。

## 7. Known Issues

- 過去に実機でのみ顕在化した重大な不具合が3回あり、位置情報・通知・署名・
  アイコン画像まわりの変更はCI/AIレビューだけでは不十分
  （[android-adaptive-icon-pitfalls](https://github.com/cloud42-labo/brain/blob/main/notes/android-adaptive-icon-pitfalls.md)・
  [mock-location-broke-the-real-test](https://github.com/cloud42-labo/brain/blob/main/notes/mock-location-broke-the-real-test.md)）。
  `CLAUDE.md`の実機確認ゲートで運用上緩和しているが、設計上の対策ではない。
- `v0.11.4`より前は、ジオフェンス100件超過時に**古い**100件を残す実装ミスが
  あった（新しい登録ほど通知されない逆の挙動）。修正済みだが再発防止のテストは
  未整備。
- `Product Feel v1.1`Epicの一部Story（STORY-03〜06）は実機確認Human Requestが
  未完了のまま`paused`（[brain/projects/serendipity-spot-v1.1-product-feel](https://github.com/cloud42-labo/brain/blob/main/projects/serendipity-spot-v1.1-product-feel/README.md)）。

## 8. Current Specification / Source of Truth

実際の現在仕様は稼働コード（`android/`）と`android/app/build.gradle.kts`の
`versionName`（本バックフィル時点で`1.6.0`）が正本。セットアップ・ビルド・
配布手順の詳細は[android/README.md](android/README.md)。運用ルールは
[CLAUDE.md](CLAUDE.md)。
