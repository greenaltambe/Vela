import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(project(":extractor-newpipe"))
    implementation(project(":net-okhttp"))
    implementation(project(":innertube-auth"))
    // yt-dlp tier on Android: Python 3.12 + yt-dlp + QuickJS (GPL-3.0 library, see docs/reports/22). No ffmpeg artifact: only metadata is read.
    implementation(project(":extractor-ytdlp"))
    implementation(libs.youtubedl.android)
    implementation(libs.okhttp)
    // NewPipeExtractor uses java.time, which needs desugaring below API 26 (minSdk is 24).
    coreLibraryDesugaring(libs.desugar.jdkLibs)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayerHls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutinesAndroid)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.testJunit)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.greenaltambe.vela"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.greenaltambe.vela"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        // The bundled Python/QuickJS ship per ABI (about 15 MB each); arm64 covers every current phone.
        ndk { abiFilters += "arm64-v8a" }
    }
    packaging {
        // The library unpacks libpython.zip.so from nativeLibraryDir, so the libs must be extracted at install.
        jniLibs {
            useLegacyPackaging = true
            // libpython.zip.so is a zip archive (the unpacked Python), not an ELF file: stripping it fails.
            keepDebugSymbols += "**/*.zip.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
    }
}