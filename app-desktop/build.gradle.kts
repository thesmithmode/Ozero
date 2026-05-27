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

val desktopVersion: String = project.findProperty("desktopVersion")?.toString()
    ?.removePrefix("v") ?: "1.0.0"

tasks.register("generateVersionProperties") {
    val versionValue = desktopVersion
    val outDir = layout.buildDirectory.dir("generated/version")
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()
        dir.resolve("version.properties").writeText("version=$versionValue\n")
    }
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/version"))
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

compose.desktop {
    application {
        mainClass = "ru.ozero.desktop.MainKt"

        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            val version = desktopVersion
            targetFormats(TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Ozero"
            packageVersion = version
            description = "Ozero VPN"
            vendor = "Ozero"

            windows {
                menuGroup = "Ozero"
                shortcut = true
                dirChooser = true
                upgradeUuid = "d3b07384-d113-4ec6-a5ea-024c9c7b1f2a"
                perUserInstall = true
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }

            linux {
                shortcut = true
                menuGroup = "Ozero"
                debMaintainer = "ozero@ozero.ru"
                iconFile.set(project.file("src/main/resources/icon.png"))
            }

            macOS {
                bundleID = "ru.ozero.desktop"
                val parts = version.split(".")
                if (parts.isNotEmpty() && (parts[0].toIntOrNull() ?: 0) < 1) {
                    val macVer = (listOf("1") + parts.drop(1)).joinToString(".")
                    packageVersion = macVer
                    packageBuildVersion = macVer
                }
            }
        }
    }
}
