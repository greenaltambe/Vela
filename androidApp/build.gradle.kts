import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// One version for everything: version.properties (see buildSrc/VelaVersion).
val appVersion = VelaVersion.read(rootProject.file("version.properties"))

// Release signing comes from the environment (CI) or an untracked keystore.properties (local); never from the repository.
// With neither, the release build is produced UNSIGNED (and named so), which keeps forks and pull requests building.
//   ANDROID_KEYSTORE_PATH or ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD
//   keystore.properties: storeFile=, storePassword=, keyAlias=, keyPassword=
class ReleaseKey(val file: File, val storePassword: String, val keyAlias: String, val keyPassword: String)
val releaseKey: ReleaseKey? = run {
    val env = System.getenv()
    val props = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { f -> Properties().also { p -> f.inputStream().use(p::load) } }
    val storePassword = env["ANDROID_KEYSTORE_PASSWORD"] ?: props?.getProperty("storePassword")
    val alias = env["ANDROID_KEY_ALIAS"] ?: props?.getProperty("keyAlias")
    val keyPassword = env["ANDROID_KEY_PASSWORD"] ?: props?.getProperty("keyPassword") ?: storePassword
    val file: File? = env["ANDROID_KEYSTORE_PATH"]?.let { File(it) }
        ?: env["ANDROID_KEYSTORE_BASE64"]?.takeIf { it.isNotBlank() }?.let { b64 ->
            layout.buildDirectory.file("signing/release.keystore").get().asFile.also { out ->
                out.parentFile.mkdirs(); out.writeBytes(Base64.getMimeDecoder().decode(b64))
            }
        }
        ?: props?.getProperty("storeFile")?.let { rootProject.file(it) }
    if (file != null && file.isFile && storePassword != null && alias != null && keyPassword != null) ReleaseKey(file, storePassword, alias, keyPassword) else null
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
        versionCode = VelaVersion.versionCode(appVersion)
        versionName = appVersion
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
    signingConfigs {
        releaseKey?.let { k -> create("release") { storeFile = k.file; storePassword = k.storePassword; keyAlias = k.keyAlias; keyPassword = k.keyPassword } }
    }
    buildTypes {
        release {
            // R8 stays off on purpose: NewPipeExtractor (reflection-heavy parsing), the embedded Python/yt-dlp and the JNI libraries
            // would need keep rules that are easy to get wrong and hard to test without a device; the APK size cost is accepted for the beta.
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    lint {
        // The 77 errors that existed when CI was added (back-gesture handling, API-level checks...) are recorded in the baseline; lint fails only on NEW problems.
        // Regenerate after fixing some: ./gradlew :androidApp:updateLintBaseline
        baseline = file("lint-baseline.xml")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
    }
}
// ./gradlew :androidApp:collectReleaseApk  ->  build/release/Vela-<version>-android-arm64[-unsigned].apk
val signedRelease = releaseKey != null
tasks.register<Copy>("collectReleaseApk") {
    group = "distribution"
    description = "Builds the release APK and copies it to build/release under its public name."
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(layout.buildDirectory.dir("release"))
    val publicName = "Vela-$appVersion-android-arm64${if (signedRelease) "" else "-unsigned"}.apk"
    rename { publicName }
}
