plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.parcelize")
}

android {
    namespace = "com.sammy.running"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sammy.running"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.0"
        buildConfigField("String", "GITHUB_APP_CLIENT_ID", "\"Iv23liqtpl8mOCCTUekN\"")
    }
    buildFeatures { buildConfig = true }
    flavorDimensions += "source"
    productFlavors {
        create("demo") {
            dimension = "source"
            applicationIdSuffix = ".demo"
            resValue("string", "app_name", "Run Log Demo")
        }
        create("samsung") {
            dimension = "source"
            resValue("string", "app_name", "Run Log")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val sdkFiles = fileTree("libs") { include("*.aar") }
val checkSamsungSdk by tasks.registering {
    doLast {
        check(sdkFiles.files.size == 1) {
            "Download Samsung Health Data SDK 1.1.0 from https://developer.samsung.com/health/data/overview.html and place its single AAR in android/app/libs/. Demo builds do not require it."
        }
    }
}
tasks.matching { it.name == "preSamsungDebugBuild" || it.name == "preSamsungReleaseBuild" }
    .configureEach { dependsOn(checkSamsungSdk) }

dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.code.gson:gson:2.13.2")
    add("samsungImplementation", sdkFiles)
}
