package ru.ozero.app.ui.settings.engines.singbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup
import ru.ozero.singboxsubscription.GroupSeeder
import ru.ozero.singboxsubscription.RawUpdater
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class SingboxEngineSettingsViewModelCoverageTest {

    private val sortOrderKey = intPreferencesKey("singbox_sort_order")
    private val dispatcher = StandardTestDispatcher()
    private val stateCollectionJobs = mutableListOf<Job>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        stateCollectionJobs.forEach { it.cancel() }
        stateCollectionJobs.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `sort order defaults to BY_LATENCY when preference is absent`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        assertEquals(SortOrder.BY_LATENCY, harness.viewModel.state.value.sortOrder)
    }

    @Test
    fun `invalid sort order preference falls back to BY_LATENCY and sorts by latency`() = runTest {
        val harness = Harness(
            initialPreferences = mutablePreferencesOf(sortOrderKey to 99),
            initialGroups = listOf(group(id = 1L, userOrder = 0)),
            initialProfiles = listOf(
                profile(id = 11L, groupId = 1L, name = "Zulu", userOrder = 1, latencyMs = 80),
                profile(id = 12L, groupId = 1L, name = "alpha", userOrder = 0, latencyMs = 10),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onGroupExpand(1L)
        advanceUntilIdle()

        assertEquals(SortOrder.BY_LATENCY, harness.viewModel.state.value.sortOrder)
        assertEquals(listOf(12L, 11L), harness.viewModel.state.value.groupProfiles.getValue(1L).map { it.id })
    }

    @Test
    fun `onSortOrderChanged persists BY_NAME and resorts expanded group`() = runTest {
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0)),
            initialProfiles = listOf(
                profile(id = 21L, groupId = 1L, name = "Zulu", userOrder = 1, latencyMs = 1),
                profile(id = 22L, groupId = 1L, name = "alpha", userOrder = 0, latencyMs = 100),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onGroupExpand(1L)
        advanceUntilIdle()

        harness.viewModel.onSortOrderChanged(SortOrder.BY_NAME)
        advanceUntilIdle()

        assertEquals(SortOrder.BY_NAME, harness.viewModel.state.value.sortOrder)
        assertEquals(SortOrder.BY_NAME.ordinal, harness.prefsFlow.value[sortOrderKey])
        assertEquals(listOf(22L, 21L), harness.viewModel.state.value.groupProfiles.getValue(1L).map { it.id })
    }

    @Test
    fun `onProbeTimeoutSecondsChange clamps and persists milliseconds`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onProbeTimeoutSecondsChange("45")
        advanceUntilIdle()

        assertEquals(30_000, harness.prefsFlow.value[SingboxProbeService.PROBE_TIMEOUT_MS_KEY])
        assertEquals(30, harness.viewModel.state.value.probeTimeoutSeconds)
    }

    @Test
    fun `add group dialog show and hide preserve then reset fields`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onAddGroupFieldChanged(name = "Custom", url = "https://example.com")
        harness.viewModel.onAddGroupDialog(true)
        advanceUntilIdle()

        assertTrue(harness.viewModel.state.value.showAddGroupDialog)
        assertEquals("Custom", harness.viewModel.state.value.addGroupName)
        assertEquals("https://example.com", harness.viewModel.state.value.addGroupUrl)

        harness.viewModel.onAddGroupDialog(false)
        advanceUntilIdle()

        assertFalse(harness.viewModel.state.value.showAddGroupDialog)
        assertEquals("", harness.viewModel.state.value.addGroupName)
        assertEquals("", harness.viewModel.state.value.addGroupUrl)
        assertNull(harness.viewModel.state.value.addGroupError)
    }

    @Test
    fun `empty add group url sets validation error and skips insert`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onAddGroupFieldChanged(name = "New group", url = "   ")
        harness.viewModel.onAddGroupConfirm()
        advanceUntilIdle()

        assertEquals("empty", harness.viewModel.state.value.addGroupError)
        assertTrue(harness.insertedGroups.isEmpty())
    }

    @Test
    fun `blank add group name uses generated default name`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onAddGroupFieldChanged(name = "   ", url = " https://example.com/sub ")
        harness.viewModel.onAddGroupConfirm()
        advanceUntilIdle()

        assertEquals(1, harness.insertedGroups.size)
        assertEquals("Ozero-1", harness.insertedGroups.single().name)
        assertEquals("https://example.com/sub", harness.insertedGroups.single().subscriptionUrl)
        assertFalse(harness.viewModel.state.value.showAddGroupDialog)
        assertEquals("", harness.viewModel.state.value.addGroupName)
        assertEquals("", harness.viewModel.state.value.addGroupUrl)
        assertNull(harness.viewModel.state.value.addGroupError)
    }

    @Test
    fun `explicit add group name is trimmed and user order follows current groups`() = runTest {
        val harness = Harness(initialGroups = listOf(group(id = 7L, userOrder = 0)))
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onAddGroupFieldChanged(name = "  Work  ", url = " https://example.com/work ")
        harness.viewModel.onAddGroupConfirm()
        advanceUntilIdle()

        assertEquals("Work", harness.insertedGroups.single().name)
        assertEquals("https://example.com/work", harness.insertedGroups.single().subscriptionUrl)
        assertEquals(1, harness.insertedGroups.single().userOrder)
    }

    @Test
    fun `new subscription group is appended after highest existing user order`() = runTest {
        val harness = Harness(
            initialGroups = listOf(
                group(id = 1L, userOrder = 0),
                group(id = 2L, userOrder = 10),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onAddGroupFieldChanged(name = "Tail", url = "https://example.com/tail")
        harness.viewModel.onAddGroupConfirm()
        advanceUntilIdle()

        assertEquals(11, harness.insertedGroups.single().userOrder)
    }

    @Test
    fun `manual links dialog show and hide preserve then reset fields`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onShowAddMenu(true)
        harness.viewModel.onManualLinksFieldChanged(input = "vless://id@host:443?type=tcp", groupName = "Manual")
        harness.viewModel.onShowAddManualLinksDialog(true)
        advanceUntilIdle()

        assertTrue(harness.viewModel.state.value.showAddManualLinksDialog)
        assertFalse(harness.viewModel.state.value.showAddMenu)
        assertEquals("vless://id@host:443?type=tcp", harness.viewModel.state.value.manualLinksInput)
        assertEquals("Manual", harness.viewModel.state.value.manualLinksGroupName)

        harness.viewModel.onShowAddManualLinksDialog(false)
        advanceUntilIdle()

        assertFalse(harness.viewModel.state.value.showAddManualLinksDialog)
        assertEquals("", harness.viewModel.state.value.manualLinksInput)
        assertEquals("", harness.viewModel.state.value.manualLinksGroupName)
        assertNull(harness.viewModel.state.value.manualLinksError)
    }

    @Test
    fun `empty manual links input sets validation error and invalid input clears on edit`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onManualLinksFieldChanged(input = "   ")
        harness.viewModel.onConfirmManualLinks()
        advanceUntilIdle()

        assertEquals("empty", harness.viewModel.state.value.manualLinksError)

        harness.viewModel.onManualLinksFieldChanged(
            input = "vless://12345678-1234-1234-1234-123456789abc@host.example.com:443?type=tcp",
        )
        advanceUntilIdle()

        assertNull(harness.viewModel.state.value.manualLinksError)
    }

    @Test
    fun `invalid manual links input sets parse error and skips insert`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onManualLinksFieldChanged(input = "not-a-link")
        harness.viewModel.onConfirmManualLinks()
        advanceUntilIdle()

        assertEquals("parse", harness.viewModel.state.value.manualLinksError)
        assertTrue(harness.insertedGroups.isEmpty())
        assertTrue(harness.profileDao.insertedProfiles.isEmpty())
    }

    @Test
    fun `blank manual links group name uses generated default name and server fallback`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onManualLinksFieldChanged(
            input = "vless://12345678-1234-1234-1234-123456789abc@host.example.com:443?type=tcp",
            groupName = "   ",
        )
        harness.viewModel.onConfirmManualLinks()
        advanceUntilIdle()

        assertEquals(1, harness.insertedGroups.size)
        assertEquals("Ozero-1", harness.insertedGroups.single().name)
        assertEquals(1, harness.profileDao.insertedProfiles.size)
        assertEquals("Server 1", harness.profileDao.insertedProfiles.single().name)
        assertFalse(harness.viewModel.state.value.showAddManualLinksDialog)
        assertEquals("", harness.viewModel.state.value.manualLinksInput)
        assertEquals("", harness.viewModel.state.value.manualLinksGroupName)
        assertNull(harness.viewModel.state.value.manualLinksError)
    }

    @Test
    fun `chain profile ids keep profiles outside visible state`() = runTest {
        val harness = Harness(
            initialProfiles = listOf(
                profile(id = 10L, groupId = 1L, name = "First", userOrder = 0),
                profile(id = 20L, groupId = 1L, name = "Second", userOrder = 1),
            ),
            initialChain = listOf(
                chainStep(profileId = 10L, userOrder = 0),
                chainStep(profileId = 99L, userOrder = 1),
                chainStep(profileId = 20L, userOrder = 2),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        assertEquals(listOf(10L, 99L, 20L), harness.viewModel.state.value.chainProfileIds)
    }

    @Test
    fun `onChainProfileAdd appends unseen profile once`() = runTest {
        val harness = Harness(
            initialChain = listOf(
                chainStep(profileId = 1L, userOrder = 0),
                chainStep(profileId = 2L, userOrder = 1),
            ),
            initialProfiles = listOf(
                profile(id = 1L, groupId = 1L, name = "One", userOrder = 0),
                profile(id = 2L, groupId = 1L, name = "Two", userOrder = 1),
                profile(id = 3L, groupId = 1L, name = "Three", userOrder = 2),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onChainProfileAdd(profile(id = 3L, groupId = 1L, name = "Three", userOrder = 2))
        advanceUntilIdle()
        harness.viewModel.onChainProfileAdd(profile(id = 3L, groupId = 1L, name = "Three", userOrder = 2))
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L, 3L), harness.chainDao.lastReplace())
        assertEquals(1, harness.chainDao.replaceCalls.size)
        assertEquals(listOf(1L, 2L, 3L), harness.viewModel.state.value.chainProfileIds)
    }

    @Test
    fun `onChainProfileRemove drops requested profile and ignores missing id`() = runTest {
        val harness = Harness(
            initialChain = listOf(
                chainStep(profileId = 1L, userOrder = 0),
                chainStep(profileId = 2L, userOrder = 1),
                chainStep(profileId = 3L, userOrder = 2),
            ),
            initialProfiles = listOf(
                profile(id = 1L, groupId = 1L, name = "One", userOrder = 0),
                profile(id = 2L, groupId = 1L, name = "Two", userOrder = 1),
                profile(id = 3L, groupId = 1L, name = "Three", userOrder = 2),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onChainProfileRemove(2L)
        advanceUntilIdle()
        harness.viewModel.onChainProfileRemove(99L)
        advanceUntilIdle()

        assertEquals(listOf(1L, 3L), harness.chainDao.lastReplace())
        assertEquals(2, harness.chainDao.replaceCalls.size)
        assertEquals(listOf(1L, 3L), harness.viewModel.state.value.chainProfileIds)
    }

    @Test
    fun `onChainProfileMove clamps movement at bounds`() = runTest {
        val harness = Harness(
            initialChain = listOf(
                chainStep(profileId = 1L, userOrder = 0),
                chainStep(profileId = 2L, userOrder = 1),
                chainStep(profileId = 3L, userOrder = 2),
            ),
            initialProfiles = listOf(
                profile(id = 1L, groupId = 1L, name = "One", userOrder = 0),
                profile(id = 2L, groupId = 1L, name = "Two", userOrder = 1),
                profile(id = 3L, groupId = 1L, name = "Three", userOrder = 2),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onChainProfileMove(1L, -10)
        advanceUntilIdle()
        harness.viewModel.onChainProfileMove(3L, 10)
        advanceUntilIdle()

        assertTrue(harness.chainDao.replaceCalls.isEmpty())
        assertEquals(listOf(1L, 2L, 3L), harness.viewModel.state.value.chainProfileIds)
    }

    @Test
    fun `onChainProfileMove reorders profile within bounds`() = runTest {
        val harness = Harness(
            initialChain = listOf(
                chainStep(profileId = 1L, userOrder = 0),
                chainStep(profileId = 2L, userOrder = 1),
                chainStep(profileId = 3L, userOrder = 2),
            ),
            initialProfiles = listOf(
                profile(id = 1L, groupId = 1L, name = "One", userOrder = 0),
                profile(id = 2L, groupId = 1L, name = "Two", userOrder = 1),
                profile(id = 3L, groupId = 1L, name = "Three", userOrder = 2),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onChainProfileMove(2L, -1)
        advanceUntilIdle()

        assertEquals(listOf(2L, 1L, 3L), harness.chainDao.lastReplace())
        assertEquals(1, harness.chainDao.replaceCalls.size)
        assertEquals(listOf(2L, 1L, 3L), harness.viewModel.state.value.chainProfileIds)
    }

    @Test
    fun `onProfileSelect persists selected profile id and bean blob`() = runTest {
        val profile = profile(id = 41L, groupId = 1L, name = "Chosen", userOrder = 0)
        val harness = Harness(initialProfiles = listOf(profile))
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onProfileSelect(profile)
        advanceUntilIdle()

        assertEquals(41L, harness.prefsFlow.value[SingboxProbeService.SELECTED_PROFILE_KEY])
        assertTrue(
            harness.prefsFlow.value[SingboxProbeService.BEAN_KEY]?.contentEquals(profile.beanBlob) == true,
        )
    }

    @Test
    fun `onSetAutoSelect toggles selected profile preference and clears bean`() = runTest {
        val profile = profile(id = 51L, groupId = 1L, name = "Chosen", userOrder = 0)
        val harness = Harness(initialProfiles = listOf(profile))
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onProfileSelect(profile)
        advanceUntilIdle()
        harness.viewModel.onSetAutoSelect(true)
        advanceUntilIdle()

        assertEquals(-1L, harness.prefsFlow.value[SingboxProbeService.SELECTED_PROFILE_KEY])
        assertNull(harness.prefsFlow.value[SingboxProbeService.BEAN_KEY])

        harness.viewModel.onSetAutoSelect(false)
        advanceUntilIdle()

        assertFalse(harness.prefsFlow.value.contains(SingboxProbeService.SELECTED_PROFILE_KEY))
        assertNull(harness.prefsFlow.value[SingboxProbeService.BEAN_KEY])
    }

    @Test
    fun `onCancel clears pinging and refreshing state`() = runTest {
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0)),
            initialProfiles = listOf(profile(id = 61L, groupId = 1L, name = "Ping", userOrder = 0)),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onPing(groupId = 1L)
        advanceUntilIdle()
        harness.viewModel.onRefresh(groupId = 1L)
        advanceUntilIdle()
        harness.viewModel.onCancel(ping = true, refresh = true)
        advanceUntilIdle()

        assertTrue(harness.viewModel.state.value.isPinging.isEmpty())
        assertTrue(harness.viewModel.state.value.testingProfileIds.isEmpty())
        assertTrue(harness.viewModel.state.value.isRefreshing.isEmpty())
    }

    @Test
    fun `onDeleteGroup removes group from source and state`() = runTest {
        val harness = Harness(initialGroups = listOf(group(id = 1L, userOrder = 0)))
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onDeleteGroup(group(id = 1L, userOrder = 0))
        advanceUntilIdle()

        assertTrue(harness.groupsFlow.value.isEmpty())
        assertTrue(harness.viewModel.state.value.groups.isEmpty())
    }

    @Test
    fun `onCancel can clear only ping state and keep refresh marker`() = runTest {
        val harness = Harness(initialGroups = listOf(group(id = 1L, userOrder = 0)))
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onRefresh(1L)
        harness.viewModel.onCancel(ping = true, refresh = false)
        advanceUntilIdle()

        assertTrue(harness.viewModel.state.value.isPinging.isEmpty())
        assertTrue(harness.viewModel.state.value.testingProfileIds.isEmpty())
    }

    @Test
    fun `restore defaults reads preset asset and clears restore error`() = runTest {
        val harness = Harness()
        val json = """
            {"groups":[
                {"name":"Preset A","url":"https://example.com/a"},
                {"name":"Preset B","url":"https://example.com/b"}
            ]}
        """.trimIndent()
        every { harness.appContext.assets.open("singbox/preset_groups.json") } returns
            ByteArrayInputStream(json.toByteArray())
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onRestoreDefaults()
        advanceUntilIdle()

        coVerify {
            harness.groupSeeder.seedPresets(
                listOf(
                    GroupSeeder.PresetGroup("Preset A", "https://example.com/a"),
                    GroupSeeder.PresetGroup("Preset B", "https://example.com/b"),
                ),
            )
        }
        assertFalse(harness.viewModel.state.value.isRestoringDefaults)
        assertNull(harness.viewModel.state.value.restoreError)
    }

    @Test
    fun `restore defaults stores error when preset asset is missing`() = runTest {
        val harness = Harness()
        every { harness.appContext.assets.open("singbox/preset_groups.json") } throws IllegalStateException("missing")
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onRestoreDefaults()
        advanceUntilIdle()

        assertFalse(harness.viewModel.state.value.isRestoringDefaults)
        assertEquals("missing", harness.viewModel.state.value.restoreError)
    }

    @Test
    fun `import from file with invalid text opens manual dialog with filename default`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onImportFromFile("not a proxy link", "Imported.txt")
        advanceUntilIdle()

        assertTrue(harness.viewModel.state.value.showAddManualLinksDialog)
        assertEquals("not a proxy link", harness.viewModel.state.value.manualLinksInput)
        assertEquals("Imported", harness.viewModel.state.value.manualLinksGroupName)
        assertEquals("parse", harness.viewModel.state.value.manualLinksError)
    }

    @Test
    fun `import from file invalid text without filename uses blank group name and truncates input`() = runTest {
        val harness = Harness()
        val text = "x".repeat(2_100)
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onImportFromFile(text, null)
        advanceUntilIdle()

        assertTrue(harness.viewModel.state.value.showAddManualLinksDialog)
        assertEquals(2_000, harness.viewModel.state.value.manualLinksInput.length)
        assertEquals("", harness.viewModel.state.value.manualLinksGroupName)
        assertEquals("parse", harness.viewModel.state.value.manualLinksError)
    }

    @Test
    fun `import from file creates group and profiles from valid links`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onImportFromFile(
            "vless://12345678-1234-1234-1234-123456789abc@host.example.com:443?type=tcp#Imported",
            null,
        )
        advanceUntilIdle()

        assertEquals("Ozero-1", harness.insertedGroups.single().name)
        assertEquals(1, harness.profileDao.insertedProfiles.size)
        assertEquals("Imported", harness.profileDao.insertedProfiles.single().name)
    }

    @Test
    fun `import from file caps profiles inserted from huge input`() = runTest {
        val harness = Harness()
        val links = (1..2_001).joinToString("\n") { index ->
            "vless://12345678-1234-1234-1234-${index.toString().padStart(12, '0')}" +
                "@host-$index.example.com:443?type=tcp#P$index"
        }
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onImportFromFile(links, "huge.txt")
        advanceUntilIdle()

        assertEquals("huge", harness.insertedGroups.single().name)
        assertEquals(2_000, harness.profileDao.insertedProfiles.size)
    }

    @Test
    fun `onPing ignores empty groups and clears previous pinging state`() = runTest {
        val harness = Harness(initialGroups = listOf(group(id = 1L, userOrder = 0)))
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onPing(groupId = 1L)
        advanceUntilIdle()

        assertTrue(harness.probeCalls.isEmpty())
        assertTrue(harness.viewModel.state.value.isPinging.isEmpty())
    }

    @Test
    fun `onGroupExpand toggles expanded group without duplicate refresh`() = runTest {
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0)),
            initialProfiles = listOf(profile(id = 71L, groupId = 1L, name = "Existing", userOrder = 0)),
        )
        coEvery { harness.rawUpdater.refresh(any()) } returns Result.success(1)
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onGroupExpand(1L)
        advanceUntilIdle()

        assertEquals(1L, harness.viewModel.state.value.expandedGroupId)
        assertEquals(listOf(71L), harness.viewModel.state.value.groupProfiles.getValue(1L).map { it.id })

        harness.viewModel.onGroupExpand(1L)
        advanceUntilIdle()

        assertNull(harness.viewModel.state.value.expandedGroupId)
        coVerify(exactly = 0) { harness.rawUpdater.refresh(any()) }
    }

    @Test
    fun `state and expanded group cap visible profile lists`() = runTest {
        val profiles = (1L..600L).map { id ->
            profile(id = id, groupId = 1L, name = "P$id", userOrder = id.toInt())
        }
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0)),
            initialProfiles = profiles,
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onGroupExpand(1L)
        advanceUntilIdle()

        assertEquals(500, harness.viewModel.state.value.allProfiles.size)
        assertEquals(500, harness.viewModel.state.value.groupProfiles.getValue(1L).size)
    }

    @Test
    fun `onGroupExpand refreshes empty group and records updater error`() = runTest {
        val harness = Harness(initialGroups = listOf(group(id = 1L, userOrder = 0)))
        coEvery { harness.rawUpdater.refresh(any()) } returns Result.failure(IllegalStateException("network down"))
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onGroupExpand(1L)
        advanceUntilIdle()

        assertEquals(1L, harness.viewModel.state.value.expandedGroupId)
        assertEquals("network down", harness.viewModel.state.value.groupRefreshErrors[1L])
        assertTrue(harness.viewModel.state.value.isRefreshing.isEmpty())
        coVerify(exactly = 1) { harness.rawUpdater.refresh(match { it.id == 1L }) }
    }

    @Test
    fun `onRefresh without group refreshes all groups`() = runTest {
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0), group(id = 2L, userOrder = 1)),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onRefresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { harness.rawUpdater.refresh(match { it.id == 1L }) }
        coVerify(exactly = 1) { harness.rawUpdater.refresh(match { it.id == 2L }) }
        assertTrue(harness.viewModel.state.value.isRefreshing.isEmpty())
    }

    @Test
    fun `onRefresh updates subscriptions without implicit profile probing`() = runTest {
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0)),
            initialProfiles = listOf(profile(id = 101L, groupId = 1L, name = "Probe", userOrder = 0)),
        )
        coEvery { harness.rawUpdater.refresh(any()) } returns Result.success(1)
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onRefresh(1L)
        advanceUntilIdle()

        coVerify(exactly = 1) { harness.rawUpdater.refresh(match { it.id == 1L }) }
        assertTrue(harness.probeCalls.isEmpty())
    }

    @Test
    fun `onRefresh stores thrown updater error and still refreshes visible group profiles`() = runTest {
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0)),
            initialProfiles = listOf(profile(id = 91L, groupId = 1L, name = "Visible", userOrder = 0)),
        )
        coEvery { harness.rawUpdater.refresh(any()) } throws IllegalArgumentException("bad payload")
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onGroupExpand(1L)
        advanceUntilIdle()
        harness.viewModel.onRefresh(1L)
        advanceUntilIdle()

        assertEquals("bad payload", harness.viewModel.state.value.groupRefreshErrors[1L])
        assertEquals(listOf(91L), harness.viewModel.state.value.groupProfiles.getValue(1L).map { it.id })
        assertTrue(harness.viewModel.state.value.isRefreshing.isEmpty())
    }

    @Test
    fun `manual import maps all supported protocols to protocol types`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onImportFromFile(
            """
                vless://12345678-1234-1234-1234-123456789abc@vless.example.com:443?type=tcp#VLESS
                $VMESS_LINK
                trojan://secret@trojan.example.com:443#Trojan
                ss://YWVzLTI1Ni1nY206c2VjcmV0@ss.example.com:8388#SS
            """.trimIndent(),
            "protocols.txt",
        )
        advanceUntilIdle()

        assertEquals("protocols", harness.insertedGroups.single().name)
        assertEquals(listOf(0, 1, 2, 3), harness.profileDao.insertedProfiles.map { it.protocolType })
    }

    @Test
    fun `onAutoSelectBest skips probe when no profiles exist`() = runTest {
        val harness = Harness(initialGroups = listOf(group(id = 1L, userOrder = 0)))
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onAutoSelectBest()
        advanceUntilIdle()

        assertTrue(harness.probeCalls.isEmpty())
        assertFalse(harness.viewModel.state.value.isAutoSelecting)
    }

    @Test
    fun `onAutoSelectBest probes all groups and refreshes expanded profiles`() = runTest {
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0), group(id = 2L, userOrder = 1)),
            initialProfiles = listOf(
                profile(id = 101L, groupId = 1L, name = "One", userOrder = 0),
                profile(id = 201L, groupId = 2L, name = "Two", userOrder = 0),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()
        harness.viewModel.onGroupExpand(1L)
        advanceUntilIdle()

        harness.viewModel.onAutoSelectBest()
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("One.example.com", "Two.example.com"), harness.probeCalls.sorted())
        assertEquals(listOf(101L), harness.viewModel.state.value.groupProfiles.getValue(1L).map { it.id })
        assertFalse(harness.viewModel.state.value.isAutoSelecting)
    }

    @Test
    fun `onAutoSelectBest caps probe candidates`() = runTest {
        val profiles = (1L..70L).map { id ->
            profile(id = id, groupId = 1L, name = "P$id", userOrder = id.toInt())
        }
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0)),
            initialProfiles = profiles,
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onAutoSelectBest()
        advanceUntilIdle()

        assertEquals(50, harness.probeCalls.size)
    }

    @Test
    fun `add group field updates are partial and preserve previous values`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onAddGroupFieldChanged(name = "First")
        harness.viewModel.onAddGroupFieldChanged(url = "https://example.com/sub")
        advanceUntilIdle()

        assertEquals("First", harness.viewModel.state.value.addGroupName)
        assertEquals("https://example.com/sub", harness.viewModel.state.value.addGroupUrl)
    }

    @Test
    fun `manual links group name update preserves parse error and input update clears it`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onManualLinksFieldChanged(input = "broken")
        harness.viewModel.onConfirmManualLinks()
        harness.viewModel.onManualLinksFieldChanged(groupName = "Manual group")
        advanceUntilIdle()

        assertEquals("parse", harness.viewModel.state.value.manualLinksError)
        assertEquals("Manual group", harness.viewModel.state.value.manualLinksGroupName)

        harness.viewModel.onManualLinksFieldChanged(input = "still broken")
        advanceUntilIdle()

        assertNull(harness.viewModel.state.value.manualLinksError)
        assertEquals("Manual group", harness.viewModel.state.value.manualLinksGroupName)
    }

    @Test
    fun `add menu toggles independently from dialogs`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onShowAddMenu(true)
        advanceUntilIdle()
        assertTrue(harness.viewModel.state.value.showAddMenu)

        harness.viewModel.onShowAddManualLinksDialog(true)
        advanceUntilIdle()
        assertFalse(harness.viewModel.state.value.showAddMenu)
        assertTrue(harness.viewModel.state.value.showAddManualLinksDialog)

        harness.viewModel.onShowAddMenu(true)
        harness.viewModel.onShowAddMenu(false)
        advanceUntilIdle()
        assertFalse(harness.viewModel.state.value.showAddMenu)
    }

    @Test
    fun `expanding or refreshing missing group is no-op`() = runTest {
        val harness = Harness()
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onGroupExpand(99L)
        harness.viewModel.onRefresh(99L)
        advanceUntilIdle()

        assertEquals(99L, harness.viewModel.state.value.expandedGroupId)
        assertTrue(harness.viewModel.state.value.groupProfiles.getValue(99L).isEmpty())
        assertTrue(harness.viewModel.state.value.groupRefreshErrors.isEmpty())
        coVerify(exactly = 0) { harness.rawUpdater.refresh(any()) }
    }

    @Test
    fun `onPing without group probes every non empty group`() = runTest {
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0), group(id = 2L, userOrder = 1)),
            initialProfiles = listOf(
                profile(id = 111L, groupId = 1L, name = "One", userOrder = 0),
                profile(id = 222L, groupId = 2L, name = "Two", userOrder = 0),
            ),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()

        harness.viewModel.onPing()
        advanceUntilIdle()

        assertEquals(listOf("One.example.com", "Two.example.com"), harness.probeCalls.sorted())
        assertTrue(harness.viewModel.state.value.isPinging.isEmpty())
        assertTrue(harness.viewModel.state.value.testingProfileIds.isEmpty())
    }

    @Test
    fun `onPing probes current expanded snapshot and refreshes group profiles`() = runTest {
        val harness = Harness(
            initialGroups = listOf(group(id = 1L, userOrder = 0)),
            initialProfiles = listOf(profile(id = 121L, groupId = 1L, name = "Before", userOrder = 0)),
        )
        harness.startStateCollection(backgroundScope)
        advanceUntilIdle()
        harness.viewModel.onGroupExpand(1L)
        advanceUntilIdle()

        harness.profilesFlow.value = listOf(profile(id = 121L, groupId = 1L, name = "After", userOrder = 0))
        advanceUntilIdle()
        harness.viewModel.onPing(1L)
        advanceUntilIdle()

        assertEquals(listOf("After.example.com"), harness.probeCalls.toList())
        assertEquals("After", harness.viewModel.state.value.groupProfiles.getValue(1L).single().name)
        assertTrue(harness.viewModel.state.value.testingProfileIds.isEmpty())
    }

    private fun group(id: Long, userOrder: Int): SubscriptionGroup =
        SubscriptionGroup(
            id = id,
            name = "Group $id",
            userOrder = userOrder,
        )

    private fun profile(
        id: Long,
        groupId: Long,
        name: String,
        userOrder: Int,
        latencyMs: Int = -1,
    ): ProxyProfile =
        ProxyProfile(
            id = id,
            groupId = groupId,
            name = name,
            beanBlob = KryoSerializer.serialize(
                VLESSBean().apply {
                    serverAddress = "$name.example.com"
                    serverPort = 443
                    type = "tcp"
                },
            ),
            protocolType = 0,
            userOrder = userOrder,
            latencyMs = latencyMs,
        )

    private fun chainStep(profileId: Long, userOrder: Int): ProxyChainStep =
        ProxyChainStep(
            profileId = profileId,
            userOrder = userOrder,
        )

    private inner class Harness(
        initialPreferences: Preferences = mutablePreferencesOf(),
        initialGroups: List<SubscriptionGroup> = emptyList(),
        initialProfiles: List<ProxyProfile> = emptyList(),
        initialChain: List<ProxyChainStep> = emptyList(),
    ) {
        val prefsFlow = MutableStateFlow(initialPreferences)
        val groupsFlow = MutableStateFlow(initialGroups)
        val profilesFlow = MutableStateFlow(initialProfiles)
        val chainFlow = MutableStateFlow(initialChain)
        val insertedGroups = mutableListOf<SubscriptionGroup>()
        val probeCalls = ConcurrentLinkedQueue<String>()
        val viewModel: SingboxEngineSettingsViewModel
        val appContext: Context
        val groupDao: SubscriptionGroupDao
        val profileDao: RecordingProxyProfileDao
        val chainDao: RecordingProxyChainDao
        val groupSeeder: GroupSeeder
        val rawUpdater: RawUpdater

        private var nextGroupId = 1L

        init {
            appContext = mockk(relaxed = true)
            groupSeeder = mockk(relaxed = true)
            coEvery { groupSeeder.seedPresets(any()) } returns Unit
            groupDao = object : SubscriptionGroupDao {
                override fun getAllFlow(): Flow<List<SubscriptionGroup>> = groupsFlow

                override suspend fun getAll(): List<SubscriptionGroup> = groupsFlow.value

                override suspend fun getById(id: Long): SubscriptionGroup? = groupsFlow.value.find { it.id == id }

                override suspend fun getByUrl(url: String): SubscriptionGroup? = null

                override suspend fun getBuiltins(): List<SubscriptionGroup> = emptyList()

                override suspend fun getProfileIdsByGroupId(groupId: Long): List<Long> = emptyList()

                override suspend fun count(): Int = groupsFlow.value.size

                override suspend fun insert(group: SubscriptionGroup): Long {
                    insertedGroups += group
                    val id = if (group.id == 0L) nextGroupId++ else group.id
                    groupsFlow.value = groupsFlow.value + group.copy(id = id)
                    return id
                }

                override suspend fun update(group: SubscriptionGroup) = Unit

                override suspend fun delete(group: SubscriptionGroup) {
                    groupsFlow.value = groupsFlow.value.filterNot { it.id == group.id }
                }
            }
            profileDao = RecordingProxyProfileDao(profilesFlow)
            chainDao = RecordingProxyChainDao(chainFlow)
            rawUpdater = mockk(relaxed = true)
            coEvery { rawUpdater.refresh(any()) } returns Result.success(0)
            val probeService = SingboxProbeService(
                profileDao = profileDao,
                dataStore = dataStore(),
                profileProbe = RecordingProfileProbe(probeCalls),
                probeDispatcher = Dispatchers.Main,
            )
            viewModel = SingboxEngineSettingsViewModel(
                appContext = appContext,
                dataStore = dataStore(),
                groupDao = groupDao,
                profileDao = profileDao,
                proxyChainDao = chainDao,
                rawUpdater = rawUpdater,
                groupSeeder = groupSeeder,
                probeService = probeService,
            )
        }

        fun startStateCollection(scope: CoroutineScope) {
            stateCollectionJobs += scope.launch(Dispatchers.Main) {
                viewModel.state.collect { }
            }
        }

        private fun dataStore(): DataStore<Preferences> =
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = prefsFlow

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                    val updated = transform(prefsFlow.value)
                    prefsFlow.value = updated
                    return updated
                }
            }
    }

    private inner class RecordingProxyProfileDao(
        private val flow: MutableStateFlow<List<ProxyProfile>>,
    ) : ProxyProfileDao {
        val insertedProfiles = mutableListOf<ProxyProfile>()

        override fun getAllFlow(): Flow<List<ProxyProfile>> = flow

        override fun getAllLimitedFlow(limit: Int): Flow<List<ProxyProfile>> =
            flow.map { it.take(limit) }

        override fun getAutoCandidatesFlow(limit: Int): Flow<List<ProxyProfile>> =
            flow.map { it.sortedByAutoPriority().take(limit) }

        override fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>> =
            MutableStateFlow(flow.value.filter { it.groupId == groupId })

        override suspend fun getByGroupId(groupId: Long): List<ProxyProfile> =
            flow.value.filter { it.groupId == groupId }

        override suspend fun getByGroupIdLimited(groupId: Long, limit: Int): List<ProxyProfile> =
            flow.value.filter { it.groupId == groupId }.take(limit)

        override suspend fun getAutoCandidatesByGroupId(groupId: Long, limit: Int): List<ProxyProfile> =
            flow.value.filter { it.groupId == groupId }.sortedByAutoPriority().take(limit)

        override suspend fun getById(id: Long): ProxyProfile? = flow.value.find { it.id == id }

        override suspend fun insert(profile: ProxyProfile): Long {
            insertedProfiles += profile
            val id = if (profile.id == 0L) (flow.value.maxOfOrNull { it.id } ?: 0L) + 1 else profile.id
            flow.value = flow.value + profile.copy(id = id)
            return id
        }

        override suspend fun insertAll(profiles: List<ProxyProfile>) {
            insertedProfiles += profiles
            flow.value = flow.value + profiles.mapIndexed { index, profile ->
                profile.copy(
                    id = if (profile.id ==
                        0L
                    ) {
                        (flow.value.maxOfOrNull { it.id } ?: 0L) + index + 1
                    } else {
                        profile.id
                    }
                )
            }
        }

        override suspend fun insertAllIgnoringConflicts(profiles: List<ProxyProfile>): List<Long> =
            profiles.map { profile ->
                if (profile.id != 0L && flow.value.any { it.id == profile.id }) {
                    -1L
                } else {
                    insert(profile)
                }
            }

        override suspend fun deleteByGroupId(groupId: Long) {
            flow.value = flow.value.filterNot { it.groupId == groupId }
        }

        override suspend fun getIdsByGroupId(groupId: Long): List<Long> =
            flow.value.filter { it.groupId == groupId }.map { it.id }

        override suspend fun deleteByIds(ids: List<Long>) {
            val idSet = ids.toSet()
            flow.value = flow.value.filterNot { it.id in idSet }
        }

        override suspend fun updateProbeResult(id: Long, latency: Int, probeError: String?, lastProbeAt: Long) =
            Unit

        override suspend fun countByGroupId(groupId: Long): Int = flow.value.count { it.groupId == groupId }

        override suspend fun update(profile: ProxyProfile) = Unit

        override suspend fun delete(profile: ProxyProfile) {
            flow.value = flow.value.filterNot { it.id == profile.id }
        }

        private fun List<ProxyProfile>.sortedByAutoPriority(): List<ProxyProfile> =
            sortedWith(
                compareBy<ProxyProfile> {
                    when {
                        it.latencyMs >= 0 -> 0
                        it.latencyMs == -1 -> 1
                        else -> 2
                    }
                }
                    .thenBy { if (it.latencyMs >= 0) it.latencyMs else it.userOrder }
                    .thenBy { it.groupId }
                    .thenBy { it.userOrder }
                    .thenBy { it.id },
            )
    }

    private inner class RecordingProxyChainDao(
        private val flow: MutableStateFlow<List<ProxyChainStep>>,
    ) : ProxyChainDao {
        val replaceCalls = mutableListOf<List<Long>>()

        override fun getAllFlow(): Flow<List<ProxyChainStep>> = flow

        override suspend fun getAll(): List<ProxyChainStep> = flow.value

        override suspend fun clear() {
            flow.value = emptyList()
        }

        override suspend fun insertAll(steps: List<ProxyChainStep>) {
            flow.value = steps
        }

        suspend fun lastReplace(): List<Long> = replaceCalls.last()

        override suspend fun replace(profileIds: List<Long>) {
            replaceCalls += profileIds
            flow.value = profileIds.mapIndexed { index, profileId ->
                ProxyChainStep(profileId = profileId, userOrder = index)
            }
        }
    }

    private class RecordingProfileProbe(
        private val calls: ConcurrentLinkedQueue<String>,
    ) : SingboxProfileProbe {
        override suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int {
            calls += bean.serverAddress
            return 1
        }
    }

    private companion object {
        const val VMESS_LINK =
            "vmess://eyJhZGQiOiJ2bWVzcy5leGFtcGxlLmNvbSIsInBvcnQiOjQ0MywiaWQiOiIxMjM0NTY3OC0" +
                "xMjM0LTEyMzQtMTIzNC0xMjM0NTY3ODlhYmMiLCJhaWQiOjAsIm5ldCI6InRjcCIsInR5cGUiOiJu" +
                "b25lIiwicHMiOiJWTWVzcyJ9"
    }
}
