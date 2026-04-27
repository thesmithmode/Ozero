plugins {
    id("ozero.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.ozero.commonvpn"

    defaultConfig {
        // libhev-socks5-tunnel.so собирается в release.yml workflow и кладётся
        // в src/main/jniLibs/<abi>/. AGP пакует jniLibs автоматически. Список
        // ABI должен совпадать с теми, под которые собирается .so в CI.
        ndk {
            // Список синхронизирован с app/build.gradle.kts и release.yml APK assertion.
            // x86 исключён: app не публикует x86, AGP всё равно вырезал бы x86 .so из APK
            // по пересечению abiFilters → расхождение между модулями = шум в CI.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-orchestrator"))
    implementation(libs.bundles.coroutines)

    // Hilt — нужен в этом модуле потому что OzeroVpnService = @AndroidEntryPoint;
    // Hilt KSP должен видеть его в processing classpath, иначе VpnService не получит
    // generated _HiltModules и инъекция упадёт IllegalStateException at runtime.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
