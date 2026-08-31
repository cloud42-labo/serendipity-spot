# PRD: ついでにスポット (Serendipity Spot)

> `product-design-doc-standard.md`（`ADP-050`）に基づきバックフィル（`ADP-050-B`）。
> `README.md`・`android/README.md`から合成。`docs/inception-deck.md`の
> Why/Elevator Pitchを受けた要求定義。

## 1. Target User / Problem

「行ってみたいが、それ自体は目的地ではない場所」がある人。目的地なら
自分で行くので通知は要らないが、目的地ではないからこそ、忘れたまま
近くを通り過ぎてしまう。既存の地図アプリのブックマーク・お気に入りは
「思い出して開く」操作が要り、この種の場所には合わない。

## 2. User Value / Use Cases

- 地図をタップ、または現在地の「＋」ボタンでスポットを登録できる。
- 登録したスポットの半径150m以内に外から入ると、アプリを閉じていても
  通知＋バイブで知らせる（同一スポットは3時間再通知しない）。
- 通知を見逃しても、2分後にまだ圏内にいれば「まだ近くです」ともう一度知らせる
  （同じ滞在中1回に限る）。
- 通知から1タップで「寄った」を記録し、後から立ち寄り履歴を振り返れる
  （Serendipity Log、EPIC-04）。
- 住所・駅名・施設名で検索してスポット候補を見つけられる。
- 登録済みスポットの一覧・編集・削除、ストリートビュー確認ができる。
- 端末を再起動してもジオフェンスが張り直される。

## 3. Functional Requirements

- 地図タップ／中心「＋」でのスポット登録（Google Maps SDK for Android）。
- Googleマップ等からの共有（`ACTION_SEND`）を受け取り、共有テキストを解析して
  登録・検索フローへつなぐ（`share/`パッケージ、v1.2.0で導入）。
- Geofencing APIによるバックグラウンド近接通知（150m、ENTER遷移＋DWELL再通知）。
- Googleアカウントでのサインイン（Credential Manager）と、利用者自身の
  Googleドライブ上のスプレッドシートへの保存・読み込み（自動作成含む）。
- 住所・施設名検索（Android標準`Geocoder`）。
- スポットの一覧・編集・削除、ストリートビュー導線（外部Googleマップアプリ）。
- 通知からの「寄った」記録と一覧表示（Serendipity Log、`SPOT-04-S02-T01`実装済み）。
  **記録済みの立ち寄り履歴の修正・削除・空状態UIは未実装**（`SPOT-04-S02-T02`、
  Backlog）。現状できるのは記録・一覧表示・通知からの取り消しのみ。
- PCなしでの最新版配布（GitHub Actionsによる`dev`/`latest`APKタグ配布）。

## 4. Non-functional Requirements

- **最小権限**: Google Sheets/Drive APIへのスコープは`drive.file`のみに限定し、
  「機密性の高いスコープ」`spreadsheets`は要求しない（Google審査回避の設計判断）。
- **利用者データの主権**: バックエンドを持たず、スポットは利用者自身の
  Googleドライブ上に保存する。立ち寄り履歴のみ端末内SharedPreferencesに
  保存し、外部送信しない。
- **実機での堅牢性**: 位置情報・通知・署名まわりの変更は、CI/AIレビューが
  緑でも実機確認を必須とする（`CLAUDE.md`「実機確認が要る変更」節）。
- **ジオフェンス上限への配慮**: Android/Google Playの1アプリ100件上限内で、
  新しい登録ほど通知対象として優先されるようにする。

## 5. Success Metrics

- 実機での20分連続利用・回転・オフライン再起動・PNG保存等を含む受入基準を
  満たす（`OEK-04-S01`等、実機確認Human Requestの合格）。
- 位置情報・通知まわりのリリース後に、実機確認ゲートで検出される重大な
  回帰が無いこと（過去のギャップは
  [mock-location-broke-the-real-test](https://github.com/cloud42-labo/brain/blob/main/notes/mock-location-broke-the-real-test.md)参照）。
- Google Playでの一般公開に必要な審査要件（プライバシーポリシー・
  スコープ最小化）を満たしたまま維持する。

## 6. Constraints

- ジオフェンスは1アプリ最大100件（Android/Google Playの制約）。
- テスト状態のOAuthはリフレッシュトークンが7日で失効する（本番切り替えで解消）。
- release鍵はクラウドセッションでは生成しない（紛失時に取り返しがつかないため、
  必ず開発者自身のPCで作業する）。
- Claude Codeのクラウドセッションでビルドするには`dl.google.com`の
  許可リスト追加が要る（`android/README.md`参照）。

## 7. Non-goals / Out of Scope

- 目的地ナビ・本格的なルート案内（Directions APIは通知タップ時の参考表示のみ）。
- **アプリ外への共有・投稿（アウトバウンド/ソーシャル共有）やレビュー機能。**
  Googleマップ等からの共有を**受け取って登録する**フロー（`ACTION_SEND`受信）は
  既に実装済みのため対象外ではない——ここで対象外とするのは、他人との共同編集や
  SNSへの投稿など、アウトバウンド方向の共有機能。
- 複数人での共同編集・チーム利用（現状は個人のGoogleドライブ内で完結）。
- 位置履歴の蓄積・分析（Geofencingイベントのみを使い、常時トラッキングはしない）。

## 8. Requirement Decisions / Open Questions

- **決定**: Sheets/Driveスコープは`drive.file`のみ。`spreadsheets`は要求しない
  （Google審査を回避し、権限も最小化できるため）。
- **決定**: 地図はOpenStreetMap（HTML試作時）からGoogle Maps SDK for Androidへ
  変更（利用者の回答に基づく判断）。
- **決定**: 通知は「アプリを開いている間だけ」ではなく、Geofencingによる
  常時バックグラウンド監視にした。
- **未確定**: `design-doc.md`はADP-050-Aと同様に本バックフィルで新規作成した
  （ADP自体と異なり、serendipity-spotには代替しうる既存の集約ドキュメントが
  無いため）。今後のEpic完了時にこのPRDと合わせて更新すること。
- **未確定**: ジオフェンス100件上限に達する規模になった場合の対応方針
  （優先度付け、古いスポットの扱い等）は未検討。
