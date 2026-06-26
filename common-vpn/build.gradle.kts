plugins {
    id("ozero.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val robolectricRuntimeDeps24 by configurations.creating
val robolectricRuntimeDeps28 by configurations.creating
val robolectricRuntimeDeps29 by configurations.creating
val robolectricRuntimeDeps33 by configurations.creating
val robolectricRuntimeDeps34 by configurations.creating

val prepareRobolectricRuntimeDeps by tasks.registering(org.gradle.api.tasks.Copy::class) {
    from(robolectricRuntimeDeps24)
    from(robolectricRuntimeDeps28)
    from(robolectricRuntimeDeps29)
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
    namespace = "ru.ozero.commonvpn"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
}

dependencies {
    implementation(project(":engines-core"))
    implementation(project(":common-dns"))
    implementation(libs.bundles.coroutines)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    robolectricRuntimeDeps24("org.robolectric:android-all-instrumented:7.0.0_r1-robolectric-r1-i6")
    robolectricRuntimeDeps28("org.robolectric:android-all-instrumented:9-robolectric-4913185-2-i6")
    robolectricRuntimeDeps29("org.robolectric:android-all-instrumented:10-robolectric-5803371-i6")
    robolectricRuntimeDeps33(libs.robolectric.android.all.instrumented)
    robolectricRuntimeDeps34(libs.robolectric.android.all.instrumented35)
}
