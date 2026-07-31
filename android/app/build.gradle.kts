import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

// Load signing secrets from android/keystore.properties (gitignored). Absent on
// machines/CI without secrets → release falls back to debug signing for local builds.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}
val hasReleaseSigning = keystorePropsFile.exists()

// Optional dev override for the debug API URL, read from android/local.properties
// (gitignored). Set `api.base.url=...` there to point the debug build at the real
// API or your machine's LAN IP, without editing any versioned file.
// Falls back to the emulator → host alias (10.0.2.2) when unset.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val debugApiBaseUrl: String =
    localProps.getProperty("api.base.url") ?: "http://10.0.2.2:3000/api/v1"

android {
    namespace = "com.whocalled.android"
    compileSdk = 37

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        // Public Play Store application id. The Kotlin namespace stays
        // com.whocalled.android to avoid a noisy source-package migration.
        applicationId = "com.devfi.whocalled"
        minSdk = 29
        targetSdk = 37
        versionCode = 10
        versionName = "0.3.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API base URL — override per build type / .env later.
        buildConfigField("String", "API_BASE_URL", "\"https://api.who-called.com/api/v1\"")

        // Public links — change these in one place (acts as our ".env").
        buildConfigField("String", "SITE_URL", "\"https://www.who-called.com\"")
        buildConfigField("String", "PRIVACY_URL", "\"https://www.who-called.com/privacy\"")
        buildConfigField("String", "POLICY_URL", "\"https://www.who-called.com/policy\"")
        buildConfigField("String", "REPO_URL", "\"https://github.com/who-called/android-ios-web\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"contact@who-called.com\"")
        buildConfigField("String", "RATE_URL", "\"https://www.who-called.com\"")
        // Official government source for the telemarketing prefixes (Play policy:
        // government info requires a visible link to the original source).
        buildConfigField(
            "String",
            "ARCEP_SOURCE_URL",
            "\"https://www.arcep.fr/la-regulation/grands-dossiers-thematiques-transverses/la-numerotation.html\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign with the release key when keystore.properties is present.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
            // Default: local backend (emulator → host = 10.0.2.2). Override via
            // `api.base.url` in local.properties (e.g. the real API, or your LAN IP).
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.gson)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
}
