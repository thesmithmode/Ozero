package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrafficStatsCardSentinelTest {

    private val source by lazy {
        val f = File(System.getProperty("user.dir") ?: ".", "src/main/java/ru/ozero/app/ui/MainScreen.kt")
        assertTrue(f.exists(), "MainScreen.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `TrafficStatsCard принимает nullable TunnelStats — пустой стейт без скрытия карточки`() {
        assertTrue(
            source.contains("private fun TrafficStatsCard(") &&
                Regex("private fun TrafficStatsCard\\([^)]*stats:\\s*TunnelStats\\?", RegexOption.DOT_MATCHES_ALL)
                    .containsMatchIn(source),
            "TrafficStatsCard.stats обязан быть nullable: при stats=null карточка показывает нули, " +
                "а не исчезает с экрана. Иначе speed-card мигает каждый раз когда TunnelController " +
                "сбрасывает stats до null между движками/тиками — пользователь видит пляшущий UI.",
        )
    }

    @Test
    fun `главный экран рендерит TrafficStatsCard без gate stats != null`() {
        val expertBlock = source.substringAfter("ExpertMainContent")
            .substringBefore("@Composable\nprivate fun TrafficStatsCard(")
        assertFalse(
            Regex("if\\s*\\(\\s*isConnected\\s*&&\\s*stats\\s*!=\\s*null\\s*\\)").containsMatchIn(expertBlock),
            "ExpertMainContent НЕ должен гейтить TrafficStatsCard через `stats != null` — " +
                "карточка обязана быть статичной (видна всегда когда isConnected). " +
                "Гейт через null приводит к появлению/исчезновению карточки на каждом null-тике stats. " +
                "Block:\n${expertBlock.take(2000)}",
        )
    }
}
