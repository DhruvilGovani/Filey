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
        versionName = "1.0.$buildNum"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")
    implementation("org.slf4j:slf4j-nop:1.7.36")
}
