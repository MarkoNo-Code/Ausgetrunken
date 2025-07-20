// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.googleServices) apply false
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
        
        // Check if we're using Android Studio JBR
        val expectedJavaHome = "C:/Program Files/Android/Android Studio/jbr"
        val expectedJavaHomePosix = "/c/Program Files/Android/Android Studio/jbr"
        if (javaHome.contains("Android Studio") || javaHome.contains("jbr")) {
            println("✅ Using Android Studio JBR (recommended)")
        } else {
            println("⚠️  Not using Android Studio JBR")
            println("💡 To fix: export JAVA_HOME=\"$expectedJavaHomePosix\"")
        }
        
        if (androidHome != null) {
            val adb = File("$androidHome/platform-tools/adb${if (System.getProperty("os.name").contains("Windows")) ".exe" else ""}")
            if (adb.exists()) {
                println("✅ ADB found: ${adb.absolutePath}")
                // Test ADB
                try {
                    val process = ProcessBuilder("$androidHome/platform-tools/adb${if (System.getProperty("os.name").contains("Windows")) ".exe" else ""}", "devices")
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    println("📱 Connected devices:")
                    println(output)
                } catch (e: Exception) {
                    println("❌ Failed to run ADB: ${e.message}")
                }
            } else {
                println("❌ ADB not found at expected location")
            }
        } else {
            println("⚠️  ANDROID_HOME not set")
        }
    }
}

tasks.register("setJavaHome") {
    description = "Set correct JAVA_HOME for Android development"
    group = "ausgetrunken"
    doLast {
        val expectedJavaHome = if (System.getProperty("os.name").contains("Windows")) {
            "C:\\Program Files\\Android\\Android Studio\\jbr"
        } else {
            "/c/Program Files/Android/Android Studio/jbr"
        }
        
        val javaExe = File("$expectedJavaHome/bin/java${if (System.getProperty("os.name").contains("Windows")) ".exe" else ""}")
        
        if (javaExe.exists()) {
            println("✅ Found Android Studio JBR at: $expectedJavaHome")
            println("🔧 To set for current session:")
            println("   export JAVA_HOME=\"/c/Program Files/Android/Android Studio/jbr\"")
            println("")
            println("🔧 For permanent setup, run one of:")
            println("   source setup-env.sh     (Unix/Git Bash)")
            println("   setup-env.bat          (Windows)")
        } else {
            println("❌ Android Studio JBR not found at: $expectedJavaHome")
            println("💡 Please install Android Studio or update the path")
        }
    }
}