plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.gradleProperty("FOREST_RUN_KEYSTORE").orNull
    ?: providers.environmentVariable("FOREST_RUN_KEYSTORE").orNull
val releaseStorePassword = providers.gradleProperty("FOREST_RUN_STORE_PASSWORD").orNull
    ?: providers.environmentVariable("FOREST_RUN_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("FOREST_RUN_KEY_ALIAS").orNull
    ?: providers.environmentVariable("FOREST_RUN_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("FOREST_RUN_KEY_PASSWORD").orNull
    ?: providers.environmentVariable("FOREST_RUN_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.anurag9000.forestrun"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anurag9000.forestrun"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // No Compose – pure SurfaceView / Canvas game
    buildFeatures {
        compose = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.16")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
