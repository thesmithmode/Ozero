package ru.ozero.app.ui.strategy

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface DomainListStore {
    fun load(): List<DomainList>
    fun save(lists: List<DomainList>)
}

class FileDomainListStore(
    filesDir: File,
    fileName: String = "domain_lists.json",
) : DomainListStore {

    private val file = File(filesDir, fileName)

    override fun load(): List<DomainList> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val domainsArr = o.getJSONArray("domains")
                DomainList(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    domains = (0 until domainsArr.length())
                        .map { domainsArr.getString(it) }
                        .filter(::isValidDomain),
                    isActive = o.optBoolean("isActive", true),
                    isBuiltIn = o.optBoolean("isBuiltIn", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun isValidDomain(d: String): Boolean =
        d.length in 1..253 && DOMAIN_REGEX.matches(d)

    override fun save(lists: List<DomainList>) {
        val array = JSONArray()
        lists.forEach { l ->
            val domains = JSONArray()
            l.domains.forEach { domains.put(it) }
            array.put(
                JSONObject()
                    .put("id", l.id)
                    .put("name", l.name)
                    .put("domains", domains)
                    .put("isActive", l.isActive)
                    .put("isBuiltIn", l.isBuiltIn),
            )
        }
        runCatching { file.writeText(array.toString()) }
    }

    private companion object {
        val DOMAIN_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$")
    }
}
