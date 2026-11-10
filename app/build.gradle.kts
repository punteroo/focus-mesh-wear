// Module build configuration targeting API 35 for Google Play Store and Wear OS 5+ on Samsung Galaxy Watch Ultra.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.focusmesh.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focusmesh.wear"
        // Minimum SDK 30 covers Wear OS 3+, while targetSdk 35 satisfies Google Play API 35 requirements.
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    // Core AndroidX and lifecycle libraries for foreground service and ambient lifecycle tracking.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")

    // Jetpack Compose for Wear OS UI, foundation, and navigation primitives.
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    // Downloadable Google Fonts provider for runtime font switching on Wear OS.
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.8")

    // Wear OS Ongoing Activity API to render the active timer pill on the One UI Watch home screen.
    implementation("androidx.wear:wear-ongoing:1.0.0")

    // Wear OS Watch Face Complications API for native embeddable watch face complication slots.
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")

    // Core Material icons for native Wear OS Picker and ToggleChip controls.
    implementation("androidx.compose.material:material-icons-core")

    // Wear OS ambient mode support for battery conservation during Always-On Display.
    implementation("androidx.wear:wear:1.3.0")

    // Jetpack DataStore for persisting timer durations, grace settings, font, sound, and mesh options.
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
