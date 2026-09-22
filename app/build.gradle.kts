plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val haruKeystorePath = System.getenv("HARU_KEYSTORE_PATH")
val haruStorePassword = System.getenv("HARU_STORE_PASSWORD")
val haruKeyPassword = System.getenv("HARU_KEY_PASSWORD")
val haruKeyAlias = System.getenv("HARU_KEY_ALIAS")
val haruReleaseRequired = System.getenv("HARU_RELEASE_REQUIRED").equals("true", ignoreCase = true)

android {
    namespace = "io.haru.assistant"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.haru.assistant"
        minSdk = 26
        targetSdk = 36
        versionCode = 42
        versionName = "0.9.7"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (
            !haruKeystorePath.isNullOrBlank() &&
            !haruStorePassword.isNullOrBlank() &&
            !haruKeyPassword.isNullOrBlank() &&
            !haruKeyAlias.isNullOrBlank()
        ) {
            create("haruRelease") {
                storeFile = file(haruKeystorePath)
                storePassword = haruStorePassword
                keyAlias = haruKeyAlias
                keyPassword = haruKeyPassword
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (haruReleaseRequired) {
                signingConfigs.getByName("haruRelease")
            } else {
                signingConfigs.findByName("haruRelease") ?: signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            )
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.maplibre.gl:android-sdk:11.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
