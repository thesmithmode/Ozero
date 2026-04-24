plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // AGP — needed so convention plugins can access com.android.build.* APIs
    implementation("com.android.tools.build:gradle:8.7.3")
    // Kotlin Gradle plugin — needed for KotlinAndroidProjectExtension
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
    // Kotlin Compose compiler plugin
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.20")
    // Detekt — code analysis
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    // Ktlint — code formatting checks
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.1")
    // Force javapoet 1.13.0 в buildSrc classpath — иначе Hilt 2.56+ падает с NoSuchMethodError canonicalName
    implementation("com.squareup:javapoet:1.13.0")
}

configurations.all {
    resolutionStrategy {
        force("com.squareup:javapoet:1.13.0")
    }
}
