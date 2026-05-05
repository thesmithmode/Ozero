-keep class org.amnezia.awg.GoBackend { *; }
-keep class org.amnezia.awg.ProxyGoBackend { *; }
-keep class org.amnezia.awg.backend.** { *; }
-keep class org.amnezia.awg.hevtunnel.TProxyService { *; }
-keepclassmembers class org.amnezia.awg.** {
    public static native <methods>;
    native <methods>;
}
-dontwarn org.amnezia.awg.**
