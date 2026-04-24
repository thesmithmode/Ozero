/**
 * Convention plugin for JaCoCo code coverage with 90% gate (line + branch).
 *
 * Configures:
 * - JaCoCo plugin with version 0.8.12
 * - jacocoTestReport task (depends on testDebugUnitTest or test)
 * - jacocoTestCoverageVerification task with 90% line + branch threshold
 * - Excludes: generated code, DI, themes, placeholders, etc.
 */
import java.math.BigDecimal

plugins {
    jacoco
}

// JaCoCo version — keep in sync with gradle/libs.versions.toml [versions].jacoco
jacoco {
    toolVersion = "0.8.12"
}

// Determine if this is an Android or pure Kotlin module
val isAndroid = plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")

val excludedClasses = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "**/*_Hilt*.*",
    "**/*Hilt_*.*",
    "**/*_Factory.*",
    "**/*_GeneratedInjector.*",
    "**/hilt_aggregated_deps/**",
    "**/*_Impl*.*",
    "**/di/**",
    "**/databinding/**",
    "**/generated/**",
    "**/ui/theme/**",
    "**/Placeholder*.*"
)

// For Android modules the task does not exist yet — register it.
// For pure JVM modules the jacoco plugin already registers jacocoTestReport — configure it.
if (isAndroid) {
    tasks.register<JacocoReport>("jacocoTestReport") {
        group = "verification"
        description = "Generate JaCoCo code coverage report"

        dependsOn("testDebugUnitTest")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(
                layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
                layout.buildDirectory.dir("intermediates/javac/debug/classes")
            ).asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            layout.buildDirectory.file(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
            )
        )

        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        group = "verification"
        description = "Verify JaCoCo code coverage meets 90% gate (line + branch)"
        dependsOn("jacocoTestReport")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(
                layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
                layout.buildDirectory.dir("intermediates/javac/debug/classes")
            ).asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            layout.buildDirectory.file(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
            )
        )

        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.90")
                }
            }
            rule {
                element = "BUNDLE"
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.90")
                }
            }
        }
    }
} else {
    // Pure JVM — jacoco plugin already registers jacocoTestReport, just configure it
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(layout.buildDirectory.dir("classes/kotlin/main"))
                .asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))

        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
        }
    }

    // jacocoTestCoverageVerification is also registered by jacoco plugin for JVM — configure it
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("jacocoTestReport")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(layout.buildDirectory.dir("classes/kotlin/main"))
                .asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))

        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.90")
                }
            }
            rule {
                element = "BUNDLE"
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.90")
                }
            }
        }
    }
}

// For Android modules: configure testOptions
if (isAndroid) {
    val android = extensions.findByName("android") as? com.android.build.gradle.BaseExtension
    android?.apply {
        testOptions.unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}
