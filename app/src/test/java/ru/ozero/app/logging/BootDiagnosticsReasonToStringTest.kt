package ru.ozero.app.logging

import android.app.ApplicationExitInfo
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import kotlin.test.assertEquals

class BootDiagnosticsReasonToStringTest {

    @Test
    fun `reasonToString covers all ApplicationExitInfo reasons and unknown fallback`() {
        val method = BootDiagnostics::class.java.getDeclaredMethod("reasonToString", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val expected = mapOf(
            "REASON_UNKNOWN" to "UNKNOWN",
            "REASON_EXIT_SELF" to "EXIT_SELF",
            "REASON_SIGNALED" to "SIGNALED",
            "REASON_LOW_MEMORY" to "LOW_MEMORY",
            "REASON_CRASH" to "CRASH_JVM",
            "REASON_CRASH_NATIVE" to "CRASH_NATIVE",
            "REASON_ANR" to "ANR",
            "REASON_INITIALIZATION_FAILURE" to "INIT_FAILURE",
            "REASON_PERMISSION_CHANGE" to "PERMISSION_CHANGE",
            "REASON_EXCESSIVE_RESOURCE_USAGE" to "EXCESSIVE_RESOURCE",
            "REASON_USER_REQUESTED" to "USER_REQUESTED",
            "REASON_USER_STOPPED" to "USER_STOPPED",
            "REASON_DEPENDENCY_DIED" to "DEPENDENCY_DIED",
            "REASON_OTHER" to "OTHER",
        )

        ApplicationExitInfo::class.java.fields
            .asSequence()
            .filter { field ->
                field.name.startsWith("REASON_") &&
                    field.type == Int::class.javaPrimitiveType &&
                    Modifier.isStatic(field.modifiers)
            }
            .forEach { field ->
                val value = field.getInt(null)
                val expectedValue = expected[field.name] ?: "code=$value"
                assertEquals(
                    expectedValue,
                    method.invoke(BootDiagnostics, value),
                    field.name,
                )
            }

        assertEquals("code=424242", method.invoke(BootDiagnostics, 424242))
    }
}
