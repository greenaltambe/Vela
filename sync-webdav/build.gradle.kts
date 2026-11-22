import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

// Pure JVM (java.net only): shared by :androidApp and :desktopApp. Sync through a WebDAV folder (Nextcloud, Koofr, Synology...), over OkHttp.
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
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutinesCore)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test { useJUnit() }
