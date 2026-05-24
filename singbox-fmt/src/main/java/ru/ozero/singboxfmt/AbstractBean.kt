package ru.ozero.singboxfmt

abstract class AbstractBean {
    var serverAddress: String = "127.0.0.1"
    var serverPort: Int = 1080
    var name: String = ""

    open fun initializeDefaultValues() {}

    fun displayAddress(): String = "$serverAddress:$serverPort"
    fun displayName(): String = if (name.isNotEmpty()) name else displayAddress()
}
