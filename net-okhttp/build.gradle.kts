import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

// The HttpTransport both apps use for the third-party APIs. Pure JVM so Android and desktop share one implementation.
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

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.okhttp.mockwebserver)
}

dependencies {
    add("liveTestImplementation", libs.kotlin.test)
    add("liveTestImplementation", libs.junit)
    add("liveTestImplementation", libs.kotlinx.coroutinesCore)
}
