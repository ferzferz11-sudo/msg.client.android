plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "msg.client.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "msg.client.android"
        minSdk = 29
        targetSdk = 37
        versionCode = 12
        versionName = "0.9.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Add version as BuildConfig fields
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        buildConfigField("int", "VERSION_CODE", "$versionCode")
        
        // Add version as string resource for XML
        resValue("string", "app_version", "$versionName")
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
    
    // Add gRPC dependencies gradually
    // gRPC dependencies
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.javalite)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // OkHttp for HTTP client
    implementation(libs.okhttp)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

