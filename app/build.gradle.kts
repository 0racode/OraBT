import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.pastq.cbt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pastq.cbt"
        minSdk = 24
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String? ?: "1").toInt()
        versionName = project.findProperty("versionName") as String? ?: "1.0.0"

        // Put ALOC_TOKEN=your_token in local.properties (never commit it)
        buildConfigField("String", "ALOC_TOKEN", "\"${localProps.getProperty("ALOC_TOKEN", "")}\"")
        // Public, read-only access to the question library (row-level security blocks writes)
        buildConfigField("String", "SUPABASE_URL", "\"https://wyzswbklytklzbcmihsh.supabase.co/\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_E21JIx3tbNTX9K3bhR6Ieg_fZRL1xqj\"")
        buildConfigField("String", "UPDATE_OWNER", "\"0racode\"")
        buildConfigField("String", "UPDATE_REPO", "\"OraBT\"")
        buildConfigField("String", "ALOC_BASE_URL", "\"https://questions.aloc.com.ng/api/v2/\"")
    }

    signingConfigs {
        create("release") {
            // Provided by CI from GitHub secrets. Without them, release builds fall back to the debug key.
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null && file(ks).exists() && !System.getenv("KEYSTORE_PASSWORD").isNullOrEmpty()) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else signingConfigs.getByName("debug")
        }
    }

    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Push notices for live sessions
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")

    // Question pictures (diagrams, graphs) with an on-phone cache
    implementation("io.coil-kt:coil-compose:2.7.0")
}
