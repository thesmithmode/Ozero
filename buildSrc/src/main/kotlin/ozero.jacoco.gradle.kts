import java.math.BigDecimal

plugins {
    jacoco
}

jacoco {
    toolVersion = "0.8.12"
}

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
            fileTree(layout.buildDirectory) {
                include(
                    "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                    "jacoco/testDebugUnitTest.exec"
                )
            }
        )

        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        group = "verification"
        description = "Verify JaCoCo code coverage meets 95% gate (line + branch)"
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
            fileTree(layout.buildDirectory) {
                include(
                    "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                    "jacoco/testDebugUnitTest.exec"
                )
            }
        )

        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.95")
                }
            }
            rule {
                element = "BUNDLE"
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.95")
                }
            }
        }
    }
} else {
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(layout.buildDirectory.dir("classes/kotlin/main"))
                .asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            fileTree(layout.buildDirectory) {
                include("jacoco/test.exec")
            }
        )

        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("jacocoTestReport")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(layout.buildDirectory.dir("classes/kotlin/main"))
                .asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            fileTree(layout.buildDirectory) {
                include("jacoco/test.exec")
            }
        )

        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.95")
                }
            }
            rule {
                element = "BUNDLE"
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.95")
                }
            }
        }
    }
}

if (isAndroid) {
    val android = extensions.findByName("android") as? com.android.build.gradle.BaseExtension
    android?.apply {
        testOptions.unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}
