plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Versiyon GitHub ile otomatik senkron: tag v1.0.X ise X, yoksa GITHUB_RUN_NUMBER, yoksa 1
val gitTagVersion = System.getenv("GITHUB_REF_NAME")?.removePrefix("v")?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+.*""")) }
val runNumberVersion = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val appVersionCode = runNumberVersion ?: 1
val appVersionName = gitTagVersion ?: "1.0.$appVersionCode"
val releaseStore = System.getenv("RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
val releaseAlias = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.indirgitsin.app"
    compileSdk = 35
    testBuildType = System.getenv("ANDROID_TEST_BUILD_TYPE") ?: "debug"

    defaultConfig {
        applicationId = "com.indirgitsin.app"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("distribution") {
                storeFile = file(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            // A permanent identity, separate from historical APKs signed with disposable debug keys.
            applicationIdSuffix = ".stable"
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            isMinifyEnabled = false
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
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // Compose 1.8 includes lint tooling that can read Kotlin 2.1 metadata.
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // Networking and persistent downloads
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // zemer-cipher: YouTube cipher + PoToken (composite build from cipher/ submodule)
    implementation("com.zemer:cipher")
    implementation("com.jakewharton.timber:timber:5.0.1")
    // NewPipeExtractor - v0.26.3+ SABR workaround (PR #1508) iceriyor, eski v0.25.1 SABR'yi cozemiyor
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    // DataStore (ayarlar)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coil (thumbnail)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room - Geçmiş
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // In-app playback (muxing uses android.media.MediaMuxer)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.core:core-ktx:1.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.work:work-testing:2.9.1")
}

val checkReleaseSigning by tasks.registering {
    doLast {
        check(releaseSigningReady && file(releaseStore!!).isFile) {
            "Release imzası eksik. RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS ve RELEASE_KEY_PASSWORD tanımlanmalı."
        }
    }
}
tasks.matching { it.name in setOf("packageRelease", "assembleRelease", "bundleRelease") }.configureEach {
    dependsOn(checkReleaseSigning)
}
