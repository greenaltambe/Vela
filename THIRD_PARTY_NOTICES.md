# Third-party notices

Vela is licensed under the GNU General Public License v3.0 or later (see `LICENSE`). It builds on the open-source software below.
The dependency table is generated from the dependency graph that ships (desktop runtime classpath and Android release runtime classpath) by
`./gradlew generateThirdPartyNotices`; CI runs `./gradlew checkThirdPartyNotices` and fails when it is out of date. Licence names are the ones
the artifacts declare in their POM; the licence texts are in each project's repository.

## Notable components

| Component | Use | Licence |
|---|---|---|
| NewPipeExtractor (TeamNewPipe) | Anonymous YouTube access (tier 1) | GPL-3.0 |
| youtubedl-android (junkfood02 fork) | yt-dlp, Python and QuickJS on Android | GPL-3.0 |
| yt-dlp | Tier 2 on Linux/Windows (managed by the app, downloaded at run time, not part of this repository) | Unlicense |
| mpv / libmpv | Desktop playback (not bundled in this repository; installed from your distribution, or see `docs/BUILDING.md` for the Windows package) | GPL-2.0-or-later / LGPL-2.1-or-later (depending on the build) |
| FFmpeg | Optional, for merging downloads on desktop (not bundled) | LGPL-2.1-or-later / GPL-2.0-or-later (depending on the build) |
| AndroidX Media3 (ExoPlayer) | Android playback | Apache-2.0 |
| Jetpack Compose / Compose Multiplatform, Kotlin, kotlinx libraries | UI and language runtime | Apache-2.0 |
| OkHttp, Okio | HTTP | Apache-2.0 |
| SQLDelight | Local database | Apache-2.0 |
| Coil | Image loading | Apache-2.0 |
| JNA | Native calls (secure storage, mpv) | Apache-2.0 or LGPL-2.1-or-later |
| dbus-java (hypfvieh) | MPRIS media controls on Linux | MIT |
| Lucide icons (`com.composables:icons-lucide-cmp`) | One of the selectable icon packs | ISC (Lucide) and MIT (Feather portions) |
| Material Icons Extended | Filled, Rounded, Outlined, Sharp and Two-tone icon packs | Apache-2.0 |

## Fonts

Vela bundles no font files. It uses the platform's own fonts (the Font setting chooses between generic families).

## Icons and artwork

The Vela icon and the app's own line/filled icon set (`VelaIcons`) are original work of this project and are released under the same licence as the project.

## Generated dependency table

