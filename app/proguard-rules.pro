# E10: Security hardening — R8 full mode + obfuscation rules
#
# AGP 8.x default = R8 full mode (-allowaccessmodification, agressive optimizations).
# Эти правила дополняют дефолтные.

# ---------------------------------------------------------------------------
# Сохраняем то, что использует reflection / native bridge
# ---------------------------------------------------------------------------
-keep class ru.ozero.coreapi.** { *; }
-keep class ru.ozero.commonvpn.OzeroVpnService { *; }
-keep class ru.ozero.engineamnezia.LibAwgDelegate { *; }
-keep class ru.ozero.enginehysteria2.LibHy2Delegate { *; }
-keep class ru.ozero.enginexray.LibXrayDelegate { *; }
-keep class ru.ozero.enginenaive.LibNaiveDelegate { *; }
-keep class ru.ozero.enginetor.LibTorDelegate { *; }

# Hilt / Dagger generated
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keepnames class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager

# Kotlinx serialization (если используется в JSON-парсерах подписок)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Compose runtime
-keep class androidx.compose.runtime.** { *; }

# ---------------------------------------------------------------------------
# Удаляем логи в release (минимизирует утечку информации через logcat)
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# ---------------------------------------------------------------------------
# Anti-tamper: SecurityGuard и ApkSignatureVerifier ОБФУСЦИРОВАНЫ полностью —
# атакующий не может тривиально хукнуть Frida-скрипт по имени класса/метода.
# Раньше -keep оставлял имена нетронутыми = бесплатный bypass всех проверок
# (Frida скрипт `Java.use("ru.ozero.security.SecurityGuard").check.implementation = ...`).
# Эти классы вызываются прямо из Kotlin-кода, рефлексия не нужна → R8 переименует.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Hide source file names в стек-трейсах (затрудняет reverse engineering)
# ---------------------------------------------------------------------------
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ---------------------------------------------------------------------------
# Aggressive optimization
# ---------------------------------------------------------------------------
-allowaccessmodification
-repackageclasses 'o'
