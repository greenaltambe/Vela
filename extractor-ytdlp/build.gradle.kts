import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

// Desktop only: yt-dlp is an external process and cannot run on Android, so :androidApp must never depend on this.
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
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.kotlinx.serializationJson)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

tasks.test { useJUnit() }
