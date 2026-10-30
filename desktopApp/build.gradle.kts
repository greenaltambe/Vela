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
    implementation(project(":innertube-auth"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    // MPRIS (media keys, GNOME/KDE media controls) over the session D-Bus.
    implementation("com.github.hypfvieh:dbus-java-core:5.1.1")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.16")

    implementation(libs.compose.uiToolingPreview)
}

apply(from = rootProject.file("gradle/live-test.gradle.kts"))

dependencies {
    // Offline UI tests: the real screens rendered at phone / tablet / desktop sizes with fake data (screenshots + layout assertions).
    testImplementation(compose.desktop.currentOs)
    testImplementation(compose.uiTest)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.kotlinx.serializationJson)
}

tasks.withType<Test>().matching { it.name == "test" }.configureEach {
    // Where the screenshot tests write their PNGs: the reports link them locally; on CI (env CI set) they go to the build folder so the tree stays clean.
    systemProperty("vela.screens.dir", if (System.getenv("CI") != null) layout.buildDirectory.dir("screens/12-screens").get().asFile.path else rootProject.file("docs/reports/12-screens").path)
}

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

// One version for everything (version.properties): the packages carry the numeric part, e.g. 0.1.0 for 0.1.0-beta.1.
val appVersion = VelaVersion.read(rootProject.file("version.properties"))

// ./gradlew :desktopApp:smokeTest  starts the real runtime without a window, prints what it found and exits 0 (or non-zero with a message).
tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Starts the real desktop runtime without a window (database, settings, extractor chain, yt-dlp / libmpv lookup) and exits."
    classpath = sourceSets.getByName("main").runtimeClasspath
    mainClass = "com.greenaltambe.vela.MainKt"
    args("--smoke-test")
}

// Linux packages WITH dependencies and a correct menu entry. Compose Desktop's packageDeb/packageRpm cannot declare runtime packages or the window class, so
// the already-built app image (createDistributable) is wrapped by jpackage directly:
//   ./gradlew :desktopApp:packageLinux  ->  build/linux-packages/vela_<ver>-1_amd64.deb (needs dpkg-deb) and vela-<ver>-1.x86_64.rpm (needs rpmbuild)
// The .deb depends on libmpv2 (Ubuntu 22.04 ships libmpv1 instead: install that by hand, see docs/BUILDING.md); the .rpm on mpv-libs (Fedora).
val linuxPackageDir = layout.buildDirectory.dir("linux-packages")
tasks.register<Exec>("packageLinux") {
    group = "distribution"
    description = "Builds the .deb (if dpkg-deb exists) and .rpm (if rpmbuild exists) with runtime dependencies from the createDistributable app image."
    dependsOn("createDistributable")
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/vela")
    val jpackage = File(System.getProperty("java.home"), "bin/jpackage").path
    val resources = rootProject.file("packaging/linux").path
    val license = rootProject.file("LICENSE").path
    val out = linuxPackageDir.get().asFile
    val ver = VelaVersion.packageVersion(appVersion)
    commandLine("bash", "-c", """
        set -euo pipefail
        mkdir -p '${out.path}'
        common=( --app-image '${appImage.get().asFile.path}' --name vela --app-version '$ver' --dest '${out.path}' --resource-dir '$resources'
                 --description 'A YouTube client that keeps working: several sources for every video, your data on your device.'
                 --vendor 'Vela contributors' --license-file '$license' --linux-shortcut --linux-menu-group 'AudioVideo;Video;' --linux-app-category AudioVideo )
        if command -v dpkg-deb >/dev/null; then '$jpackage' --type deb "${'$'}{common[@]}" --linux-package-deps 'libmpv2 | libmpv1' --linux-deb-maintainer 'Vela contributors <noreply@users.noreply.github.com>'; else echo 'dpkg-deb not found: .deb skipped'; fi
        if command -v rpmbuild >/dev/null; then '$jpackage' --type rpm "${'$'}{common[@]}" --linux-package-deps 'mpv-libs' --linux-rpm-license-type 'GPL-3.0-or-later'; else echo 'rpmbuild not found: .rpm skipped'; fi
    """.trimIndent())
}

// Portable archives of the same app image: Vela-<version>-linux-x64.tar.gz and Vela-<version>-windows-x64.zip (unpack and run, no installer).
tasks.register<Tar>("portableTarball") {
    group = "distribution"
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app/vela")) { into("Vela-$appVersion") }
    compression = Compression.GZIP
    // Executable bits are not carried over reliably from the app image: set them on the files that must run.
    eachFile {
        if (path.endsWith("/bin/vela") || path.endsWith("/lib/runtime/lib/jexec") || path.endsWith("/lib/runtime/lib/jspawnhelper") || path.endsWith("/lib/libapplauncher.so")) permissions { unix("rwxr-xr-x") }
    }
    archiveFileName.set("Vela-$appVersion-linux-x64.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("portable"))
}
tasks.register<Zip>("portableZip") {
    group = "distribution"
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app/vela")) { into("Vela-$appVersion") }
    archiveFileName.set("Vela-$appVersion-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("portable"))
}

compose.desktop {
    application {
        mainClass = "com.greenaltambe.vela.MainKt"
        // The window class (X11 / XWayland) is set to "Vela" in main(), matching StartupWMClass of the menu entry; that needs this module opened.
        jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"

        nativeDistributions {
            // Linux: .deb and .rpm; Windows: .msi. (No macOS target.) createDistributable gives the portable folder on both.
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi)
            packageName = "vela"
            packageVersion = VelaVersion.packageVersion(appVersion)
            description = "A YouTube client that keeps working: several sources for every video, your data on your device."
            vendor = "Vela contributors"
            copyright = "Copyright (C) Vela contributors. GPL-3.0-or-later."
            licenseFile.set(rootProject.file("LICENSE"))
            // The jlink-trimmed runtime left out java.net.http (the smoke test of the packaged app caught it) and would also drop jdk.httpserver (ChunkingProxy),
            // java.sql, the TLS curves (jdk.crypto.ec) and more that are only reached through reflection. Include every module: about 40 MB larger, but nothing missing at run time.
            includeAllModules = true
            // Files per platform (appResources/windows-x64/libmpv-2.dll is fetched by the release workflow, see docs/BUILDING.md); empty in the repository.
            appResourcesRootDir.set(layout.projectDirectory.dir("appResources"))

            linux {
                iconFile.set(rootProject.file("docs/icon/vela-icon-1024.png"))
                menuGroup = "AudioVideo;Video;"
                appCategory = "AudioVideo"
                debMaintainer = "Vela contributors <noreply@users.noreply.github.com>"
                // Debian/Ubuntu 24.04: libmpv2 (22.04 ships libmpv1: install that instead). Fedora: mpv-libs (see the rpm requires below).
                debPackageVersion = VelaVersion.packageVersion(appVersion)
                rpmPackageVersion = VelaVersion.packageVersion(appVersion)
                rpmLicenseType = "GPL-3.0-or-later"
                shortcut = true
            }
            windows {
                iconFile.set(rootProject.file("docs/icon/vela-icon.ico"))
                menuGroup = "Vela"
                shortcut = true
                menu = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "6d2f6f1c-3a52-4e1d-9d0b-5f6c7c4a1b21"
            }
        }
    }
}