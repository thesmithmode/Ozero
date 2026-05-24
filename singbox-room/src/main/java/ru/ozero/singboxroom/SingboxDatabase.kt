package ru.ozero.singboxroom

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup

@Database(
    entities = [SubscriptionGroup::class, ProxyProfile::class],
    version = 1,
    exportSchema = true,
)
abstract class SingboxDatabase : RoomDatabase() {
    abstract fun subscriptionGroupDao(): SubscriptionGroupDao
    abstract fun proxyProfileDao(): ProxyProfileDao
}
