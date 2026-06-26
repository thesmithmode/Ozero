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
    namespace = "ru.ozero.singboxsubscription"
}

dependencies {
    implementation(project(":singbox-fmt"))
    implementation(project(":singbox-room"))
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.okhttp)
    implementation(libs.snakeyaml)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.okhttp.mockwebserver)
    robolectricRuntimeDeps24("org.robolectric:android-all-instrumented:7.0.0_r1-robolectric-r1-i6")
    robolectricRuntimeDeps33(libs.robolectric.android.all.instrumented)
    robolectricRuntimeDeps34(libs.robolectric.android.all.instrumented35)
}
