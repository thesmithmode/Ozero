-keep class ru.ozero.enginescore.** { *; }
-keep class ru.ozero.commonvpn.OzeroVpnService { *; }
-keep class ru.ozero.commonvpn.HevTunnelGateway { *; }

-keep class hev.** { *; }
-keep class ru.ozero.enginebyedpi.ByeDpiProxy { *; }

-keep class android.util.Log { *; }
-keep class ru.ozero.commoncrypto.** { *; }
-keep class ru.ozero.commondns.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

-keepclasseswithmembernames class * { native <methods>; }
-keepclasseswithmembers class * { native <methods>; }

-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class ru.ozero.app.OzeroApp { *; }
-keep class ru.ozero.app.MainActivity { *; }
-keep class ru.ozero.app.Hilt_* { *; }
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

-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep @androidx.hilt.work.HiltWorker class * { <init>(...); }
-keep class * implements androidx.work.Configuration$Provider { *; }
-keep class * extends androidx.startup.Initializer { *; }

-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-dontnote kotlinx.serialization.AnnotationsKt

-keep class androidx.compose.runtime.** { *; }

-keep class ru.ozero.app.** { *; }
-keep class ru.ozero.app.logging.BootFileLogger { *; }
-keep class ru.ozero.app.logging.UnifiedLogger { *; }
-keep class ru.ozero.app.logging.BootDiagnostics { *; }
-keep class ru.ozero.commonvpn.** { *; }

-keep class ru.ozero.corestorage.** { *; }

-keep class ru.ozero.enginebyedpi.** { *; }
-keep class ru.ozero.enginewarp.** { *; }
-keep class ru.ozero.engineurnetwork.** { *; }

-keep class org.amnezia.awg.** { *; }
-dontwarn org.amnezia.awg.**

-keep class com.bringyour.sdk.** { *; }
-dontwarn com.bringyour.sdk.**

-keep class **_MapFactory { *; }
-keep class **_InjectedMapFactory { *; }
-keep class **_MapKeys { *; }
-keep class androidx.hilt.work.HiltWorkerFactory { *; }
-keep class androidx.hilt.work.** { *; }

-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
