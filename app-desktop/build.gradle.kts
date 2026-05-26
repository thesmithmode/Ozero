import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("ozero.kotlin.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.coroutines.core)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}

compose.desktop {
    application {
        mainClass = "ru.ozero.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Ozero"
            packageVersion = "1.0.0"
            description = "Ozero VPN for Windows"
            vendor = "Ozero"

            windows {
                menuGroup = "Ozero"
                upgradeUuid = "d3b07384-d113-4ec6-a5ea-024c9c7b1f2a"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}
