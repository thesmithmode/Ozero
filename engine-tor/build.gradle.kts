import binaries.GenerateTorChecksumsTask

plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginetor"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    // ndkVersion and externalNativeBuild activated when CMakeLists.txt is added (E1+)
}

// RT.5.3 — генерация TorBinaryChecksums из binaries.lock.yaml.
val generateTorChecksums =
    tasks.register("generateTorChecksums", GenerateTorChecksumsTask::class.java) {
        lockFile.set(rootProject.layout.projectDirectory.file("build-tools/binaries.lock.yaml"))
        outputDir.set(layout.buildDirectory.dir("generated/source/torChecksums"))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generateTorChecksums,
            GenerateTorChecksumsTask::outputDir,
        )
    }
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-orchestrator"))
    implementation(project(":core-subscriptions"))
    implementation(project(":core-storage"))
    implementation(project(":common-vpn"))
    implementation(libs.bundles.coroutines)

    // PlayCore SplitInstall — on-demand доставка :dynamic_tor (~200 МБ tor+iptproxy)
    implementation(libs.play.feature.delivery)
    // Task<T>.await() для PlayCore deferredUninstall().
    implementation(libs.coroutines.play.services)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
