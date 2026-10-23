plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jilue.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jilue.game"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    // 签名：仅在 CI 提供 KEYSTORE_BASE64 等环境变量时启用
    signingConfigs {
        create("release") {
            val b64 = System.getenv("KEYSTORE_BASE64")
            if (b64 != null) {
                val ks = File(rootProject.projectDir, "release.keystore")
                ks.outputStream().use { it.write(java.util.Base64.getDecoder().decode(b64)) }
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = if (System.getenv("KEYSTORE_BASE64") != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // 轻量内嵌 HTTP 服务，用于本地托管网页引擎 + 已下载的资源
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
