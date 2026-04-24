// Required so buildSrc itself resolves plugin artifacts from the right repos
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "buildSrc"
