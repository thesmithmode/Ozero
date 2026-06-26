import org.gradle.api.tasks.testing.Test
import java.net.URI

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

val prefetchRobolectricAndroidAllInstrumented by tasks.registering {
    val versions = listOf(
        "7.0.0_r1-robolectric-r1-i6",
        "9-robolectric-4913185-2-i6",
        "10-robolectric-5803371-i6",
        "13-robolectric-9030017-i6",
        "14-robolectric-10818077-i6",
    )
    val m2Root = providers.systemProperty("user.home").map {
        file("$it/.m2/repository/org/robolectric/android-all-instrumented")
    }
    outputs.files(
        versions.map { version ->
            m2Root.map { it.resolve("$version/android-all-instrumented-$version.jar") }
        },
    )
    doLast {
        val root = m2Root.get()
        versions.forEach { version ->
            val dir = root.resolve(version)
            dir.mkdirs()
            listOf("pom", "jar").forEach { extension ->
                val target = dir.resolve("android-all-instrumented-$version.$extension")
                if (!target.isFile || target.length() == 0L) {
                    val url = URI(
                        "https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/" +
                            "$version/android-all-instrumented-$version.$extension",
                    ).toURL()
                    target.outputStream().use { output ->
                        url.openStream().use { input -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}

subprojects {
    plugins.withId("com.android.application") {
        tasks.withType<Test>().configureEach {
            dependsOn(rootProject.tasks.named("prefetchRobolectricAndroidAllInstrumented"))
        }
    }
    plugins.withId("com.android.library") {
        tasks.withType<Test>().configureEach {
            dependsOn(rootProject.tasks.named("prefetchRobolectricAndroidAllInstrumented"))
        }
    }
}
