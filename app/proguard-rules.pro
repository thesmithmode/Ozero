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
}

# ---------------------------------------------------------------------------
# Anti-tamper: запрещаем переименование классов SecurityGuard и проверок —
# атакующий должен явно знать, какой класс хукать. Если хотим чтобы они
# тоже были obfuscated — комментим (рискуем сломать reflection из тестов).
# ---------------------------------------------------------------------------
-keep class ru.ozero.security.SecurityGuard { *; }
-keep class ru.ozero.security.signature.ApkSignatureVerifier { *; }

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
