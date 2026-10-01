plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version code from the version name (1.2.3 -> 10203), so every new version installs as an update.
val appVersion = (project.findProperty("versionName") as String?) ?: "0.8.0"
fun versionCodeOf(name: String): Int {
    val (major, minor, patch) = (name.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0))
    return major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.kingofthebeasts.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kingofthebeasts.app"
        minSdk = 24
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: versionCodeOf(appVersion)
        versionName = appVersion
    }

    signingConfigs {
        // A fixed key committed to the repo, so test builds from any machine or CI run install as updates
        // over each other. Real store releases use the private key from the CI secrets instead.
        getByName("debug") {
            storeFile = file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val keystore = System.getenv("ANDROID_KEYSTORE_PATH")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Dev builds say so in their version, e.g. 0.3.0-dev or 0.3.0-dev.57 on CI.
            versionNameSuffix = "-dev" + ((project.findProperty("devBuild") as String?)?.let { ".$it" } ?: "")
        }
        release {
            isMinifyEnabled = false
            // Signed with the release key when CI provides one, otherwise with the debug key
            // so the APK is still installable for testing.
            signingConfig = if (System.getenv("ANDROID_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Name the APK after the game and version instead of app-debug.apk / app-release.apk.
    applicationVariants.all {
        val apkVersion = versionName
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "KingOfTheBeasts-$apkVersion.apk"
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
        compose = true
    }
    testOptions {
        // Robolectric UI tests need the app's resources (fonts, art).
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
