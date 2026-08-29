# Google Play ストア掲載用 画像素材

[SPOT-06-S01-H02](https://app.notion.com/p/3c5fbd826f3b8104a173eaa489084e1c) の成果物。
`assets-source/ic_launcher_source.png`（アプリアイコンの元絵）から生成した。

## 作成済み（Play Console・実機での最終検証は未実施）

| ファイル | サイズ | 用途 |
| :--- | :--- | :--- |
| [`icon-512.png`](icon-512.png) | 512×512, 32-bit PNG（RGBA、アルファチャンネル付き） | Play Console ストアアイコン |
| [`feature-graphic-1024x500.png`](feature-graphic-1024x500.png) | 1024×500, 24-bit PNG | フィーチャーグラフィック |

生成方法（PR #28へのCodexレビュー指摘2件を反映済み）:

- **`icon-512.png`**: `assets-source/ic_launcher_source.png`（2048×2048、角丸スクエアの
  絵柄が既に描かれた完成品）は絵柄自体に角丸の余白（白）が焼き込まれているため、単純に
  縮小するとPlay側のマスクと二重に丸まる／白い縁が出る問題があった（Codexレビュー
  指摘・P2）。四辺から120pxずつ（コーナーの丸め半径〜305pxの内側に収まる量）を
  クロップしてフルブリードの正方形にしてから512×512へリサイズしている。**アダプティブ
  アイコンのマスク処理は経由していない**（ランチャーアイコンではなくストア掲載用の
  平面画像のため、
  [brain/notes/android-adaptive-icon-pitfalls](https://github.com/cloud42-labo/brain/blob/main/notes/android-adaptive-icon-pitfalls.md)
  で踏んだ「中央72dpだけが見える」問題そのものは対象外）。また24-bit（アルファ無し）で
  書き出していたためPlay Consoleのストアアイコン要件（32-bit・アルファチャンネル付き）を
  満たしていなかった（Codexレビュー指摘・P1）。RGBA（不透明・alpha=255）で再書き出しした。
  **ただし、この2件の指摘対応はいずれも差分（ファイルの形式・寸法・画素）から確認できる
  範囲にとどまる。Play Consoleへの実際のアップロード可否と、Play側マスク適用後の最終的な
  見え方は未検証**（このサンドボックスにPlay Consoleアカウントも実機も無いため。
  Codexレビュー再指摘・P1、`google-play-release-checklist.md`参照）。
- **`feature-graphic-1024x500.png`**: アイコンの配色（紫→青のグラデーション）を引き継いだ
  背景に、アイコンとアプリ名・タグラインを配置した。バナー内のアイコン部分に丸角＋影を
  つけているのは意図した装飾（ストアアイコンとは別レイヤーの合成）であり、上記の
  ストアアイコン自体の問題とは無関係。24-bit PNG（アルファなし）はフィーチャーグラフィック
  の仕様として正しい。

## 揃っていないもの: スクリーンショット

**実アプリ画面のスクリーンショットは、このセッションでは作成していない。** このアプリは

- Google Maps SDK（ライブのタイル描画・GL）
- Googleサインイン（Credential Manager）
- 端末の位置情報・通知権限

に依存しており、これらはこのサンドボックス環境（Android SDK の `platform-tools` はあるが
`emulator` パッケージ・システムイメージ・`/dev/kvm` が無い）では動かせない。Jetpack Compose の
Preview Screenshot Testing（layoutlib によるレンダリング、AGP 8.5+ で利用可能）も検討したが、
`MapScreen` は Maps SDK のライブビューに強く依存しており、layoutlib では正しく描画されない
（本物のタイル・GL描画をエミュレートできない）ため見送った。

実際に動いていないアプリの画面を合成・捏造してスクリーンショットとして提出することは
しない（`google-play-release-checklist.md` に「最終的なマスク後の見え方と実画面
スクリーンショットは実機確認を伴う」と元々明記されている通り、この部分はもともと実機確認が
前提だった）。

**次工程**: 実機またはエミュレータ環境（Android Studio等）で `./gradlew installDebug` 後に
最低2枚（推奨4〜8枚）のスクリーンショットを撮影し、このディレクトリへ追加する。同じ機会に、
`icon-512.png`・`feature-graphic-1024x500.png`のPlay Consoleアップロード可否とマスク適用後の
最終的な見た目も確認する（3点セットでの検証）。`SPOT-06-S01-H03`（Play Console実入力）と
合わせてHuman側で実施するか、KVM付きの実行環境が使えるセッションで再挑戦する。
