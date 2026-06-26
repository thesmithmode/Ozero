plugins {
    id("ozero.android.library")
}

val robolectricRuntimeDeps24 by configurations.creating
val robolectricRuntimeDeps33 by configurations.creating
val robolectricRuntimeDeps34 by configurations.creating

val prepareRobolectricRuntimeDeps by tasks.registering(org.gradle.api.tasks.Copy::class) {
    from(robolectricRuntimeDeps24)
    from(robolectricRuntimeDeps33)
    from(robolectricRuntimeDeps34)
    into(layout.buildDirectory.dir("robolectric-runtime-deps"))
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    dependsOn(prepareRobolectricRuntimeDeps)
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-runtime-deps").get().asFile.absolutePath,
    )
    systemProperty("robolectric.offline", "true")
}

android {
    namespace = "ru.ozero.enginewarp"

    buildFeatures {
        aidl = true
    }

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
}

dependencies {
    api(project(":shared-warp-settings"))
    implementation(project(":engines-core"))
    implementation(project(":common-vpn"))
    implementation(libs.bundles.coroutines)

    implementation(libs.hilt.android)
    implementation(libs.datastore.preferences)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.robolectric)
    testImplementation(libs.datastore.preferences.core)
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
    robolectricRuntimeDeps24("org.robolectric:android-all-instrumented:7.0.0_r1-robolectric-r1-i6")
    robolectricRuntimeDeps33(libs.robolectric.android.all.instrumented)
    robolectricRuntimeDeps34(libs.robolectric.android.all.instrumented35)
}
