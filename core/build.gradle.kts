plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

val forbiddenAndroidImports by tasks.registering {
    group = "verification"
    description = "Fails if :core imports Android framework or AndroidX APIs."

    val sources = fileTree("src/main/kotlin") {
        include("**/*.kt")
    }

    inputs.files(sources)

    doLast {
        val offenders = sources.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("import android.") || trimmed.startsWith("import androidx.")) {
                    "${file.relativeTo(projectDir)}:${index + 1}: $trimmed"
                } else {
                    null
                }
            }
        }

        check(offenders.isEmpty()) {
            "Forbidden Android imports in :core:\n${offenders.joinToString("\n")}"
        }
    }
}

tasks.check {
    dependsOn(forbiddenAndroidImports)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
