package ru.ozero.app.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.singboxsubscription.RawUpdater

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SingboxSubscriptionEntryPoint {
    fun rawUpdater(): RawUpdater
}
