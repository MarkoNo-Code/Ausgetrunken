// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
    alias(libs.plugins.ksp) apply false
}

// Convenience tasks for development
tasks.register("buildAndInstall") {
    description = "Build and install debug APK on connected device"
    group = "ausgetrunken"
    dependsOn(":app:installDebug")
}

tasks.register("quickStart") {
    description = "Clean, build, install and start the app"
    group = "ausgetrunken"
    dependsOn("clean", ":app:installDebug")
    doLast {
        exec {
            commandLine("adb", "shell", "am", "start", "-n", "com.ausgetrunken/.MainActivity")
        }
    }
}

tasks.register("checkDevices") {
    description = "Check connected Android devices"
    group = "ausgetrunken"
    doLast {
        exec {
            commandLine("adb", "devices")
        }
    }
}

tasks.register("setupCheck") {
    description = "Verify development environment setup"
    group = "ausgetrunken"
    doLast {
        val javaHome = System.getProperty("java.home")
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        
        println("🔍 Environment Check:")
        println("☕ Java Home: $javaHome")
        println("🤖 Android Home: $androidHome")
        
        if (androidHome != null) {
            val adb = File("$androidHome/platform-tools/adb${if (System.getProperty("os.name").contains("Windows")) ".exe" else ""}")
            if (adb.exists()) {
                println("✅ ADB found: ${adb.absolutePath}")
            } else {
                println("❌ ADB not found at expected location")
            }
        } else {
            println("⚠️  ANDROID_HOME not set")
        }
    }
}