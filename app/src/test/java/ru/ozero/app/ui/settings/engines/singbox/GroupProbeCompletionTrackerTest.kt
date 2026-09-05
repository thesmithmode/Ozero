package ru.ozero.app.ui.settings.engines.singbox

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals

class GroupProbeCompletionTrackerTest {
    @Test
    fun `completion is idempotent ignores unknown profiles and clears group only after last profile`() {
        val state = MutableStateFlow(SingboxSettingsUiState(isPinging = setOf(GROUP_ID)))
        val profiles = listOf(profile(1L), profile(2L))
        val trackerClass = Class.forName(
            "ru.ozero.app.ui.settings.engines.singbox.GroupProbeCompletionTracker",
        )
        val constructor = trackerClass.declaredConstructors.single().apply { isAccessible = true }
        val tracker = constructor.newInstance(profiles, state)
        val complete = trackerClass.getDeclaredMethod(
            "complete",
            Long::class.javaPrimitiveType!!,
        ).apply {
            isAccessible = true
        }

        complete.invoke(tracker, 999L)
        assertEquals(setOf(GROUP_ID), state.value.isPinging)

        complete.invoke(tracker, 1L)
        assertEquals(setOf(GROUP_ID), state.value.isPinging)

        complete.invoke(tracker, 1L)
        assertEquals(setOf(GROUP_ID), state.value.isPinging)

        complete.invoke(tracker, 2L)
        assertEquals(emptySet(), state.value.isPinging)
    }

    private fun profile(id: Long) = ProxyProfile(
        id = id,
        groupId = GROUP_ID,
        name = "Profile $id",
        beanBlob = byteArrayOf(1),
        protocolType = 0,
    )

    private companion object {
        const val GROUP_ID = 10L
    }
}
