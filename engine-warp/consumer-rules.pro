-keep class org.amnezia.awg.GoBackend { *; }
-keep class org.amnezia.awg.ProxyGoBackend { *; }
-keep interface org.amnezia.awg.backend.SocketProtector { *; }
-keepclassmembers class org.amnezia.awg.** {
    public static native <methods>;
    native <methods>;
}
-dontwarn org.amnezia.awg.**
