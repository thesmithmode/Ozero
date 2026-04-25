package binaries

open class OzeroBinariesExtension {
    private val declared: MutableList<String> = mutableListOf()

    fun artifact(name: String) {
        declared.add(name)
    }

    fun names(): List<String> = declared.toList()
}
