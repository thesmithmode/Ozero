plugins {
    id("ozero.dynamic.feature")
}

android {
    namespace = "ru.ozero.dynamicfeature.tor"
}

dependencies {
    // Dynamic feature modules declare base app as implementation, not compile
    implementation(project(":app"))
    implementation(project(":engine-tor"))
    implementation(project(":core-api"))
}
