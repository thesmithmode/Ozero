package ru.ozero.app.ui.onboarding

import javax.inject.Inject
import javax.inject.Singleton

interface FirstRunBootstrap {
    suspend fun runIfFirstStart()
}

@Singleton
class NoOpFirstRunBootstrap @Inject constructor() : FirstRunBootstrap {
    override suspend fun runIfFirstStart() = Unit
}
