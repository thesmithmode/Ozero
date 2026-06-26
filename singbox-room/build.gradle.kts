plugins {
    id("ozero.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.ozero.singboxroom"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val robolectricRuntimeDeps33 by configurations.creating
val robolectricRuntimeDeps35 by configurations.creating

val prepareRobolectricRuntimeDeps by tasks.registering(Copy::class) {
    from(robolectricRuntimeDeps33)
    from(robolectricRuntimeDeps35)
    into(layout.buildDirectory.dir("robolectric-runtime-deps"))
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricRuntimeDeps)
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-runtime-deps").get().asFile.absolutePath,
    )
    systemProperty("robolectric.offline", "true")
}

dependencies {
    api(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)

    testImplementation(libs.junit4)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.robolectric.android.all.instrumented)
    testRuntimeOnly(libs.robolectric.android.all.instrumented35)
    testRuntimeOnly(libs.junit.vintage.engine)
    robolectricRuntimeDeps33(libs.robolectric.android.all.instrumented)
    robolectricRuntimeDeps35(libs.robolectric.android.all.instrumented35)
}
