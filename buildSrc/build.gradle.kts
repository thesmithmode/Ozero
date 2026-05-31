plugins {
    `kotlin-dsl`
    jacoco
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.20")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.7.0")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.1")
    implementation("com.squareup:javapoet:1.13.0")
    implementation("org.yaml:snakeyaml:2.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
}

configurations.all {
    resolutionStrategy {
        force("com.squareup:javapoet:1.13.0")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    sourceDirectories.setFrom(layout.projectDirectory.dir("src/main/kotlin"))
    classDirectories.setFrom(layout.buildDirectory.dir("classes/kotlin/main"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/test.exec")
        },
    )
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")
    sourceDirectories.setFrom(layout.projectDirectory.dir("src/main/kotlin"))
    classDirectories.setFrom(layout.buildDirectory.dir("classes/kotlin/main"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/test.exec")
        },
    )
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
        }
        rule {
            element = "BUNDLE"
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
        }
    }
}
