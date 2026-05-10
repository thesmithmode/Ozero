package ru.ozero.app.ui.splittunnel

import android.content.pm.ApplicationInfo
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppListProviderTest {

    @Test
    fun `own package исключён всегда`() {
        val info = makeInfo("ru.ozero.app", flags = 0)
        assertFalse(
            isUserVisibleApp(info, emptySet(), "ru.ozero.app"),
            "Сам Ozero не показывать в split-tunnel — нет смысла туннелировать сам себя.",
        )
    }

    @Test
    fun `user app без launcher отображается`() {
        val info = makeInfo("com.example.bg", flags = 0)
        assertTrue(
            isUserVisibleApp(info, emptySet(), "ru.ozero.app"),
            "User-installed app без LAUNCHER intent должен быть в split-tunnel списке — " +
                "это background services / push-only apps которые юзер всё равно ставил сам.",
        )
    }

    @Test
    fun `user app с launcher отображается`() {
        val info = makeInfo("com.example.app", flags = 0)
        assertTrue(isUserVisibleApp(info, setOf("com.example.app"), "ru.ozero.app"))
    }

    @Test
    fun `system app без launcher скрыт`() {
        val info = makeInfo("com.android.systemservice", flags = ApplicationInfo.FLAG_SYSTEM)
        assertFalse(
            isUserVisibleApp(info, emptySet(), "ru.ozero.app"),
            "Pure system service без launcher — мусор в UI, скрыть.",
        )
    }

    @Test
    fun `system app с launcher отображается`() {
        val info = makeInfo("com.android.settings", flags = ApplicationInfo.FLAG_SYSTEM)
        assertTrue(
            isUserVisibleApp(info, setOf("com.android.settings"), "ru.ozero.app"),
            "System app с LAUNCHER (Settings, Calculator) — пользователь хочет таргетить.",
        )
    }

    @Test
    fun `updated system app отображается даже без launcher`() {
        val info = makeInfo(
            "com.google.android.gms",
            flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
        )
        assertTrue(
            isUserVisibleApp(info, emptySet(), "ru.ozero.app"),
            "Updated system apps (Play Services, Chrome) — фактически user apps, показывать.",
        )
    }

    @Test
    fun `loadApps кэширует — повторный вызов не сканирует PackageManager заново`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/splittunnel/AppListProvider.kt",
        ).readText()
        assertTrue(
            source.contains("@Volatile private var listCache: List<InstalledApp>?") &&
                source.contains("listCache?.let { return"),
            "DefaultAppListProvider обязан кэшировать loadApps() — повторный сканс PackageManager " +
                "при каждом открытии split-tunnel экрана = bottleneck (200+ приложений). " +
                "PORTAL_WG-style: загрузить один раз, переиспользовать. Source:\n${source.take(2000)}",
        )
        assertTrue(
            source.contains("@Singleton"),
            "DefaultAppListProvider обязан быть @Singleton — иначе кэш per-instance бесполезен.",
        )
    }

    @Test
    fun `loadIcon кэширует результат`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/splittunnel/AppListProvider.kt",
        ).readText()
        assertTrue(
            source.contains("iconCache") &&
                source.contains("iconCache[packageName]"),
            "loadIcon обязан кэшировать ImageBitmap — иначе при scroll LazyColumn повторные " +
                "decodes одной и той же иконки = jank.",
        )
        assertTrue(
            source.contains("loadMetadata") &&
                source.contains("icon = null"),
            "loadApps обязан возвращать metadata БЕЗ иконок — иконки lazy через loadIcon. " +
                "Иначе первый вход в split-tunnel = 1-3 сек блокировки на 200+ apps * loadIcon.",
        )
    }

    @Test
    fun `manifest содержит QUERY_ALL_PACKAGES permission`() {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val manifest = File(moduleRoot, "src/main/AndroidManifest.xml")
        assertTrue(manifest.exists(), "AndroidManifest.xml не найден: $manifest")
        val text = manifest.readText()
        assertTrue(
            text.contains("android.permission.QUERY_ALL_PACKAGES"),
            "Без QUERY_ALL_PACKAGES Android 11+ возвращает обрезанный getInstalledApplications " +
                "и пользователь не видит часть приложений в split-tunnel.",
        )
    }

    private fun makeInfo(pkg: String, flags: Int): ApplicationInfo {
        val info = ApplicationInfo()
        info.packageName = pkg
        info.flags = flags
        return info
    }
}
