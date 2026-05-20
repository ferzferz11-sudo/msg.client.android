plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
}

android {
    namespace = "lavender.client.android"
    compileSdk = 37

    defaultConfig {
        val versionFile = rootProject.file("version.txt")
        val versionFromFile = if (versionFile.exists()) versionFile.readText().trim() else "1.0.0"

        applicationId = "lavender.client.android"
        minSdk = 29
        targetSdk = 35
        versionName = versionFromFile

        // Generate versionCode from version parts (e.g., 1.0.2.16 -> 1000216)
        val parts = versionFromFile.split(".")
        versionCode = try {
            if (parts.size >= 3) {
                val major = parts[0].toInt()
                val minor = parts[1].toInt()
                val patch = parts[2].toInt()
                val build = if (parts.size > 3) parts[3].toInt() else 0
                major * 1000000 + minor * 10000 + patch * 100 + build
            } else 1
        } catch (e: Exception) { 1 }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add version as BuildConfig fields
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")

        // Add version as string resource for XML
        resValue("string", "app_version", "$versionName")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "lavender123"
            keyAlias = "lavender"
            keyPassword = "lavender123"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }
}



dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    
    // Firebase and FCM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // gRPC dependencies
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.javalite)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // OkHttp for HTTP client
    implementation(libs.okhttp)
    
    // CircleImageView for avatars
    implementation(libs.circleimageview)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Glide for image loading
    implementation(libs.glide)
    implementation(libs.glide.okhttp)
    ksp(libs.glide.compiler)
    
    // ExoPlayer for audio playback
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    implementation("androidx.media3:media3-common:1.10.0")
    
    // Biometric authentication
    implementation(libs.androidx.biometric)

    // WebRTC
    implementation("io.github.webrtc-sdk:android:144.7559.05")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
