package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkPeerBadgeGatingTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/ui/MainScreen.kt")
        assertTrue(f.exists(), "MainScreen.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `UrnetworkPeerBadge скрывает строку при count=0 в первые секунды поиска`() {
        val body = source
            .substringAfter("private fun UrnetworkPeerBadge")
            .substringBefore("private const val URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S")
        assertTrue(
            body.contains("URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S"),
            "UrnetworkPeerBadge обязан использовать threshold для гейтинга — иначе badge " +
                "«пиры недоступны» появляется с первого тика и мигает в глаза. Body:\n$body",
        )
        assertTrue(
            body.contains("count > 0 ->"),
            "Branch для count > 0 обязан существовать (count=положительный сценарий). Body:\n$body",
        )
        assertTrue(
            body.contains("searchSeconds >= URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S"),
            "Branch для searchSeconds >= threshold обязан существовать. Body:\n$body",
        )
    }

    @Test
    fun `URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S не меньше 15`() {
        val regex = Regex("URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S\\s*:\\s*Int\\s*=\\s*(\\d+)")
        val m = regex.find(source) ?: error("URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S не найден")
        val threshold = m.groupValues[1].toInt()
        assertTrue(
            threshold >= 15,
            "threshold обязан быть >= 15s — иначе пользователь видит панику «пиры недоступны» в " +
                "первые секунды (URnetwork peer-search идёт ~10-20s). Fact=$threshold",
        )
    }
}
