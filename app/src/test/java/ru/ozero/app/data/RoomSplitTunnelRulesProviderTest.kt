package ru.ozero.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.entity.AppSplitRule
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomSplitTunnelRulesProviderTest {

    private class FakeDao(rules: List<AppSplitRule> = emptyList()) : AppSplitRuleDao {
        private val flow = MutableStateFlow(rules)

        override fun observeAll(): Flow<List<AppSplitRule>> = flow.asStateFlow()
        override suspend fun upsert(rule: AppSplitRule) = Unit
        override suspend fun delete(packageName: String) = Unit
    }

    private fun provider(rules: List<AppSplitRule>) = RoomSplitTunnelRulesProvider(FakeDao(rules))

    @Test
    fun `allowlistPackages возвращает только isExcluded=false`() = runTest {
        val p = provider(
            listOf(
                AppSplitRule("com.a", isExcluded = false),
                AppSplitRule("com.b", isExcluded = true),
                AppSplitRule("com.c", isExcluded = false),
            ),
        )
        assertEquals(setOf("com.a", "com.c"), p.allowlistPackages())
    }

    @Test
    fun `blocklistPackages возвращает только isExcluded=true`() = runTest {
        val p = provider(
            listOf(
                AppSplitRule("com.a", isExcluded = false),
                AppSplitRule("com.b", isExcluded = true),
                AppSplitRule("com.c", isExcluded = true),
            ),
        )
        assertEquals(setOf("com.b", "com.c"), p.blocklistPackages())
    }

    @Test
    fun `пустой dao - оба метода возвращают emptySet`() = runTest {
        val p = provider(emptyList())
        assertTrue(p.allowlistPackages().isEmpty())
        assertTrue(p.blocklistPackages().isEmpty())
    }

    @Test
    fun `все записи isExcluded=false - blocklist пуст`() = runTest {
        val p = provider(listOf(AppSplitRule("com.x", isExcluded = false)))
        assertTrue(p.blocklistPackages().isEmpty())
        assertEquals(setOf("com.x"), p.allowlistPackages())
    }

    @Test
    fun `все записи isExcluded=true - allowlist пуст`() = runTest {
        val p = provider(listOf(AppSplitRule("com.x", isExcluded = true)))
        assertTrue(p.allowlistPackages().isEmpty())
        assertEquals(setOf("com.x"), p.blocklistPackages())
    }

    @Test
    fun `allowlist и blocklist не пересекаются`() = runTest {
        val rules = listOf(
            AppSplitRule("com.a", isExcluded = false),
            AppSplitRule("com.b", isExcluded = true),
        )
        val p = provider(rules)
        val allowlist = p.allowlistPackages()
        val blocklist = p.blocklistPackages()
        assertTrue(allowlist.intersect(blocklist).isEmpty(), "allowlist и blocklist обязаны быть непересекающимися")
    }
}
