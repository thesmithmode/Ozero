package ru.ozero.singboxfmt

class ShadowsocksBean : AbstractBean() {
    var method: String = "aes-128-gcm"
    var password: String = ""
    var plugin: String = ""
    var pluginOpts: String = ""

    override fun initializeDefaultValues() {
        if (method.isEmpty()) method = "aes-128-gcm"
        super.initializeDefaultValues()
    }
}
