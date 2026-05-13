package ru.ozero.app.ui.strategy

import java.util.UUID

class DomainListManager(
    private val store: DomainListStore,
    private val builtInLists: List<DomainList>,
) {

    fun load(): List<DomainList> {
        val stored = store.load()
        if (stored.isEmpty()) {
            store.save(builtInLists)
            return builtInLists
        }
        return syncBuiltIns(stored)
    }

    private fun syncBuiltIns(stored: List<DomainList>): List<DomainList> {
        val storedById = stored.associateBy { it.id }
        val result = mutableListOf<DomainList>()
        for (builtIn in builtInLists) {
            val existing = storedById[builtIn.id]
            if (existing != null) {
                result += existing.copy(domains = builtIn.domains, isBuiltIn = true)
            } else {
                result += builtIn
            }
        }
        stored.filter { !it.isBuiltIn }.forEach { result += it }
        return result
    }

    fun getActiveDomains(lists: List<DomainList>): List<String> =
        lists.filter { it.isActive }
            .flatMap { it.domains }
            .distinct()

    fun toggle(lists: List<DomainList>, id: String): List<DomainList> =
        lists.map { if (it.id == id) it.copy(isActive = !it.isActive) else it }

    fun addCustom(lists: List<DomainList>, name: String, domains: List<String>): List<DomainList> =
        lists + DomainList(id = UUID.randomUUID().toString(), name = name, domains = domains)

    fun delete(lists: List<DomainList>, id: String): List<DomainList> =
        lists.filter { it.id != id }

    fun resetToDefaults(lists: List<DomainList>): List<DomainList> =
        builtInLists + lists.filter { !it.isBuiltIn }

    fun save(lists: List<DomainList>) = store.save(lists)

    companion object {
        val BUILT_IN_CONFIGS: List<Triple<String, String, Boolean>> = listOf(
            Triple("general", "General", true),
            Triple("youtube_lite", "YouTube (основные)", true),
            Triple("telegram_lite", "Telegram (основные)", true),
            Triple("instagram_lite", "Instagram (основные)", true),
            Triple("googlevideo", "Google Video", false),
            Triple("youtube", "YouTube (все домены)", false),
            Triple("cloudflare", "Cloudflare", false),
            Triple("discord", "Discord", false),
            Triple("social", "Social Media", false),
            Triple("telegram", "Telegram (все домены)", false),
        )
    }
}
