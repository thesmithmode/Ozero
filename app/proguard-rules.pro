# ── Obfuscation dictionaries ──────────────────────────────────────────────────
-obfuscationdictionary obfuscation-dict.txt
-classobfuscationdictionary obfuscation-dict.txt
-packageobfuscationdictionary obfuscation-dict.txt

# ── Native methods (JNI) ─────────────────────────────────────────────────────
-keepclasseswithmembernames class * { native <methods>; }
-keepclasseswithmembers class * { native <methods>; }

# ── Hilt / Dagger DI ─────────────────────────────────────────────────────────
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class ru.ozero.**.Hilt_* { *; }
-keep class **_HiltModules** { *; }
-keep class **_HiltModules_*Factory { *; }
-keep class **_HiltComponents { *; }
-keep class **_HiltComponents$* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class **_GeneratedInjector { *; }
-keep class **_AssistedFactory { *; }
-keep class **_AssistedFactory_Impl { *; }
-keep class Dagger* { *; }
-keep @dagger.assisted.AssistedInject class * { <init>(...); }
-keepclasseswithmembers class * { @javax.inject.Inject <init>(...); }
-keepclassmembers class * { @javax.inject.Inject <fields>; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keepclassmembers class * { @dagger.assisted.AssistedInject <init>(...); }
-keep class **_MapFactory { *; }
-keep class **_InjectedMapFactory { *; }
-keep class **_MapKeys { *; }
-keep class androidx.hilt.work.HiltWorkerFactory { *; }
-keep class androidx.hilt.work.** { *; }

# ── WorkManager / Startup ────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep @androidx.hilt.work.HiltWorker class * { <init>(...); }
-keep class * implements androidx.work.Configuration$Provider { *; }
-keep class * extends androidx.startup.Initializer { *; }

# ── Annotations / attributes ─────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-dontnote kotlinx.serialization.AnnotationsKt
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ── AIDL (cross-process IPC) ─────────────────────────────────────────────────
-keep class * extends android.os.IInterface { *; }
-keep class **$Stub { *; }
-keep class **$Stub$Proxy { *; }

# ── Kryo (singbox-fmt bean serialization, blobs stored in Room DB) ───────────
-keepclassmembers class ru.ozero.singboxfmt.** { <init>(); <fields>; }

# ── gomobile bridge — libbox.so calls via JNI (GetMethodID) ──────────────────
-keep class go.** { *; }
-dontwarn go.**
-keep class io.nekohasekai.** { *; }
-dontwarn io.nekohasekai.**
-keep class libcore.** { *; }
-keep class libsingboxgojni.** { *; }
-dontwarn libcore.**
-dontwarn libsingboxgojni.**

# ── Third-party libs without own consumer-rules ──────────────────────────────
-keep class android.util.Log { *; }

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.bouncycastle.jsse.**

-keep class com.bringyour.sdk.** { *; }
-dontwarn com.bringyour.sdk.**

-keep class net.schmizz.sshj.** { *; }
-keep interface net.schmizz.sshj.** { *; }
-dontwarn net.schmizz.sshj.**
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.i2p.crypto.eddsa.**

-dontwarn sun.security.**
-dontwarn javax.naming.**
-dontwarn com.jcraft.jzlib.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
