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
include(":core-api")
include(":core-orchestrator")
include(":core-subscriptions")
include(":core-storage")
include(":common-vpn")
include(":common-dns")
include(":common-crypto")
include(":common-json")
include(":engine-byedpi")
include(":security")
