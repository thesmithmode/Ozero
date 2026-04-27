package ru.ozero.app.di

import dagger.Provides
import dagger.multibindings.IntoMap
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineModuleTest {

        @Test
    fun `engine module covers every EngineId via @IntoMap`() {
        val moduleClass = EngineModule::class.java
        val coveredIds = moduleClass.declaredMethods
            .filter { it.isAnnotationPresent(Provides::class.java) }
            .filter { it.isAnnotationPresent(IntoMap::class.java) }
            .mapNotNull { it.getAnnotation(EngineKey::class.java) }
            .map { it.value }
            .toSet()

        assertEquals(
            EngineId.entries.toSet(),
            coveredIds,
            "EngineModule must @IntoMap-bind every EngineId",
        )
    }

    @Test
    fun `every @IntoMap method returns Engine type`() {
        val engineInterface = Engine::class.java
        val nonEngineMethods = EngineModule::class.java.declaredMethods
            .filter { it.isAnnotationPresent(IntoMap::class.java) }
            .filter { !engineInterface.isAssignableFrom(it.returnType) }
            .map { it.name }

        assertTrue(
            nonEngineMethods.isEmpty(),
            "Every @IntoMap method must return Engine, but found: $nonEngineMethods",
        )
    }
}
