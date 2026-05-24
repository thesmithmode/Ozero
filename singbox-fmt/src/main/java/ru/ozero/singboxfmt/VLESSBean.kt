package ru.ozero.singboxfmt

class VLESSBean : StandardV2RayBean() {
    var flow: String = ""

    override fun initializeDefaultValues() {
        if (encryption.isEmpty()) encryption = "none"
        super.initializeDefaultValues()
    }
}
