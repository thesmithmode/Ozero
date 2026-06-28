package ru.ozero.singboxfmt

class TrojanBean : StandardV2RayBean() {
    var password: String = ""

    override fun initializeDefaultValues() {
        security = if (security == "reality") "reality" else "tls"
        allowInsecure = false
        super.initializeDefaultValues()
    }
}
