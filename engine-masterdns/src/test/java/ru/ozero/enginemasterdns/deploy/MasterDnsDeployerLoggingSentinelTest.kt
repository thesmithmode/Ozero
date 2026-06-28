package ru.ozero.enginemasterdns.deploy

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MasterDnsDeployerLoggingSentinelTest {
    @Test
    fun `persistent deploy logs do not include server endpoints or remote command output`() {
        val source = Files.readString(
            Path.of(
                "engine-masterdns/src/main/java/ru/ozero/enginemasterdns/deploy/MasterDnsDeployerImpl.kt",
            ),
        )

        val forbiddenSnippets = listOf(
            "host=\${credentials.host}",
            "result=\${",
            ".takeShort(",
            ".take(80)",
            ".take(120)",
        )

        forbiddenSnippets.forEach { snippet ->
            assertFalse(source.contains(snippet), "MasterDNS deploy persistent logs must not include $snippet")
        }
    }
}
