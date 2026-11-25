plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.glimpse.files"
    compileSdk = 34
    defaultConfig { applicationId = "com.glimpse.files"; minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/INDEX.LIST") } }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")
    implementation("org.slf4j:slf4j-android:1.7.36")
}
