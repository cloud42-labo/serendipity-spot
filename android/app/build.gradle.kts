import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.cloud42labo.serendipityspot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cloud42labo.serendipityspot"
        minSdk = 26
        targetSdk = 36
        versionCode = 40
        versionName = "1.5.1"

        manifestPlaceholders["mapsApiKey"] =
            (localProperties.getProperty("MAPS_API_KEY") ?: "").ifBlank { "MISSING_MAPS_API_KEY" }

        // Credential Manager の Sign in with Google に渡す「ウェブアプリケーション」型の
        // OAuth クライアント ID。Android 型のクライアント ID ではない点に注意。
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"${localProperties.getProperty("GOOGLE_SERVER_CLIENT_ID") ?: ""}\"",
        )
    }

    signingConfigs {
        // RELEASE_STORE_FILE が無い間は、下のbuildTypes.releaseでsigningConfigが
        // 一切設定されないため、releaseビルドは（debug署名ではなく）**未署名**の
        // APK/AABになる（`app-release-unsigned.apk`という実際のファイル名で確認済み。
        // Codexレビュー指摘、SPOT-06-S01のPR #25）。秘密鍵はこのプロジェクトの
        // コードには一切含めない。
        create("release") {
            val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.getByName("release").let { config ->
                if (config.storeFile != null) signingConfig = config
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // RobolectricでComposeを実描画するテスト（OnboardingIntroLayoutTest、
            // SPOT-06-S01-T03）がリソース（テーマ・文字列等）を必要とするため。
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            // google-auth-library の複数 jar が同名で持ち込むメタデータ。実行時に不要。
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Maps
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.maps.android:maps-compose:6.2.1")

    // Location / Geofencing
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // サインイン (Credential Manager) と、Sheets/Driveスコープの認可 (AuthorizationClient)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Google Sheets / Drive API (Sheetsを個人スプレッドシートとしてDBに使う)
    implementation("com.google.api-client:google-api-client:2.7.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-sheets:v4-rev20260610-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20260712-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.http-client:google-http-client-gson:1.45.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    // Coroutines + Play Services Task interop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
    // ユニットテスト用クラスパスではandroid.jarのorg.jsonはスタブ（呼ぶと例外）のため、
    // Spot/VisitRecordのtoJson()/fromJson()系をJVM単体テストで動かすには実装が要る
    // （SPOT-04-S01-T01、VisitRecordTestで初めてorg.jsonに依存するテストを追加した際に判明）。
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    // Compose UIをRobolectric（JVM上、emulator不要）で実描画してテストするための構成
    // （OnboardingIntroLayoutTest、SPOT-06-S01-T03）。androidTestではなくtestImplementation
    // に付けているのはRobolectricがJVM単体テストとして動くため。
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    // createComposeRule()がテスト用のホストActivityを起動するために要る（manifestを提供するだけ）。
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
