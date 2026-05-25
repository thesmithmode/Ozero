pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ozero"

include(":app")
include(":engines-core")
include(":core-storage")
include(":common-vpn")
include(":common-dns")
include(":common-net")
include(":common-crypto")
include(":engine-byedpi")
include(":engine-urnetwork")
include(":engine-warp")
include(":engine-masterdns")
include(":engine-fptn")
include(":core-backup")
include(":engine-singbox")
include(":singbox-process")
include(":singbox-core")
include(":singbox-fmt")
include(":singbox-config")
include(":singbox-room")
include(":singbox-subscription")
