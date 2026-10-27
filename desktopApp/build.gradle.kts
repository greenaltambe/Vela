@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":extractor-newpipe"))
    implementation(project(":net-okhttp"))
    implementation(project(":extractor-ytdlp"))
    implementation(project(":player-mpv"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

apply(from = rootProject.file("gradle/live-test.gradle.kts"))

dependencies {
    // Live tests drive the real UI with real libmpv and network; never part of `check`.
    add("liveTestImplementation", compose.desktop.currentOs)
    add("liveTestImplementation", compose.uiTest)
    add("liveTestImplementation", libs.kotlin.test)
    add("liveTestImplementation", libs.junit)
    add("liveTestImplementation", libs.kotlinx.coroutinesTest)
    add("liveTestImplementation", libs.kotlinx.serializationJson)
}

// A real window with the real player screen for N seconds, printing what actually reaches the screen:
// ./gradlew :desktopApp:liveWindow -PliveVideo=aqz-KE-bpKQ -PliveSeconds=45
tasks.register<JavaExec>("liveWindow") {
    group = "verification"
    description = "Opens a real window, plays a video through the real PlayerScreen and reports frames drawn, CPU, resize and fullscreen."
    classpath = sourceSets.getByName("liveTest").runtimeClasspath
    mainClass = "com.greenaltambe.vela.LiveWindowKt"
    if (providers.gradleProperty("noRenderCap").isPresent) jvmArgs("-Dvela.noRenderCap=true")
    args(
        providers.gradleProperty("liveVideo").getOrElse("aqz-KE-bpKQ"),
        providers.gradleProperty("liveSeconds").getOrElse("45"),
        providers.gradleProperty("liveQuality").getOrElse(""),
    )
}

compose.desktop {
    application {
        mainClass = "com.greenaltambe.vela.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.greenaltambe.vela"
            packageVersion = "1.0.0"
            // ChunkingProxy uses com.sun.net.httpserver, which a jlink-trimmed runtime would otherwise leave out.
            modules("jdk.httpserver")
        }
    }
}