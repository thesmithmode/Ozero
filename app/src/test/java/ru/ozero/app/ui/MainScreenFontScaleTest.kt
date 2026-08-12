package ru.ozero.app.ui

import androidx.compose.ui.unit.dp
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MainScreenFontScaleTest {

    @Test
    fun `обычный масштаб сохраняет исходную компоновку`() {
        assertFalse(isCompactMainLayout(width = 412.dp, height = 840.dp, fontScale = 1f))
    }

    @Test
    fun `уменьшенный масштаб сохраняет исходную компоновку`() {
        assertFalse(isCompactMainLayout(width = 412.dp, height = 840.dp, fontScale = 0.85f))
    }

    @Test
    fun `увеличенный масштаб включает компактную компоновку`() {
        assertTrue(isCompactMainLayout(width = 412.dp, height = 840.dp, fontScale = 1.2f))
    }

    @Test
    fun `маленькая высота включает компактную компоновку`() {
        assertTrue(isCompactMainLayout(width = 412.dp, height = 700.dp, fontScale = 1f))
    }

    @Test
    fun `узкая ширина включает компактную компоновку`() {
        assertTrue(isCompactMainLayout(width = 320.dp, height = 840.dp, fontScale = 1f))
    }
}
