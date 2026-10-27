import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

// Desktop only: libmpv is a native library, so :androidApp must never depend on this. Pure JVM, no Compose.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

apply(from = rootProject.file("gradle/live-test.gradle.kts"))

dependencies {
    api(project(":shared"))
    implementation(libs.jna)
    implementation(libs.kotlinx.coroutinesCore)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

tasks.test { useJUnit() }

// Live tests resolve real streams with NewPipe; not part of `check`.
dependencies {
    add("liveTestImplementation", project(":extractor-newpipe"))
}
