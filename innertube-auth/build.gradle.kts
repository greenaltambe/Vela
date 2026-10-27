import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

// Thin own Innertube client for account data and actions, plus the alternate-client player. Pure JVM so Android and desktop share it.
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
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutinesCore)
    api(libs.kotlinx.serializationJson)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test { useJUnit() }
