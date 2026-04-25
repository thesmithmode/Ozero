plugins {
    id("ozero.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.ozero.commonvpn"
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
