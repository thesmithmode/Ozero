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
                    domains = (0 until domainsArr.length()).map { domainsArr.getString(it) },
                    isActive = o.optBoolean("isActive", true),
                    isBuiltIn = o.optBoolean("isBuiltIn", false),
                )
            }
        }.getOrDefault(emptyList())
    }

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
}
