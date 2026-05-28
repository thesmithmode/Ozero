package ru.ozero.singboxsubscription

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.SubscriptionGroup

class FakeSubscriptionGroupDao : SubscriptionGroupDao {
    val groups = mutableListOf<SubscriptionGroup>()
    private var nextId = 1L

    override suspend fun insert(group: SubscriptionGroup): Long {
        val id = if (group.id == 0L) {
            val maxExistingId = groups.maxOfOrNull { it.id } ?: 0L
            maxOf(nextId, maxExistingId + 1).also { nextId = it + 1 }
        } else {
            group.id.also { nextId = maxOf(nextId, it + 1) }
        }
        groups.removeAll { it.id == id }
        groups.add(group.copy(id = id))
        return id
    }

    override suspend fun getById(id: Long): SubscriptionGroup? =
        groups.firstOrNull { it.id == id }

    override fun getAllFlow(): Flow<List<SubscriptionGroup>> = flowOf(groups.toList())

    override suspend fun getAll(): List<SubscriptionGroup> = groups.toList()

    override suspend fun getByUrl(url: String): SubscriptionGroup? =
        groups.firstOrNull { it.subscriptionUrl == url }

    override suspend fun getBuiltins(): List<SubscriptionGroup> =
        groups.filter { it.isBuiltin }

    override suspend fun update(group: SubscriptionGroup) {
        val index = groups.indexOfFirst { it.id == group.id }
        if (index >= 0) groups[index] = group
    }

    override suspend fun delete(group: SubscriptionGroup) {
        groups.removeAll { it.id == group.id }
    }

    override suspend fun count(): Int = groups.size
}
