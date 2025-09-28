import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.playPublisher)
}

android {
    namespace = "com.ausgetrunken"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ausgetrunken"
        minSdk = 31
        //noinspection EditedTargetSdkVersion
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.3-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Load keystore properties from secure file (not committed to git)
            val keystorePropertiesFile = rootProject.file("project-docs/certificates/keystore.properties")
            val keystoreProperties = Properties()
            
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            } else {
                // Fallback to environment variables (useful for CI/CD)
                storeFile = file(System.getenv("KEYSTORE_FILE") ?: "../ausgetrunken-release.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: throw GradleException("Keystore password not found. Create keystore.properties or set KEYSTORE_PASSWORD environment variable.")
                keyAlias = System.getenv("KEY_ALIAS") ?: "ausgetrunken-key"
                keyPassword = System.getenv("KEY_PASSWORD") ?: throw GradleException("Key password not found. Create keystore.properties or set KEY_PASSWORD environment variable.")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Google Play Console publishing configuration
play {
    // Load service account key from secure file (not committed to git)
    val playConfigFile = rootProject.file("play-config.properties")
    val playConfig = Properties()
    
    if (playConfigFile.exists()) {
        playConfig.load(FileInputStream(playConfigFile))
        serviceAccountCredentials.set(file(playConfig["serviceAccountJsonFile"] as String))
    } else {
        // Fallback to environment variable (useful for CI/CD)
        val serviceAccountJson = System.getenv("PLAY_SERVICE_ACCOUNT_JSON")
        if (serviceAccountJson != null) {
            serviceAccountCredentials.set(file(serviceAccountJson))
        }
    }
    
    defaultToAppBundles.set(true)
    track.set("beta") // Beta testing track
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation(libs.androidx.activity.compose)
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    
    // Supabase (compatible with Kotlin 2.0)
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.6.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.6.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.6.0")
    implementation("io.github.jan-tennert.supabase:realtime-kt:2.6.0")
    implementation("io.github.jan-tennert.supabase:functions-kt:2.6.0")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Google Play Services
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    
    // Google Maps Compose
    implementation("com.google.maps.android:maps-compose:4.4.1")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Gson
    implementation(libs.gson)
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)
    
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Foundation for HorizontalPager
    implementation("androidx.compose.foundation:foundation:1.7.6")
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}