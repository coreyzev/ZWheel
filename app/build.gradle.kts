import org.gradle.api.tasks.testing.Test

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

// ADR-010 policy guard: ZWheel is a user-owned, offline-first companion. Runtime egress
// is limited to OpenStreetMap/OSMDroid tile servers and the user-configured Home Assistant
// URL (which is never hardcoded). No OEM/vendor cloud, no analytics, no firmware endpoints.
val enforceAdr010NetworkPolicy by tasks.registering {
    group = "verification"
    description = "ADR-010: fail if any hardcoded non-OSM host or analytics dependency is found."

    val sourceRoots = listOf(
        layout.projectDirectory.dir("src"),
        layout.projectDirectory.dir("../core/src"),
        layout.projectDirectory.dir("../wear/src"),
    )

    inputs.files(
        provider {
            sourceRoots.flatMap { dir ->
                fileTree(dir) { include("**/*.kt") }.files
            }
        },
    )
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))

    doLast {
        // Hosts permitted by ADR-010: OSM/OSMDroid tile servers and the well-known
        // mDNS hostname for Home Assistant (used only as a placeholder example in the UI).
        val allowedHosts = setOf(
            "tile.openstreetmap.org",
            "a.tile.openstreetmap.org",
            "b.tile.openstreetmap.org",
            "c.tile.openstreetmap.org",
            "osmdroid.github.io",
            "homeassistant.local",
        )

        // Analytics/crash/ads SDKs banned by ADR-010.
        val bannedDeps = listOf(
            "firebase-analytics", "firebase-crashlytics", "google-analytics",
            "appcenter", "sentry", "bugsnag", "amplitude", "mixpanel",
            "facebook-core", "adjust", "branch", "flurry",
        )

        val urlPattern = Regex("""https?://([a-zA-Z0-9.\-]+)""")

        val violations = mutableListOf<String>()

        sourceRoots.forEach { srcDir ->
            // Exclude debug source sets — debug-only fixture URLs are acceptable.
            fileTree(srcDir) { include("**/*.kt"); exclude("**/debug/**") }.forEach { file ->
                val text = file.readText()
                urlPattern.findAll(text).forEach { match ->
                    val host = match.groupValues[1]
                    if (host !in allowedHosts) {
                        violations += "${file.relativeTo(rootDir)}: hardcoded URL ${match.value}"
                    }
                }
            }
        }

        // Check the version catalog (all deps are declared there).
        val versionCatalog = layout.projectDirectory.file("../gradle/libs.versions.toml").asFile
        if (versionCatalog.exists()) {
            val catalogText = versionCatalog.readText()
            bannedDeps.forEach { dep ->
                if (dep in catalogText) {
                    violations += "libs.versions.toml: banned analytics/ads dependency '$dep'"
                }
            }
        }

        check(violations.isEmpty()) {
            "ADR-010 network policy violation(s):\n" + violations.joinToString("\n")
        }
    }
}

tasks.check {
    dependsOn(enforceAdr010NetworkPolicy)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.kable.core.android)
    implementation(libs.osmdroid)
    implementation(libs.play.services.location)
    implementation(libs.play.services.wearable)
    wearApp(project(":wear"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.robolectric)
    ksp(libs.hilt.compiler)
}
