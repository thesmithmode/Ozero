package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MainScreenFontScaleTest {
    @Test
    fun `обычный масштаб остается без изменений`() {
        assertEquals(1f, boundedMainScreenFontScale(1f))
    }

    @Test
    fun `уменьшенный пользователем масштаб остается без изменений`() {
        assertEquals(0.85f, boundedMainScreenFontScale(0.85f))
    }

    @Test
    fun `доступный увеличенный масштаб сохраняется`() {
        assertEquals(1.2f, boundedMainScreenFontScale(1.2f))
    }

    @Test
    fun `максимальный поддерживаемый масштаб сохраняется`() {
        assertEquals(1.3f, boundedMainScreenFontScale(1.3f))
    }

    @Test
    fun `системный сверхкрупный масштаб ограничивается`() {
        assertEquals(1.3f, boundedMainScreenFontScale(2f))
    }
}
