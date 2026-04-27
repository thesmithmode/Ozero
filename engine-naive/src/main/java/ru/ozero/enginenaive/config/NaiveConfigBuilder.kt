package ru.ozero.enginenaive.config

import ru.ozero.commonjson.JsonWriter
import ru.ozero.coresubscriptions.uri.NaiveServer

class NaiveConfigBuilder {

    fun build(server: NaiveServer, socksPort: Int): String {
        require(socksPort in MIN_PORT..MAX_PORT) { "socksPort вне диапазона: $socksPort" }
        require(server.host.isNotBlank()) { "host пустой" }
        require(server.username.isNotBlank() && server.password.isNotBlank()) {
            "user/pass обязательны"
        }
        val root = linkedMapOf<String, Any?>(
            "listen" to "socks://127.0.0.1:$socksPort",
            "proxy" to server.proxyUrl,
            "log" to "",
        )
        return JsonWriter.write(root)
    }

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
    }
}
