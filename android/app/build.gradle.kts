plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNum = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 10

android {
    namespace = "com.glimpse.files"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.glimpse.files"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNum
        versionName = "2.0.$buildNum"
    }

    signingConfigs {
        create("glimpse") {
            storeFile = file("glimpse-release.jks")
            storePassword = System.getenv("KEYSTORE_PASS") ?: "glimpse2026"
            keyAlias = System.getenv("KEY_ALIAS") ?: "glimpse"
            keyPassword = System.getenv("KEY_PASS") ?: "glimpse2026"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("glimpse")
        }
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("glimpse")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")
    implementation("org.slf4j:slf4j-nop:1.7.36")
}
