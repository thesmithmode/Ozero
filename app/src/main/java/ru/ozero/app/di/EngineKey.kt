package ru.ozero.app.di

import dagger.MapKey
import ru.ozero.coreapi.EngineId

@MapKey
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class EngineKey(val value: EngineId)
