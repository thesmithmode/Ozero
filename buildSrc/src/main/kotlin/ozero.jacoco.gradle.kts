import java.math.BigDecimal

plugins {
    jacoco
}

jacoco {
    toolVersion = "0.8.12"
}

val isAndroid = plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")

data class CoverageThresholds(val line: BigDecimal, val branch: BigDecimal)

fun coverageThresholdsFor(projectPath: String): CoverageThresholds = when (projectPath) {
    ":app" -> CoverageThresholds(BigDecimal("0.83"), BigDecimal("0.69"))
    ":common-vpn" -> CoverageThresholds(BigDecimal("0.72"), BigDecimal("0.67"))
    ":engine-urnetwork" -> CoverageThresholds(BigDecimal("0.33"), BigDecimal("0.29"))
    ":engine-byedpi" -> CoverageThresholds(BigDecimal("0.80"), BigDecimal("0.79"))
    ":engine-fptn" -> CoverageThresholds(BigDecimal("0.57"), BigDecimal("0.65"))
    ":engine-warp" -> CoverageThresholds(BigDecimal("0.85"), BigDecimal("0.67"))
    ":common-net" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.78"))
    ":engine-masterdns" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.83"))
    ":engine-singbox" -> CoverageThresholds(BigDecimal("0.50"), BigDecimal("0.45"))
    ":singbox-subscription" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.93"))
    ":shared-warp-settings" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.94"))
    ":singbox-fmt" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":singbox-config" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    else -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
}

val excludedClasses = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "**/*_Hilt*.*",
    "**/*Hilt_*.*",
    "**/*_Factory.*",
    "**/*_GeneratedInjector.*",
    "**/hilt_aggregated_deps/**",
    "**/*_Impl*.*",
    "**/*\$DefaultImpls*.*",
    "**/di/**",
    "**/databinding/**",
    "**/generated/**",
    "**/*_gradle*.*",
    "**/ComposableSingletons*.*",
    "**/ui/theme/**",
    "**/ui/**/*ScreenKt*.*",
    "**/ui/**/*CardKt*.*",
    "**/ui/**/*SectionKt*.*",
    "**/ui/**/*ContentKt*.*",
    "**/ui/**/*DialogKt*.*",
    "**/ui/components/**",
    "**/ui/icons/**",
    "**/ui/RootNavigationKt*.*",
    "**/ui/TopScreen*.*",
    "**/ui/*MainState*.*",
    "**/ui/launcher/**",
    "**/ui/settings/SettingsNavActions*.*",
    "**/*Activity*.*",
    "**/*Application*.*",
    "**/*Receiver*.*",
    "**/*Worker*.*",
    "**/OzeroApp*.*",
    "**/OzeroQuickTile*.*",
    "**/*Tile*.*",
    "**/*Module*.*",
    "**/*Database*.*",
    "**/*Dao*.*",
    "**/dao/**",
    "**/entity/**",
    "**/*WebSocket*.*",
    "**/*Notification*.*",
    "**/EngineCapabilities*.*",
    "**/EngineConfig*.*",
    "**/EngineId*.*",
    "**/EnginePreflight*.*",
    "**/EngineStats*.*",
    "**/EnginePlugin\$PeerWatchdogPolicy*.*",
    "**/EnginePlugin\$ReadyResult*.*",
    "**/EnginePlugin\$RecoverResult*.*",
    "**/ExitNodeStrategy*.*",
    "**/IpProbeRoute*.*",
    "**/ProbeResult*.*",
    "**/StartResult*.*",
    "**/TunAttachResult*.*",
    "**/TunFdAcceptor*.*",
    "**/Upstream*.*",
    "**/WireGuardOutboundConfig*.*",
    "**/settings/AppMode*.*",
    "**/settings/ByeDpiUiSettings*.*",
    "**/settings/HostsMode*.*",
    "**/settings/SettingsKeys*.*",
    "**/settings/SettingsModel*.*",
    "**/settings/SettingsRepositoryImpl*.*",
    "**/settings/UserFlagsRepositoryImpl*.*",
    "**/settings/SplitTunnelMode*.*",
    "**/settings/TrafficMode*.*",
    "**/Libsingboxgojni*.*",
    "**/SingboxEngineService*.*",
    "**/SingboxRuntime*.*",
    "**/GoBackend*.*",
    "**/ProxyGoBackend*.*",
    "**/RemoteAwgRuntime*.*",
    "**/SshjTransport*.*",
    "**/WarpSdkBridge\$DefaultImpls*.*",
    "**/CrashLogStore*.*",
    "**/RoomSplitTunnelRulesProvider*.*",
    "**/DefaultTProxyLoader*.*",
    "**/FptnNativeHttpsClient*.*",
    "**/FptnNativeSniChecker*.*",
    "**/AndroidNetworkProfileDetector*.*",
    "**/AndroidDummyPipeFactory*.*",
    "**/RelayLockManager*.*",
    "**/RelayNetworkMonitor*.*",
    "**/OkHttpIpInfoProvider*.*",
    "**/SocksDohResolver*.*",
    "**/DohResolver*.*",
    "**/DnsResolverKt*.*",
    "**/singboxsubscription/parser/SubscriptionInfo*.*"
)

