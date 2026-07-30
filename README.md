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
