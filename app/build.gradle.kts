plugins {
    alias(libs.plugins.android.application)
}

val nominatimUserAgent =
    (project.findProperty("NOMINATIM_USER_AGENT") as? String)
        ?.ifBlank { null }
        ?: "WeatherApp/1.0 (https://github.com/MSMD9/WEATHER; msmd9weatherapp@gmail.com)"

val nominatimReferer =
    (project.findProperty("NOMINATIM_REFERER") as? String)
        ?.ifBlank { null }
        ?: "https://github.com/MSMD9/WEATHER"

fun String.escapeForBuildConfig(): String = replace("\"", "\\\"")

android {
    namespace = "com.example.weather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.weather"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "NOMINATIM_USER_AGENT",
            "\"${nominatimUserAgent.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "NOMINATIM_REFERER",
            "\"${nominatimReferer.escapeForBuildConfig()}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}