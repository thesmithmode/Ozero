package binaries

enum class Destination {
    LIBS,
    JNI_LIBS,
}

data class Artifact(
    val name: String,
    val engine: String,
    val abi: String?,
    val destination: Destination,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val sourceRepo: String,
    val sourceCommit: String,
)

data class LockFile(
    val tag: String,
    val generatedAt: String,
    val artifacts: List<Artifact>,
) {
    fun findByName(name: String): Artifact? = artifacts.firstOrNull { it.name == name }
}
