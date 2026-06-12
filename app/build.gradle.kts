plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val ciKeystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.zwheel.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zwheel"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-dev"
    }

    signingConfigs {
        if (ciKeystorePath != null) {
            create("stableDebug") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = if (ciKeystorePath != null) {
                signingConfigs.getByName("stableDebug")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

val verifyNetworkPermissionScoping by tasks.registering {
    group = "verification"
    description = "Fails if runtime network permission is not scoped to debug builds."

    val mainManifest = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val debugManifest = layout.projectDirectory.file("src/debug/AndroidManifest.xml")
    val releaseManifest = layout.projectDirectory.file("src/release/AndroidManifest.xml")

    inputs.files(mainManifest, debugManifest)
    inputs.files(
        provider {
            if (releaseManifest.asFile.exists()) {
                listOf(releaseManifest.asFile)
            } else {
                emptyList()
            }
        },
    )

    doLast {
        val internetPermission = "android.permission.INTERNET"
        check(!mainManifest.asFile.readText().contains(internetPermission)) {
            "INTERNET permission must not be declared in app/src/main."
        }
        check(debugManifest.asFile.readText().contains(internetPermission)) {
            "Debug manifest must declare INTERNET for BLE fixture upload."
        }
        if (releaseManifest.asFile.exists()) {
            check(!releaseManifest.asFile.readText().contains(internetPermission)) {
                "INTERNET permission must not be declared in app/src/release."
            }
        }
    }
}

tasks.check {
    dependsOn(verifyNetworkPermissionScoping)
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.kable.core.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.hilt.compiler)
}
