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
    implementation(compose.foundation)
    implementation(libs.coroutines.core)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}

compose.desktop {
    application {
        mainClass = "ru.ozero.desktop.MainKt"

        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Ozero"
            val ver = project.findProperty("desktopVersion")?.toString()
                ?.removePrefix("v") ?: "1.0.0"
            packageVersion = ver
            description = "Ozero VPN"
            vendor = "Ozero"

            windows {
                menuGroup = "Ozero"
                shortcut = true
                dirChooser = true
                upgradeUuid = "d3b07384-d113-4ec6-a5ea-024c9c7b1f2a"
                perUserInstall = true
            }

            linux {
                shortcut = true
                menuGroup = "Ozero"
                debMaintainer = "ozero@ozero.ru"
            }

            macOS {
                bundleID = "ru.ozero.desktop"
                // macOS DMG требует MAJOR > 0; при 0.x.y подменяем на 1.x.y
                val parts = ver.split(".")
                if (parts.isNotEmpty() && (parts[0].toIntOrNull() ?: 0) < 1) {
                    val macVer = (listOf("1") + parts.drop(1)).joinToString(".")
                    packageVersion = macVer
                    packageBuildVersion = macVer
                }
            }
        }
    }
}
