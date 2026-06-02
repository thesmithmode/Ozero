import java.math.BigDecimal

plugins {
    jacoco
}

jacoco {
    toolVersion = "0.8.12"
}

val isAndroid = plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")

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
    "**/DataStore*.*",
    "**/Real*.*",
    "**/Remote*.*",
    "**/*WebSocket*.*",
    "**/*Notification*.*",
    "**/settings/AutoStartGateway*.*",
    "**/settings/BootReceiverAutoStartGateway*.*",
    "**/OzeroVpnService*.*",
    "**/TProxyService*.*",
    "**/TunBuilderHelper*.*",
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
    "**/SingboxRuntime*.*",
    "**/SingboxEngineService*.*",
    "**/SingboxProtectorBridge*.*",
    "**/Libsingboxgojni*.*",
    "**/SshjTransport*.*",
    "**/MasterDnsClientService*.*",
    "**/MasterDnsClientWrapper*.*",
    "**/UrnetworkRuntime*.*",
    "**/UrnetworkApiHelper*.*",
    "**/UrnetworkSdkBridge*.*",
    "**/WarpUapi*.*",
    "**/WarpHandshakeUapi*.*",
    "**/WarpEngineService*.*",
    "**/WarpSdkBridge\$DefaultImpls*.*",
    "**/WarpSocketDiagnostics*.*",
    "**/TunInterfaceStats*.*",
    "**/UidTrafficStats*.*",
    "**/CrashLogStore*.*",
    "**/RoomSplitTunnelRulesProvider*.*",
    "**/TunBuilderConfigurator*.*",
    "**/DefaultTProxyLoader*.*",
    "**/AndroidNetworkProfileDetector*.*",
    "**/AndroidDummyPipeFactory*.*",
    "**/RelayLockManager*.*",
    "**/RelayNetworkMonitor*.*",
    "**/UrnetworkRelayCoordinator*.*",
    "**/OkHttpIpInfoProvider*.*",
    "**/SocksDohResolver*.*",
    "**/Socks5HandshakeProbe*.*",
    "**/DohResolver*.*",
    "**/DnsResolverKt*.*",
    "**/AppBackupSerializer*.*",
    "**/BackupSettingsSerializer*.*",
    "**/BackupStrategySerializer*.*",
    "**/BackupWarpSerializer*.*",
    "**/BackupCategory*.*",
    "**/Base64Text*.*",
    "**/WarpTurnOnResult*.*",
    "**/ProxyWarpAutoConfig*.*",
    "**/PrefsMirrorRanker*.*",
    "**/WarpPreflight*.*",
    "**/EngineWarp*.*",
    "**/EngineUrnetwork*.*",
    "**/UrnetworkPreferredLocationConnector*.*",
    "**/UrnetworkPayoutWalletSetup*.*",
    "**/UrnetworkContractStatusListener*.*",
    "**/SdkLocationToken*.*",
    "**/UrnetworkConfigStoreKt*.*",
    "**/MasterDnsEngine*.*",
    "**/MasterDnsPreflight*.*",
    "**/MasterDnsDeployCredentials*.*",
    "**/MasterDnsDeployerImpl*.*",
    "**/MasterDnsDeployerImplKt*.*",
    "**/SingboxEngine*.*",
    "**/SingboxStats*.*",
    "**/SingboxHttp204RoutedProbe*.*",
    "**/FptnEngine*.*",
    "**/FptnEngineKt*.*",
    "**/FptnNativeHttpsClient*.*",
    "**/FptnNativeResponse*.*",
    "**/FptnNativeSniChecker*.*",
    "**/FptnNativeWebSocket*.*",
    "**/FptnToken*.*",
    "**/FptnBypassMethod*.*",
    "**/InMemoryFptnConfigStore*.*",
    "**/ByeDpiProxy*.*",
    "**/ByeDpiPreflight*.*",
    "**/AutoStrategyPicker\$pickBest\$*.*",
    "**/HttpSocksProbeClient\$probe\$*.*",
    "**/PickResult\$Cancelled*.*",
    "**/WarpToWireGuardAdapter*.*",
    "**/TcpProbe*.*",
    "**/SplitTunnelRulesProvider*.*",
    "**/singboxfmt/AbstractBean*.*",
    "**/singboxfmt/ShadowsocksBean*.*",
    "**/singboxfmt/StandardV2RayBean*.*",
    "**/singboxfmt/TrojanBean*.*",
    "**/singboxfmt/VMessBean*.*",
    "**/singboxsubscription/RawUpdater\$Companion*.*",
    "**/singboxsubscription/RawUpdater\$refresh\$*.*",
    "**/singboxsubscription/parser/SubscriptionInfo*.*",
    "**/RomCompat*.*",
    "**/DnsMessage*.*",
    "**/IpInfoProvider*.*",
    "**/CountryFlag*.*",
    "**/OkHttpIpInfoProviderKt*.*",
    "**/AppBackupManager*.*",
    "**/ApkDownloader*.*",
    "**/ApkUpdateVerifier*.*",
    "**/GithubPinnedClient*.*",
    "**/GithubReleaseFetcher*.*",
    "**/ReleaseInfo*.*",
    "**/SilentPackageInstaller*.*",
    "**/UpdateCoordinator*.*",
    "**/UpdateInstallEvent*.*",
    "**/UpdateInstallEventBus*.*",
    "**/BackupCategoryKt*.*",
    "**/BackupWarpSlot*.*",
    "**/BackupSavedStrategy*.*",
    "**/BackupJsonExtensionsKt*.*",
    "**/BackupUrnetworkLocation*.*",
    "**/HttpUrlConnectionClient*.*",
    "**/AppLogger*.*",
    "**/BootDiagnostics*.*",
    "**/LogcatReader*.*",
    "**/LogcatLineParser*.*",
    "**/LogTailReader*.*",
    "**/UnifiedLogFileParser*.*",
    "**/BootFileLogger*.*",
    "**/UnifiedLogger*.*",
    "**/LogFileStore*.*",
    "**/AwgRuntime*.*",
    "**/WarpConfFileImporter*.*",
    "**/IniSanitizer*.*",
    "**/WarpAutoConfig*.*",
    "**/WarpEndpointProber*.*",
    "**/WarpConfigSlotStore*.*",
    "**/EvolutionEngine*.*",
    "**/ByeDpiArgvValidator*.*",
    "**/ByeDpiEngine*.*",
    "**/GeneMemory*.*",
    "**/StrategyEvolver*.*",
    "**/ByeDpiUiArgsBuilder*.*",
    "**/GenePool*.*",
    "**/ByeDpiOptionBlocks*.*",
    "**/UrnetworkLocationSelection*.*",
    "**/UrnetworkCachedLocation*.*",
    "**/Base58*.*",
    "**/UrnetworkConfig*.*",
    "**/UrnetworkContractStatusObserver*.*",
    "**/UrnetworkDeviceIdentity*.*",
    "**/UrnetworkAuthService\$DefaultImpls*.*",
    "**/InMemoryUrnetworkConfigStore*.*",
    "**/UrnetworkPreflight*.*",
    "**/ChainOrchestrator*.*",
    "**/SshjMasterDnsDeployer*.*",
    "**/MasterDnsClientWrapperContract*.*",
    "**/MasterDnsPortAllocator*.*",
    "**/MasterDnsServerDeployer*.*",
    "**/MasterDnsConfigWriter*.*",
    "**/MasterDnsResolversCache*.*",
    "**/Placeholder*.*"
)

if (isAndroid) {
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
                    "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                    "jacoco/testDebugUnitTest.exec"
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
                    "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                    "jacoco/testDebugUnitTest.exec"
                )
            }
        )

        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.95")
                }
            }
            rule {
                element = "BUNDLE"
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.95")
                }
            }
        }
    }
} else {
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")

        sourceDirectories.setFrom(
            layout.projectDirectory.files("src/main/java", "src/main/kotlin")
        )
        classDirectories.setFrom(
            files(layout.buildDirectory.dir("classes/kotlin/main"))
                .asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            fileTree(layout.buildDirectory) {
                include("jacoco/test.exec")
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
            files(layout.buildDirectory.dir("classes/kotlin/main"))
                .asFileTree.matching { exclude(excludedClasses) }
        )
        executionData.setFrom(
            fileTree(layout.buildDirectory) {
                include("jacoco/test.exec")
            }
        )

        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.95")
                }
            }
            rule {
                element = "BUNDLE"
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.95")
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
