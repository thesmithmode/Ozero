package ru.ozero.singboxsubscription

import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.SubscriptionGroup

class GroupSeeder(private val dao: SubscriptionGroupDao) {

    data class PresetGroup(val name: String, val url: String)

    suspend fun seedPresets(presets: List<PresetGroup>) {
        presets.forEachIndexed { index, preset ->
            val existing = dao.getByUrl(preset.url)
            if (existing == null) {
                dao.insert(
                    SubscriptionGroup(
                        name = preset.name,
                        subscriptionUrl = preset.url,
                        isBuiltin = true,
                        autoUpdate = false,
                        userOrder = index,
                    ),
                )
            } else if (existing.isBuiltin && existing.autoUpdate) {
                dao.update(existing.copy(autoUpdate = false))
            }
        }
    }
}
