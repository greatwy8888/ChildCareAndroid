plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.childcare"
    compileSdk = 35
    defaultConfig {
        // Isolated package: this test build must never replace the installed Device Owner app.
        applicationId = "com.example.childcare.next"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    testImplementation("junit:junit:4.13.2")
}
