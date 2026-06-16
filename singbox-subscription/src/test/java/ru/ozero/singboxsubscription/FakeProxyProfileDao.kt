package ru.ozero.singboxsubscription

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile

class FakeProxyProfileDao : ProxyProfileDao {
    val profiles = mutableListOf<ProxyProfile>()
    private var nextId = 1L

    override suspend fun insert(profile: ProxyProfile): Long {
        val id = if (profile.id == 0L) nextId++ else profile.id
        profiles.removeAll { it.id == id }
        profiles.add(profile.copy(id = id))
        return id
    }

    override suspend fun insertAll(profiles: List<ProxyProfile>) {
        profiles.forEach { insert(it) }
    }

    override suspend fun insertAllIgnoringConflicts(profiles: List<ProxyProfile>): List<Long> =
        profiles.map { profile ->
            if (profile.id != 0L && this.profiles.any { it.id == profile.id }) {
                -1L
            } else {
                insert(profile)
            }
        }

    override suspend fun getById(id: Long): ProxyProfile? =
        profiles.firstOrNull { it.id == id }

    override fun getAllFlow(): Flow<List<ProxyProfile>> =
        flowOf(profiles.sortedWith(compareBy<ProxyProfile> { it.groupId }.thenBy { it.userOrder }.thenBy { it.id }))

    override fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>> =
        flowOf(profiles.filter { it.groupId == groupId })

    override suspend fun getByGroupId(groupId: Long): List<ProxyProfile> =
        profiles.filter { it.groupId == groupId }

    override suspend fun deleteByGroupId(groupId: Long) {
        profiles.removeAll { it.groupId == groupId }
    }

    override suspend fun getIdsByGroupId(groupId: Long): List<Long> =
        profiles.filter { it.groupId == groupId }.map { it.id }

    override suspend fun deleteByIds(ids: List<Long>) {
        val idSet = ids.toSet()
        profiles.removeAll { it.id in idSet }
    }

    override suspend fun updateLatency(id: Long, latency: Int) {
        val idx = profiles.indexOfFirst { it.id == id }
        if (idx >= 0) profiles[idx] = profiles[idx].copy(latencyMs = latency)
    }

    override suspend fun countByGroupId(groupId: Long): Int =
        profiles.count { it.groupId == groupId }

    override suspend fun update(profile: ProxyProfile) {
        val idx = profiles.indexOfFirst { it.id == profile.id }
        if (idx >= 0) profiles[idx] = profile
    }

    override suspend fun delete(profile: ProxyProfile) {
        profiles.removeAll { it.id == profile.id }
    }
}
