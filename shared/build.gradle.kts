import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// Versions the app reports about itself come from the version catalog, never from a hand-edited constant.
val generatedVersionsDir = layout.buildDirectory.dir("generated/vela/commonMain")
val generateVersions = tasks.register("generateVersions") {
    val newpipe = libs.versions.newpipe.extractor.get()
    val released = libs.versions.newpipe.released.get()
    val ytdlp = libs.versions.ytdlp.verified.get()
    val out = generatedVersionsDir.map { it.file("com/greenaltambe/vela/health/GeneratedVersions.kt") }
    inputs.property("v", "$newpipe|$released|$ytdlp")
    outputs.file(out)
    doLast {
        out.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "package com.greenaltambe.vela.health\n\n/** Generated from gradle/libs.versions.toml by :shared:generateVersions. Do not edit. */\n" +
                "object GeneratedVersions {\n    const val NEWPIPE = \"$newpipe\"\n    const val NEWPIPE_RELEASED = \"$released\"\n    const val YTDLP_VERIFIED = \"$ytdlp\"\n}\n",
        )
    }
}

kotlin {
    jvm()
    
    android {
       namespace = "com.greenaltambe.vela.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        commonMain.configure { kotlin.srcDir(files(generatedVersionsDir).builtBy(generateVersions)) }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.androidx.activity.compose)
            implementation(libs.coil.networkOkhttp)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
            // DPAPI (Crypt32) for the Windows secret store.
            implementation(libs.jna.platform)
            implementation(libs.coil.networkOkhttp)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.sqldelight.runtime)
            implementation(libs.coil.compose)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.lucide.icons)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

sqldelight {
    databases {
        create("VelaDatabase") {
            packageName.set("com.greenaltambe.vela.data.db")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}