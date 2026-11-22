import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

// Pure JVM (java.net only, no UPnP library): shared by :androidApp and :desktopApp. Casting is DLNA/UPnP AVTransport.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    api(project(":shared"))
    implementation(libs.kotlinx.coroutinesCore)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

tasks.test { useJUnit() }
