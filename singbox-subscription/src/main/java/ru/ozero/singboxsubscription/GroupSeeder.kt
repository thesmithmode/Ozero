package ru.ozero.singboxsubscription

import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.SubscriptionGroup

class GroupSeeder(private val dao: SubscriptionGroupDao) {

    data class PresetGroup(val name: String, val url: String)

    suspend fun seedPresets(presets: List<PresetGroup>) {
        val seenUrls = mutableSetOf<String>()
        presets.forEachIndexed { index, preset ->
            if (!seenUrls.add(preset.url)) {
                return@forEach
            }
            if (dao.getByUrl(preset.url) == null) {
                dao.insert(
                    SubscriptionGroup(
                        name = preset.name,
                        subscriptionUrl = preset.url,
                        isBuiltin = true,
                        userOrder = index,
                    ),
                )
            }
        }
    }
}
