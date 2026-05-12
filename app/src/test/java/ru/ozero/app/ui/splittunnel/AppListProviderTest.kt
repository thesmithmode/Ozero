package ru.ozero.app.ui.splittunnel

import android.Manifest
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppListProviderTest {

    @Test
    fun `holdsInternetPermission true когда INTERNET в списке`() {
        assertTrue(
            holdsInternetPermission(arrayOf(Manifest.permission.INTERNET)),
            "PackageInfo с INTERNET в requestedPermissions должен возвращать true.",
        )
    }

    @Test
    fun `holdsInternetPermission true среди других permissions`() {
        assertTrue(
            holdsInternetPermission(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.INTERNET,
                    Manifest.permission.READ_CONTACTS,
                ),
            ),
        )
    }

    @Test
    fun `holdsInternetPermission false когда нет INTERNET`() {
        assertFalse(holdsInternetPermission(arrayOf(Manifest.permission.CAMERA)))
    }

    @Test
    fun `holdsInternetPermission false для null permissions`() {
        assertFalse(holdsInternetPermission(null))
    }

    @Test
    fun `holdsInternetPermission false для пустого массива`() {
        assertFalse(holdsInternetPermission(emptyArray()))
    }

    @Test
    fun `loadMetadata использует getPackagesHoldingPermissions INTERNET`() {
        val source = readProviderSource()
        assertTrue(
            source.contains("getPackagesHoldingPermissions") &&
                source.contains("Manifest.permission.INTERNET"),
            "loadMetadata обязан использовать getPackagesHoldingPermissions(INTERNET) " +
                "вместо getInstalledApplications + системного фильтра. " +
                "amnezia/PORTAL WG показывает все приложения с INTERNET — это включает " +
                "Google Play Services, WebView providers, push-сервисы, captive-portal-login, " +
                "которые наш старый фильтр isUserVisibleApp скрывал как 'pure system'.",
        )
    }

    @Test
    fun `loadMetadata исключает own package`() {
        val source = readProviderSource()
        assertTrue(
            source.contains("it.packageName != ownPackage"),
            "loadMetadata обязан фильтровать сам Ozero — нет смысла туннелировать сам себя.",
        )
    }

    @Test
    fun `loadMetadata НЕ использует устаревший фильтр isUserVisibleApp`() {
        val source = readProviderSource()
        assertFalse(
            source.contains("isUserVisibleApp"),
            "isUserVisibleApp фильтр удалён — он скрывал системные сервисы " +
                "(Play Services, push-only) которые юзер хочет видеть в split-tunnel.",
        )
    }

    @Test
    fun `loadMetadata добавляет work profile приложения через LauncherApps`() {
        val source = readProviderSource()
        assertTrue(
            source.contains("LauncherApps") &&
                source.contains("launcherApps.profiles") &&
                source.contains("launcherApps.getActivityList"),
            "Cross-profile work profile apps собираются через LauncherApps.profiles → getActivityList. " +
                "PORTAL WG показывает приложения work profile отдельно от main user — " +
                "split-tunnel должен покрывать оба профиля одинаково.",
        )
    }

    @Test
    fun `LauncherApps итерация profiles защищена runCatching per-profile`() {
        val source = readProviderSource()
        val profilesBlock = source.substringAfter("for (profile in launcherApps.profiles)")
        assertTrue(
            profilesBlock.trimStart().startsWith("{") && profilesBlock.contains("runCatching"),
            "runCatching обязан быть ВНУТРИ цикла per-profile. " +
                "Иначе SecurityException на locked work profile дропает все следующие профили.",
        )
    }

    @Test
    fun `loadApps кэширует — повторный вызов не сканирует PackageManager заново`() {
        val source = readProviderSource()
        assertTrue(
            source.contains("@Volatile private var listCache: List<InstalledApp>?") &&
                source.contains("listCache?.let { return"),
            "DefaultAppListProvider обязан кэшировать loadApps() — повторный сканс PackageManager " +
                "при каждом открытии split-tunnel экрана = bottleneck (200+ приложений).",
        )
        assertTrue(
            source.contains("@Singleton"),
            "DefaultAppListProvider обязан быть @Singleton — иначе кэш per-instance бесполезен.",
        )
    }

    @Test
    fun `loadIcon кэширует результат`() {
        val source = readProviderSource()
        assertTrue(
            source.contains("iconCache") && source.contains("iconCache[packageName]"),
            "loadIcon обязан кэшировать ImageBitmap — иначе при scroll LazyColumn повторные " +
                "decodes одной и той же иконки = jank.",
        )
        assertTrue(
            source.contains("loadMetadata") && source.contains("icon = null"),
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
            "Без QUERY_ALL_PACKAGES Android 11+ возвращает обрезанный список и " +
                "getPackagesHoldingPermissions не видит приложения вне <queries>.",
        )
    }

    private fun readProviderSource(): String = File(
        System.getProperty("user.dir") ?: ".",
        "src/main/java/ru/ozero/app/ui/splittunnel/AppListProvider.kt",
    ).readText()
}
