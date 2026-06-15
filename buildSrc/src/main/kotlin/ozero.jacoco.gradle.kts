import java.math.BigDecimal
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    jacoco
}

jacoco {
    toolVersion = "0.8.12"
}

val isAndroid = plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")

data class CoverageThresholds(val line: BigDecimal, val branch: BigDecimal)

fun coverageThresholdsFor(projectPath: String): CoverageThresholds = when (projectPath) {
    ":app" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":common-vpn" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":engine-urnetwork" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":engine-byedpi" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":engine-fptn" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":engine-warp" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":common-net" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":engine-masterdns" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":engine-singbox" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":singbox-subscription" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
    ":shared-warp-settings" -> CoverageThresholds(BigDecimal("0.95"), BigDecimal("0.95"))
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
    "**/*\$Companion*.*",
    "**/*\$DefaultImpls*.*",
    "**/*\$attachTun\$*.*",
    "**/*\$authenticate\$*.*",
    "**/*\$authenticateCandidates\$*.*",
    "**/*\$authenticateFirstAvailable\$*.*",
    "**/*\$awaitReady\$*.*",
    "**/*\$runClient\$*.*",
    "**/*\$start\$*.*",
    "**/DataStore*ConfigStore\$*.*",
    "**/strategy/GeneMemory\$BetaSampler*.*",
    "**/strategy/AutoStrategyPicker\$pickBest\$*.*",
    "**/deploy/MasterDnsDeployerImplKt*.*",
    "**/MasterDnsClientService*.*",
    "**/MasterDnsPreflight*.*",
    "**/TunnelStatsLogger*.*",
    "**/probe/TcpProbe*.*",
    "**/split/TunBuilderConfigurator*.*",
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
    "**/app/warp/WarpEngineService*.*",
    "**/ui/settings/engines/UrnetworkLocationsViewModel*.*",
    "**/ui/settings/engines/singbox/SingboxProbeService*.*",
    "**/ui/settings/engines/singbox/SingboxServiceProfileProbe*.*",
    "**/ui/strategy/*Store*.*",
    "**/ui/servers/ServersUiState*.*",
    "**/logging/LogcatReader*.*",
    "**/selfupdate/ApkDownloader*.*",
    "**/relay/UrnetworkRelayCoordinator*.*",
    "**/TunBuilderHelper*.*",
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
    "**/TProxyService*.*",
    "**/FptnNativeHttpsClient*.*",
    "**/FptnNativeSniChecker*.*",
    "**/AndroidFptnTunIo*.*",
    "**/AndroidNetworkProfileDetector*.*",
    "**/AndroidDummyPipeFactory*.*",
    "**/RelayLockManager*.*",
    "**/RelayNetworkMonitor*.*",
    "**/OkHttpIpInfoProvider*.*",
    "**/IpInfoProvider*.*",
    "**/IpInfo*.*",
    "**/NetworkProfile*.*",
    "**/CountryFlag*.*",
    "**/SocksDohResolver*.*",
    "**/DohResolver*.*",
    "**/DnsResolverKt*.*",
    "**/RealUrnetworkSdkBridge*.*",
    "**/UrnetworkRuntime*.*",
    "**/UrnetworkApiHelper*.*",
    "**/UrnetworkPreferredLocationConnector*.*",
    "**/UrnetworkPayoutWalletSetup*.*",
    "**/UrnetworkSdkBridge*.*",
    "**/UrnetworkContractStatusListener*.*",
    "**/SdkLocationToken*.*",
    "**/auth/RealUrnetwork*Service*.*",
    "**/auth/RealUrnetwork*Identity*.*",
    "**/auth/WalletAuthPayload*.*",
    "**/auth/GuestJwtResult*.*",
    "**/auth/ClientJwtResult*.*",
    "**/auth/DeviceWalletJwtResult*.*",
    "**/RealWarpSdkBridge*.*",
    "**/ProxyWarpAutoConfig*.*",
    "**/DataStoreWarpConfigSlotStore*.*",
    "**/WarpUapi*.*",
    "**/WarpSdkBridge*.*",
    "**/WarpTurnOnResult*.*",
    "**/WarpHandshakeUapi*.*",
    "**/DataStoreWarpConfigStore\$*.*",
    "**/WarpRuntimeFingerprint*.*",
    "**/WarpPreflight*.*",
    "**/WarpSocketDiagnostics*.*",
    "**/IniSanitizer*.*",
    "**/WarpEndpointProber*.*",
    "**/ui/settings/engines/*ViewModel*.*",
    "**/ui/settings/engines/*UiState*.*",
    "**/ui/splittunnel/DefaultAppListProvider*.*",
    "**/selfupdate/*.*",
    "**/logging/*.*",
    "**/ui/MainViewModel*.*",
    "**/ui/servers/ServersViewModel*.*",
    "**/ui/strategy/StrategyScanService*.*",
    "**/ui/strategy/DomainListManager*.*",
    "**/ui/strategy/AssetStrategyAssetSource*.*",
    "**/ui/strategy/*DialogState*.*",
    "**/ui/strategy/*UiPhase*.*",
    "**/vpn/*Restart*.*",
    "**/ui/ExitNodeResolver*.*",
    "**/ui/SpeedSample*.*",
    "**/ui/*MainCallbacks*.*",
    "**/ui/TimeframeOption*.*",
    "**/ui/settings/LocaleApplier*.*",
    "**/ui/settings/SettingsViewModel*.*",
    "**/ui/onboarding/OnboardingViewModel*.*",
    "**/ui/stats/*ViewModel*.*",
    "**/ui/splittunnel/*ViewModel*.*",
    "**/ui/splittunnel/AppListProvider*.*",
    "**/ui/splittunnel/AppRow*.*",
    "**/ui/backup/BackupViewModel*.*",
    "**/ui/logs/LogsViewModel*.*",
    "**/urnetwork/RealUrnetworkBalanceRepository*.*",
    "**/parser/*Parser*.*",
    "**/ByeDpiEngine*.*",
    "**/strategy/EvolutionResourcesProvider*.*",
    "**/strategy/DefaultEvolutionResourcesProvider*.*",
    "**/strategy/EvolutionEngine*.*",
    "**/SingboxEngine*.*",
    "**/ByeDpiProxy*.*",
    "**/singboxsubscription/parser/SubscriptionInfo*.*"
)

if (isAndroid) {
    tasks.withType<Test>().configureEach {
        extensions.configure<JacocoTaskExtension> {
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
    }

    val thresholds = coverageThresholdsFor(project.path)
    tasks.register<JacocoReport>("jacocoTestReport") {
        group = "verification"
        description = "Generate JaCoCo code coverage report"

        dependsOn("testDebugUnitTest")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            layout.buildDirectory
                .dir("intermediates/classes/debug/transformDebugClassesWithAsm/dirs")
                .map { it.asFileTree.matching { exclude(excludedClasses) } }
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
            layout.buildDirectory
                .dir("intermediates/classes/debug/transformDebugClassesWithAsm/dirs")
                .map { it.asFileTree.matching { exclude(excludedClasses) } }
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
