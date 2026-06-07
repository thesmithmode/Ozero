package binaries

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Date

object LockFileParser {
    private val SHA256_REGEX = Regex("[0-9a-f]{64}")

    fun parse(path: Path): LockFile {
        val content = Files.readString(path)
        if (content.isBlank()) {
            throw LockFileException("Lock file is empty: $path")
        }
        val raw: Map<String, Any?> =
            try {
                val root = Yaml().load<Any?>(content)
                val map = root as? Map<*, *>
                    ?: throw LockFileException("Lock file root must be a YAML map: $path")
                map.entries.associate { (key, value) ->
                    key?.toString() to value
                }
            } catch (e: YAMLException) {
                throw LockFileException("Malformed YAML in $path: ${e.message}", e)
            }

        val tagValue = raw["tag"] ?: throw LockFileException("Missing required field 'tag' in $path")
        val tag = tagValue.toString()
        val generatedAt = when (val value = raw["generated_at"]) {
            null -> throw LockFileException("Missing required field 'generated_at' in $path")
            is String -> value
            is Date -> Instant.ofEpochMilli(value.time).toString()
            else -> value.toString()
        }

        val rawArtifacts = raw["artifacts"]
        val artifacts = when (rawArtifacts) {
            null -> emptyList()
            is List<*> -> rawArtifacts.mapIndexed { i, item ->
                val map = item as? Map<*, *>
                    ?: throw LockFileException("Artifact #$i must be a YAML map in $path")
                parseArtifact(
                    map.entries.associate { (key, value) -> key?.toString() to value },
                    i,
                    path,
                )
            }
            else -> emptyList()
        }

        val dupName = artifacts.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }?.key
        if (dupName != null) {
            throw LockFileException("Duplicate artifact name '$dupName' in $path")
        }

        return LockFile(tag = tag, generatedAt = generatedAt, artifacts = artifacts)
    }

    private fun parseArtifact(m: Map<String, Any?>, idx: Int, path: Path): Artifact {
        fun req(key: String): String =
            m[key]?.toString()
                ?: throw LockFileException("Artifact #$idx missing required field '$key' in $path")

        fun reqLong(key: String): Long =
            when (val v = m[key]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                    ?: throw LockFileException("Artifact #$idx field '$key' must be integer in $path")
                else -> throw LockFileException("Artifact #$idx missing required field '$key' in $path")
            }

        val name = req("name")
        val engine = req("engine")
        val destinationStr = req("destination")
        val destination =
            when (destinationStr) {
                "libs" -> Destination.LIBS
                "jniLibs" -> Destination.JNI_LIBS
                else -> throw LockFileException(
                    "Artifact '$name' has unknown destination '$destinationStr' (expected libs|jniLibs) in $path",
                )
            }
        val abi = m.stringOrNull("abi")
        if (destination == Destination.JNI_LIBS && abi.isNullOrBlank()) {
            throw LockFileException("Artifact '$name' has destination=jniLibs but no abi in $path")
        }
        val downloadUrl = req("download_url")
        val uri = runCatching { java.net.URI(downloadUrl) }
            .getOrElse { throw LockFileException("Artifact '$name' has invalid download_url in $path") }
        if (!uri.isAbsolute) {
            throw LockFileException("Artifact '$name' download_url must be an absolute URL in $path")
        }
        val sha256 = req("sha256")
        if (!SHA256_REGEX.matches(sha256)) {
            throw LockFileException(
                "Artifact '$name' sha256 must be 64 lowercase hex chars, got length ${sha256.length} in $path",
            )
        }
        val sizeBytes = reqLong("size_bytes")
        if (sizeBytes <= 0L) {
            throw LockFileException("Artifact '$name' size_bytes must be > 0 in $path")
        }
        val sourceRepo = req("source_repo")
        val sourceCommit = req("source_commit")

        val targetFilename = m.stringOrNull("target_filename")?.takeIf { it.isNotBlank() }

        return Artifact(
            name = name,
            engine = engine,
            abi = abi,
            destination = destination,
            downloadUrl = downloadUrl,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            sourceRepo = sourceRepo,
            sourceCommit = sourceCommit,
            targetFilename = targetFilename,
        )
    }

    private fun Map<String, Any?>.stringOrNull(key: String): String? {
        val value = this[key] ?: return null
        return value as? String ?: value.toString()
    }
}
