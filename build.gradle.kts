plugins {
        id("com.android.application") apply false
    id("com.android.library") apply false
    id("com.android.dynamic-feature") apply false
        id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
        id("io.gitlab.arturbosch.detekt") apply false
    id("org.jlleitschuh.gradle.ktlint") apply false
        alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
        id("org.owasp.dependencycheck") version "10.0.4" apply false
        jacoco
}

jacoco {
    toolVersion = "0.8.12"
}

buildscript {
    configurations.classpath {
        resolutionStrategy {
            force("com.squareup:javapoet:1.13.0")
        }
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("com.squareup:javapoet:1.13.0")
            force("androidx.core:core-ktx:1.13.1")
            force("org.jetbrains.kotlin:kotlin-stdlib:2.0.20")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.20")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.20")
            force("com.squareup.okio:okio-jvm:3.10.0")
            force("com.squareup.okhttp3:okhttp:4.12.0")
            force("com.squareup.okhttp3:mockwebserver:4.12.0")
            force("com.squareup.okhttp3:logging-interceptor:4.12.0")
        }
    }
}
