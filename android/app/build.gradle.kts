plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 36
    namespace = "org.repro.taoraw"
    defaultConfig {
        applicationId = "org.repro.taoraw"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            packaging { jniLibs.keepDebugSymbols.add("*/arm64-v8a/*.so") }
        }
    }
    kotlinOptions { jvmTarget = "1.8" }
}
