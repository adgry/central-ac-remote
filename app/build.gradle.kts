import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/*
 * Release signing. Reads keystore.properties locally (gitignored), or the same
 * four values from the environment in CI. Without them a release build comes out
 * unsigned rather than falling back to the debug key — a debug-signed "release"
 * would silently fail to install over a properly signed one, which is a much
 * worse problem than an obvious missing signature.
 */
val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStore = signingValue("storeFile", "KEYSTORE_FILE")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.hvacpanel"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hvacpanel"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"

        // Where the in-app updater looks for newer builds.
        buildConfigField("String", "UPDATE_REPO", "\"adgry/central-ac-remote\"")
    }

    signingConfigs {
        create("release") {
            if (releaseStore != null) {
                storeFile = releaseStore
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
