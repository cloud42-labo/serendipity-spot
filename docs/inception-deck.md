# Inception Deck: ついでにスポット (Serendipity Spot)

> `product-design-doc-standard.md`（`ADP-050`）に基づきバックフィル（`ADP-050-B`）。
> このリポジトリはProduct開始時の標準フローを経ずに育ったため、
> `README.md`・`android/README.md`・`cloud42-labo/brain`の該当記録から
> 事後的に再構成したもので、新しい意図を書き足してはいない。

## Why

「行ってみたいが、そこ自体は目的地ではない場所」は、思い出しても行動に移らない。
目的地なら自分で調べて行くので、通知は要らない。目的地ではないからこそ、
「たまたま近くに来た」瞬間に思い出させる仕組みが要る。ついでにスポットは
この一点だけを解決するために存在する。

## Elevator Pitch

用事のついでに寄れる場所を覚えておきたい人向けの、ついでにスポットは
位置ベースの「寄り道」通知アプリで、地図でスポットを登録しておくだけで、
近づいたらバックグラウンドで自動的に教えてくれる。行きたいリストや
地図上のブックマークと違い、**思い出す努力を要求しない**。

## Product Box

*「近くに来たら、教える。ついでにスポット。」* 地図をタップするだけの登録。
アプリを閉じていても効く通知。自分のGoogleドライブに残るデータ（ロックイン無し）。
試作品感の無い、素直に人に配れる仕上がり。

## NOT List

- 目的地ナビ・ルート案内アプリではない（Directions APIは通知タップ時の参考
  ルート表示にのみ使う）
- アプリ外への共有・投稿（アウトバウンド/ソーシャル共有）ではない。
  Googleマップ等からの共有を**受け取って登録する**フローは既に実装済みで
  対象外ではない（現状はいずれも個人のGoogleドライブ内で完結）
- 常時位置情報を収集・分析するトラッキングアプリではない（Geofencingの
  範囲内イベントのみを使い、位置履歴を蓄積しない）
- 独自バックエンド・サーバーを持つプロダクトではない（ユーザー自身の
  Google Drive/Sheetsに保存する設計）

## Stakeholders

| Stakeholder | ついでにスポットに求めるもの |
|---|---|
| 利用者 | 忘れずに、うるさすぎずに「ついでに寄れる」を知れること |
| Cloud42 Labo（オーナー） | 試作品感のない完成度と、実機で壊れない信頼性 |
| Claude（開発・保守） | 実機確認が要る変更（位置情報・通知・署名）を明示するガバナンス |
| Google Play（配布先） | プライバシーポリシー・スコープの最小化等の審査要件を満たすこと |

## Solution Outline

Kotlin + Jetpack ComposeのネイティブAndroidアプリ。地図（Google Maps SDK）で
スポットを登録し、Geofencing APIでバックグラウンド監視、近接で通知を出す。
データは独自サーバーを持たず、利用者自身のGoogleドライブ上のスプレッドシートに
`drive.file`スコープ（最小権限）で保存する。詳細は
[android/README.md](../android/README.md)・[design-doc.md](../design-doc.md)。

## Risks

- **実機でしか出ない不具合**: 位置情報・ジオフェンス・通知・署名まわりは
  CI/AIレビューが緑でも実機不具合を否定できない（過去3回踏んだ実例、
  [android-adaptive-icon-pitfalls](https://github.com/cloud42-labo/brain/blob/main/notes/android-adaptive-icon-pitfalls.md)・
  [mock-location-broke-the-real-test](https://github.com/cloud42-labo/brain/blob/main/notes/mock-location-broke-the-real-test.md)）。
  `CLAUDE.md`の「実機確認が要る変更」節でマージ後のqa/実機確認を必須化して緩和。
- **Google Playの機微スコープ審査**: `spreadsheets`スコープを要求すると
  審査（2〜6週間）が要る。`drive.file`のみに絞ることで回避している設計判断を
  崩さないことが重要。
- **ジオフェンス100件上限**（Android/Google Playの制約）: 101件目以降は
  通知対象外になる。利用者数・登録スポット数が増えた場合の再検討事項。

## Size & Milestones

`v1.0.0`（2026-07-31、`experimental`から正式リリース版として切り出し）以降、
セマンティックバージョニングで継続開発中（現行バージョンは
[android/app/build.gradle.kts](../android/app/build.gradle.kts)の`versionName`参照）。
直近のマイルストーンはEPIC-02〜04（登録摩擦ゼロ・近接通知の精度・立ち寄り履歴の
資産化、`SPOT-PLAN-02`で分解・実施中）。

## Trade-off Sliders

固定: 位置情報・通知・署名など実機でしか壊れない領域の実機確認ゲート
（速度のために省かない）。最初に緩める: ドキュメントの網羅性より、
実際に動くリリースの速度を優先する（`experimental`での試作方針を正式版でも
一部引き継ぐが、実機確認ゲートだけは`experimental`と違い外さない）。

## Scope Boundary

ついでにスポットは「近くに来たら思い出させる」という一点のプロダクト。
訪問先の検索・レビュー・ソーシャル機能・目的地までの本格的なナビゲーションは
このプロダクトの責務ではない（それぞれ既存の地図・レビューアプリの領域）。