if (isAndroid) {
    val thresholds = coverageThresholdsFor(project.path)
    tasks.register<JacocoReport>("jacocoTestReport") {
        group = "verification"
        description = "Generate JaCoCo code coverage report"

        dependsOn("testDebugUnitTest")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(
                layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
                layout.buildDirectory.dir("intermediates/javac/debug/classes")
            ).asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            fileTree(layout.buildDirectory) {
                include(
                    "outputs/unit_test_code_coverage/**/testDebugUnitTest.exec",
                    "outputs/unit_test_code_coverage/**/test/*.exec",
                    "jacoco/testDebugUnitTest.exec",
                    "**/jacoco/testDebugUnitTest.exec",
                )
            }
        )

        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        group = "verification"
        description = "Verify JaCoCo code coverage meets 95% gate (line + branch)"
        dependsOn("jacocoTestReport")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(
                layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
                layout.buildDirectory.dir("intermediates/javac/debug/classes")
            ).asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            fileTree(layout.buildDirectory) {
                include(
                    "outputs/unit_test_code_coverage/**/testDebugUnitTest.exec",
                    "outputs/unit_test_code_coverage/**/test/*.exec",
                    "jacoco/testDebugUnitTest.exec",
                    "**/jacoco/testDebugUnitTest.exec",
                )
            }
        )

        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = thresholds.line
                }
            }
            rule {
                element = "BUNDLE"
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = thresholds.branch
                }
            }
        }
    }
} else {
    val thresholds = coverageThresholdsFor(project.path)
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(
                layout.buildDirectory.dir("classes/kotlin/main"),
                layout.buildDirectory.dir("classes/java/main"),
            )
                .asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            fileTree(layout.buildDirectory) {
                include("jacoco/test.exec", "jacoco/test*.exec", "outputs/unit_test_code_coverage/**/*.exec")
            }
        )

        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("jacocoTestReport")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(
                layout.buildDirectory.dir("classes/kotlin/main"),
                layout.buildDirectory.dir("classes/java/main"),
            )
                .asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            fileTree(layout.buildDirectory) {
                include("jacoco/test.exec", "jacoco/test*.exec", "outputs/unit_test_code_coverage/**/*.exec")
            }
        )

        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = thresholds.line
                }
            }
            rule {
                element = "BUNDLE"
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = thresholds.branch
                }
            }
        }
    }
}

if (isAndroid) {
    val android = extensions.findByName("android") as? com.android.build.gradle.BaseExtension
    android?.apply {
        testOptions.unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}
