package ru.ozero.singboxsubscription

import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.SubscriptionGroup

class GroupSeeder(
    private val dao: SubscriptionGroupDao,
    private val onStaleBuiltinProfilesDeleted: suspend (List<Long>) -> Unit = {},
) {

    data class PresetGroup(val name: String, val url: String)

    suspend fun seedPresets(presets: List<PresetGroup>) {
        val activePresetUrls = presets.mapTo(mutableSetOf()) { it.url }
        dao.getBuiltins()
            .filterNot { group -> group.subscriptionUrl in activePresetUrls }
            .forEach { group ->
                val profileIds = dao.getProfileIdsByGroupId(group.id)
                dao.deleteBuiltinGroupWithProfiles(group)
                if (profileIds.isNotEmpty()) onStaleBuiltinProfilesDeleted(profileIds)
            }

        val seenUrls = mutableSetOf<String>()
        presets.forEachIndexed { index, preset ->
            if (!seenUrls.add(preset.url)) {
                return@forEachIndexed
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
