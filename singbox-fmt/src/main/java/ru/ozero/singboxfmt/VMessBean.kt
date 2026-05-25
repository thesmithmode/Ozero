package ru.ozero.singboxfmt

class VMessBean : StandardV2RayBean() {
    var alterId: Int = 0

    override fun initializeDefaultValues() {
        if (encryption.isEmpty()) encryption = "auto"
        super.initializeDefaultValues()
    }
}
