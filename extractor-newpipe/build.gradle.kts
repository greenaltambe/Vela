import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

// Android consumes this module, so keep bytecode and Java API level at what NewPipeExtractor itself targets.
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
    api(libs.newpipe.extractor)
    implementation(libs.newpipe.nanojson)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.kotlinx.serializationJson)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

tasks.test { useJUnit() }
