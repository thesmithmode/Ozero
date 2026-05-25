package ru.ozero.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxsubscription.RawUpdater
import java.util.concurrent.TimeUnit

@HiltWorker
class SubscriptionUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val rawUpdater: RawUpdater,
    private val groupDao: SubscriptionGroupDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val groups = groupDao.getAll().filter { group ->
            group.autoUpdate && (now - group.lastUpdated) >= MIN_UPDATE_INTERVAL_MS
        }
        if (groups.isEmpty()) return Result.success()
        val results = groups.map { group -> rawUpdater.refresh(group) }
        val allFailed = results.all { it.isFailure }
        return if (allFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "singbox_subscription_update"
        private const val INTERVAL_HOURS = 24L
        private const val MIN_UPDATE_INTERVAL_MS = 23L * 60 * 60 * 1000

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
