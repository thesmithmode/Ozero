plugins {
    id("ozero.dynamic.feature")
    id("ozero.binaries")
}

// libtor.so + libiptproxy.so (lyrebird+snowflake+dnstt) скачиваются из
// GitHub Releases (RT.1.7.6). Источник: tor-android Maven Central + IPtProxy
// Maven Central, оба извлечены в один Release tag tor-<sha8>.
//
// Dynamic feature → .so автоматически уезжают в on-demand модуль (~34 МБ
// per ABI), скачиваются юзером по запросу через PlayCore SplitInstall (RT.5).
//
// JNI integration через IPtProxy.Controller / Tor JNI binding — RT.2/RT.3.
ozeroBinaries {
    artifact("libtor-arm64-v8a.so")
    artifact("libtor-armeabi-v7a.so")
    artifact("libtor-x86_64.so")
    artifact("libtor-x86.so")
    artifact("libiptproxy-arm64-v8a.so")
    artifact("libiptproxy-armeabi-v7a.so")
    artifact("libiptproxy-x86_64.so")
    artifact("libiptproxy-x86.so")
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