<!-- BEGIN GENERATED DEPENDENCIES -->
| Artifact | Version | Licence (as declared) |
|---|---|---|
| androidx.activity:activity | 1.13.0 | The Apache Software License, Version 2.0 |
| androidx.activity:activity-compose | 1.13.0 | The Apache Software License, Version 2.0 |
| androidx.activity:activity-ktx | 1.13.0 | The Apache Software License, Version 2.0 |
| androidx.annotation:annotation | 1.10.0 | The Apache Software License, Version 2.0 |
| androidx.annotation:annotation-experimental | 1.4.1 | The Apache Software License, Version 2.0 |
| androidx.annotation:annotation-jvm | 1.10.0 | The Apache Software License, Version 2.0 |
| androidx.appcompat:appcompat | 1.7.0 | The Apache Software License, Version 2.0 |
| androidx.appcompat:appcompat-resources | 1.7.0 | The Apache Software License, Version 2.0 |
| androidx.arch.core:core-common | 2.2.0 | The Apache Software License, Version 2.0 |
| androidx.arch.core:core-runtime | 2.2.0 | The Apache Software License, Version 2.0 |
| androidx.autofill:autofill | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.collection:collection | 1.5.0 | The Apache Software License, Version 2.0 |
| androidx.collection:collection-jvm | 1.5.0 | The Apache Software License, Version 2.0 |
| androidx.collection:collection-ktx | 1.5.0 | The Apache Software License, Version 2.0 |
| androidx.compose.animation:animation | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.animation:animation-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.animation:animation-core | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.animation:animation-core-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.foundation:foundation | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.foundation:foundation-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.foundation:foundation-layout | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.foundation:foundation-layout-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.material3:material3 | 1.5.0-alpha22 | The Apache Software License, Version 2.0 |
| androidx.compose.material3:material3-android | 1.5.0-alpha22 | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-icons-core | 1.7.6 | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-icons-core-android | 1.7.6 | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-icons-extended | 1.7.6 | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-icons-extended-android | 1.7.6 | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-ripple | 1.12.0-beta01 | The Apache Software License, Version 2.0 |
| androidx.compose.material:material-ripple-android | 1.12.0-beta01 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-annotation | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-annotation-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-annotation-jvm | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-retain | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-retain-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-retain-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-saveable | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-saveable-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.runtime:runtime-saveable-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-geometry | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-geometry-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-graphics | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-graphics-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-text | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-text-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-data | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-data-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-preview | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-tooling-preview-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-unit | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-unit-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-util | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.compose.ui:ui-util-android | 1.12.1 | The Apache Software License, Version 2.0 |
| androidx.concurrent:concurrent-futures | 1.2.0 | The Apache Software License, Version 2.0 |
| androidx.concurrent:concurrent-futures-ktx | 1.2.0 | The Apache Software License, Version 2.0 |
| androidx.core:core | 1.18.0 | The Apache Software License, Version 2.0 |
| androidx.core:core-ktx | 1.18.0 | The Apache Software License, Version 2.0 |
| androidx.core:core-viewtree | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.cursoradapter:cursoradapter | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.customview:customview | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.customview:customview-poolingcontainer | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.documentfile:documentfile | 1.0.1 | The Apache Software License, Version 2.0 |
| androidx.drawerlayout:drawerlayout | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.emoji2:emoji2 | 1.4.0 | The Apache Software License, Version 2.0 |
| androidx.emoji2:emoji2-views-helper | 1.4.0 | The Apache Software License, Version 2.0 |
| androidx.exifinterface:exifinterface | 1.4.1 | The Apache Software License, Version 2.0 |
| androidx.fragment:fragment | 1.5.4 | The Apache Software License, Version 2.0 |
| androidx.graphics:graphics-path | 1.0.1 | The Apache Software License, Version 2.0 |
| androidx.graphics:graphics-shapes | 1.1.0 | The Apache Software License, Version 2.0 |
| androidx.graphics:graphics-shapes-android | 1.1.0 | The Apache Software License, Version 2.0 |
| androidx.graphics:graphics-shapes-desktop | 1.1.0 | The Apache Software License, Version 2.0 |
| androidx.interpolator:interpolator | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-common | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-common-java8 | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-common-jvm | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-livedata | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-livedata-core | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-livedata-core-ktx | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-process | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-android | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-compose | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-compose-android | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-compose-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-runtime-ktx-android | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-service | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-android | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose-android | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-savedstate | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-savedstate-android | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.lifecycle:lifecycle-viewmodel-savedstate-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| androidx.loader:loader | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-common | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-container | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-database | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-datasource | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-decoder | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-exoplayer | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-exoplayer-hls | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-extractor | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-session | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media3:media3-ui | 1.11.1 | The Apache Software License, Version 2.0 |
| androidx.media:media | 1.7.0 | The Apache Software License, Version 2.0 |
| androidx.navigationevent:navigationevent | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.navigationevent:navigationevent-android | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.navigationevent:navigationevent-compose | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.navigationevent:navigationevent-compose-android | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.navigationevent:navigationevent-compose-desktop | 1.1.1 | The Apache Software License, Version 2.0 |
| androidx.navigationevent:navigationevent-desktop | 1.1.1 | The Apache Software License, Version 2.0 |
| androidx.profileinstaller:profileinstaller | 1.4.1 | The Apache Software License, Version 2.0 |
| androidx.recyclerview:recyclerview | 1.3.0 | The Apache Software License, Version 2.0 |
| androidx.resourceinspection:resourceinspection-annotation | 1.0.1 | The Apache Software License, Version 2.0 |
| androidx.room:room-common | 2.6.1 | The Apache Software License, Version 2.0 |
| androidx.room:room-ktx | 2.6.1 | The Apache Software License, Version 2.0 |
| androidx.room:room-runtime | 2.6.1 | The Apache Software License, Version 2.0 |
| androidx.savedstate:savedstate | 1.4.0 | The Apache Software License, Version 2.0 |
| androidx.savedstate:savedstate-android | 1.4.0 | The Apache Software License, Version 2.0 |
| androidx.savedstate:savedstate-compose | 1.4.0 | The Apache Software License, Version 2.0 |
| androidx.savedstate:savedstate-compose-android | 1.4.0 | The Apache Software License, Version 2.0 |
| androidx.savedstate:savedstate-compose-desktop | 1.4.0 | The Apache Software License, Version 2.0 |
| androidx.savedstate:savedstate-desktop | 1.4.0 | The Apache Software License, Version 2.0 |
| androidx.savedstate:savedstate-ktx | 1.4.0 | The Apache Software License, Version 2.0 |
| androidx.sqlite:sqlite | 2.7.1 | The Apache Software License, Version 2.0 |
| androidx.sqlite:sqlite-android | 2.7.1 | The Apache Software License, Version 2.0 |
| androidx.sqlite:sqlite-framework | 2.7.1 | The Apache Software License, Version 2.0 |
| androidx.sqlite:sqlite-framework-android | 2.7.1 | The Apache Software License, Version 2.0 |
| androidx.startup:startup-runtime | 1.2.0 | The Apache Software License, Version 2.0 |
| androidx.tracing:tracing | 1.2.0 | The Apache Software License, Version 2.0 |
| androidx.tracing:tracing-ktx | 1.2.0 | The Apache Software License, Version 2.0 |
| androidx.vectordrawable:vectordrawable | 1.1.0 | The Apache Software License, Version 2.0 |
| androidx.vectordrawable:vectordrawable-animated | 1.1.0 | The Apache Software License, Version 2.0 |
| androidx.versionedparcelable:versionedparcelable | 1.1.1 | The Apache Software License, Version 2.0 |
| androidx.viewpager:viewpager | 1.0.0 | The Apache Software License, Version 2.0 |
| androidx.window:window | 1.5.0 | The Apache Software License, Version 2.0 |
| androidx.window:window-core | 1.5.0 | The Apache Software License, Version 2.0 |
| androidx.window:window-core-android | 1.5.0 | The Apache Software License, Version 2.0 |
| androidx.work:work-runtime | 2.10.1 | The Apache Software License, Version 2.0 |
| androidx.work:work-runtime-ktx | 2.10.1 | The Apache Software License, Version 2.0 |
| app.cash.sqldelight:android-driver | 2.4.1 | Apache-2.0 |
| app.cash.sqldelight:jdbc-driver | 2.4.1 | Apache-2.0 |
| app.cash.sqldelight:runtime | 2.4.1 | Apache-2.0 |
| app.cash.sqldelight:runtime-jvm | 2.4.1 | Apache-2.0 |
| app.cash.sqldelight:sqlite-driver | 2.4.1 | Apache-2.0 |
| com.composables:icons-lucide-cmp | 2.2.1 | MIT License |
| com.composables:icons-lucide-cmp-android | 2.2.1 | MIT License |
| com.composables:icons-lucide-cmp-jvm | 2.2.1 | MIT License |
| com.fasterxml.jackson.core:jackson-annotations | 2.11.1 | The Apache Software License, Version 2.0 |
| com.fasterxml.jackson.core:jackson-core | 2.11.1 | The Apache Software License, Version 2.0 |
| com.fasterxml.jackson.core:jackson-databind | 2.11.1 | The Apache Software License, Version 2.0 |
| com.github.TeamNewPipe:NewPipeExtractor | v0.26.5 | GNU General Public License v3.0 or later |
| com.github.TeamNewPipe:nanojson | e9d656ddb49a412a5a0a5d5ef20ca7ef09549996 | Apache-2.0 (not in the POM; upstream grack/nanojson states Apache-2.0, to be confirmed) |
| com.github.hypfvieh:dbus-java-core | 5.1.1 | MIT License |
| com.github.hypfvieh:dbus-java-transport-native-unixsocket | 5.1.1 | MIT License |
| com.google.accompanist:accompanist-drawablepainter | 0.37.3 | The Apache Software License, Version 2.0 |
| com.google.code.findbugs:jsr305 | 3.0.2 | The Apache Software License, Version 2.0 |
| com.google.guava:failureaccess | 1.0.2 | The Apache Software License, Version 2.0 |
| com.google.guava:guava | 33.3.1-android | Apache-2.0 (not in the POM; stated by the project) |
| com.google.guava:listenablefuture | 9999.0-empty-to-avoid-conflict-with-guava | The Apache Software License, Version 2.0 |
| com.google.protobuf:protobuf-javalite | 4.35.1 | BSD-3-Clause |
| com.squareup.okhttp3:okhttp | 5.5.1 | The Apache Software License, Version 2.0 |
| com.squareup.okhttp3:okhttp-android | 5.5.1 | The Apache Software License, Version 2.0 |
| com.squareup.okhttp3:okhttp-jvm | 5.5.1 | The Apache Software License, Version 2.0 |
| com.squareup.okio:okio | 3.18.1 | The Apache Software License, Version 2.0 |
| com.squareup.okio:okio-jvm | 3.18.1 | The Apache Software License, Version 2.0 |
| commons-io:commons-io | 2.5 | Apache License, Version 2.0 |
| io.coil-kt.coil3:coil | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-android | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-compose | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-compose-android | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-compose-core | 3.2.0 | Apache-2.0 (not in the POM; stated by the project) |
| io.coil-kt.coil3:coil-compose-core-android | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-compose-core-jvm | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-compose-jvm | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-core | 3.2.0 | Apache-2.0 (not in the POM; stated by the project) |
| io.coil-kt.coil3:coil-core-android | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-core-jvm | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-jvm | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-network-core | 3.2.0 | Apache-2.0 (not in the POM; stated by the project) |
| io.coil-kt.coil3:coil-network-core-android | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-network-core-jvm | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-network-okhttp | 3.2.0 | The Apache License, Version 2.0 |
| io.coil-kt.coil3:coil-network-okhttp-jvm | 3.2.0 | The Apache License, Version 2.0 |
| io.github.junkfood02.youtubedl-android:common | 0.18.1 | GPL-3.0 license |
| io.github.junkfood02.youtubedl-android:library | 0.18.1 | GPL-3.0 license |
| net.java.dev.jna:jna | 5.17.0 | LGPL-2.1-or-later; Apache-2.0 |
| org.apache.commons:commons-compress | 1.12 | Apache License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-common | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-common-jvm | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime-android | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-android | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-android | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose-android | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate-android | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate-desktop | 2.11.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.navigationevent:navigationevent-compose | 1.1.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.navigationevent:navigationevent-compose-desktop | 1.1.0 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.savedstate:savedstate | 1.3.6 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.savedstate:savedstate-compose | 1.3.6 | The Apache Software License, Version 2.0 |
| org.jetbrains.androidx.savedstate:savedstate-compose-desktop | 1.3.6 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.animation:animation | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.animation:animation-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.animation:animation-core | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.animation:animation-core-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.animation:animation-core-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.animation:animation-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.components:components-resources | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.components:components-resources-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.components:components-resources-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.desktop:desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.desktop:desktop-jvm | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.desktop:desktop-jvm-linux-x64 | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.foundation:foundation | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.foundation:foundation-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.foundation:foundation-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.foundation:foundation-layout | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.foundation:foundation-layout-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.foundation:foundation-layout-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material3:material3 | 1.12.0-alpha03 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material3:material3-android | 1.12.0-alpha03 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material3:material3-desktop | 1.12.0-alpha03 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material:material | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material:material-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material:material-icons-core | 1.7.3 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material:material-icons-core-desktop | 1.7.3 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material:material-icons-extended | 1.7.3 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material:material-icons-extended-desktop | 1.7.3 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material:material-ripple | 1.12.0-beta01 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material:material-ripple-android | 1.12.0-beta01 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.material:material-ripple-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.runtime:runtime | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.runtime:runtime-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.runtime:runtime-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.runtime:runtime-saveable | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.runtime:runtime-saveable-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.runtime:runtime-saveable-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-backhandler | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-backhandler-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-geometry | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-geometry-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-geometry-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-graphics | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-graphics-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-graphics-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-text | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-text-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-text-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-tooling | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-tooling-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-tooling-data | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-tooling-data-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-tooling-preview | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-tooling-preview-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-tooling-preview-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-unit | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-unit-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-unit-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-util | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-util-android | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.compose.ui:ui-util-desktop | 1.12.1 | The Apache Software License, Version 2.0 |
| org.jetbrains.kotlin:kotlin-stdlib | 2.4.20 | Apache-2.0 |
| org.jetbrains.kotlin:kotlin-stdlib-common | 2.4.20 | Apache-2.0 |
| org.jetbrains.kotlin:kotlin-stdlib-jdk7 | 1.8.22 | The Apache License, Version 2.0 |
| org.jetbrains.kotlin:kotlin-stdlib-jdk8 | 1.8.22 | The Apache License, Version 2.0 |
| org.jetbrains.kotlinx:atomicfu | 0.28.0 | Apache-2.0 |
| org.jetbrains.kotlinx:atomicfu-jvm | 0.28.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-bom | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-swing | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-datetime | 0.7.1 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-datetime-jvm | 0.7.1 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-bom | 1.12.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-core | 1.12.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-core-jvm | 1.12.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.12.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-json-jvm | 1.12.0 | Apache-2.0 |
| org.jetbrains.runtime:jbr-api | 1.9.0 | The Apache License, Version 2.0 |
| org.jetbrains.skiko:skiko | 0.150.1 | The Apache License, Version 2.0 |
| org.jetbrains.skiko:skiko-awt | 0.150.1 | The Apache License, Version 2.0 |
| org.jetbrains.skiko:skiko-awt-runtime-linux-x64 | 0.150.1 | The Apache License, Version 2.0 |
| org.jetbrains:annotations | 23.0.0 | The Apache Software License, Version 2.0 |
| org.jsoup:jsoup | 1.22.2 | The MIT License |
| org.jspecify:jspecify | 1.0.0 | The Apache License, Version 2.0 |
| org.mozilla:rhino | 1.8.1 | Mozilla Public License, Version 2.0 |
| org.mozilla:rhino-engine | 1.8.1 | Mozilla Public License, Version 2.0 |
| org.slf4j:slf4j-api | 2.0.16 | MIT License |
| org.slf4j:slf4j-nop | 2.0.16 | MIT License |
| org.xerial:sqlite-jdbc | 3.53.4.0 | The Apache Software License, Version 2.0 |
<!-- END GENERATED DEPENDENCIES -->
