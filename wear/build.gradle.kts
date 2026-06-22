import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val localDebugKeystore = providers.environmentVariable("GRADLE_USER_HOME")
    .map { file("$it/android-debug.keystore") }
    .orElse(rootProject.layout.buildDirectory.file("android-debug.keystore").map { it.asFile })

android {
    namespace = "com.zwheel.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zwheel"
        minSdk = 30
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        getByName("debug") {
            val keystore = localDebugKeystore.get()
            keystore.parentFile.mkdirs()
            storeFile = keystore
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

val ensureDebugKeystore by tasks.registering(Exec::class) {
    val keystore = localDebugKeystore.get()

    onlyIf {
        !keystore.exists()
    }

    doFirst {
        keystore.parentFile.mkdirs()
    }

    commandLine(
        "keytool",
        "-genkeypair",
        "-v",
        "-keystore", keystore.absolutePath,
        "-storepass", "android",
        "-alias", "androiddebugkey",
        "-keypass", "android",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=Android Debug,O=Android,C=US",
    )
}

tasks.matching { it.name == "validateSigningDebug" }.configureEach {
    dependsOn(ensureDebugKeystore)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit.jupiter)
}
