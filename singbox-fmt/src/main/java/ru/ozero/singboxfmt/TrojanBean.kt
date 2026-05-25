package ru.ozero.singboxfmt

class TrojanBean : StandardV2RayBean() {
    var password: String = ""

    override fun initializeDefaultValues() {
        if (security.isEmpty()) security = "tls"
        super.initializeDefaultValues()
    }
}
