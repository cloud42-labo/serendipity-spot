# Google Play ストア掲載用 画像素材

[SPOT-06-S01-H02](https://app.notion.com/p/3c5fbd826f3b8104a173eaa489084e1c) の成果物。
`assets-source/ic_launcher_source.png`（アプリアイコンの元絵）から生成した。

## 揃っているもの

| ファイル | サイズ | 用途 |
| :--- | :--- | :--- |
| [`icon-512.png`](icon-512.png) | 512×512, 24-bit PNG（アルファなし） | Play Console ストアアイコン |
| [`feature-graphic-1024x500.png`](feature-graphic-1024x500.png) | 1024×500, 24-bit PNG | フィーチャーグラフィック |

生成方法: `assets-source/ic_launcher_source.png`（2048×2048、既に角丸スクエアの絵柄が
描かれた完成品）をそのまま縮小しただけ。**アダプティブアイコンのマスク処理は経由していない**
（ランチャーアイコンではなくストア掲載用の平面画像のため、
[brain/notes/android-adaptive-icon-pitfalls](https://github.com/cloud42-labo/brain/blob/main/notes/android-adaptive-icon-pitfalls.md)
で踏んだ「中央72dpだけが見える」問題は対象外）。フィーチャーグラフィックはアイコンの
配色（紫→青のグラデーション）を引き継いだ背景に、アイコンとアプリ名・タグラインを配置した。

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
最低2枚（推奨4〜8枚）のスクリーンショットを撮影し、このディレクトリへ追加する。
`SPOT-06-S01-H03`（Play Console実入力）と合わせてHuman側で実施するか、
KVM付きの実行環境が使えるセッションで再挑戦する。
