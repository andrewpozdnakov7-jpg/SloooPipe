// SPDX-License-Identifier: GPL-3.0-or-later
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.auth.library)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
}

group = "net.newpipe"
version = "212ca1c8d941"

android {
    namespace = "net.newpipe.newplayer"
    compileSdk {
        version = release(NEWPIPE_VERSION_SDK_COMPILE_MAJOR) {
            minorApiLevel = NEWPIPE_VERSION_SDK_COMPILE_MINOR
        }
    }
    buildToolsVersion = "36.0.0"
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar)
    implementation(newplayerLibs.androidx.core.ktx)
    implementation(newplayerLibs.androidx.appcompat)
    implementation(newplayerLibs.androidx.activity.compose)
    implementation(newplayerLibs.material)
    implementation(newplayerLibs.androidx.constraintlayout)
    implementation(newplayerLibs.androidx.constraintlayout.compose)
    implementation(newplayerLibs.androidx.material3)
    implementation(newplayerLibs.androidx.ui.tooling)
    implementation(newplayerLibs.androidx.material.icons.extended.android)
    api(newplayerLibs.androidx.media3.exoplayer)
    api(newplayerLibs.androidx.media3.common)
    api(newplayerLibs.androidx.media3.datasource)
    implementation(newplayerLibs.androidx.media3.ui)
    implementation(newplayerLibs.androidx.media3.ui.compose)
    api(newplayerLibs.androidx.lifecycle.viewmodel.compose)
    implementation(newplayerLibs.androidx.foundation)
    implementation(newplayerLibs.androidx.fragment.ktx)
    api(newplayerLibs.androidx.lifecycle.runtime.ktx)
    implementation(newplayerLibs.androidx.lifecycle.runtime.compose)
    implementation(platform(newplayerLibs.androidx.compose.bom))
    implementation(newplayerLibs.androidx.ui)
    implementation(newplayerLibs.androidx.ui.graphics)
    implementation(newplayerLibs.androidx.ui.tooling.preview)
    implementation(newplayerLibs.coil.compose)
    implementation(newplayerLibs.reorderable)
    implementation(newplayerLibs.androidx.media3.session)
    implementation(newplayerLibs.androidx.media3.exoplayer.dash)
    implementation(newplayerLibs.androidx.adaptive.android)
}
