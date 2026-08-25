package ru.ozero.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.SubscriptionGroup
import ru.ozero.singboxsubscription.RawUpdater
import ru.ozero.singboxsubscription.isTransientSubscriptionRefreshFailure
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
            group.shouldRunSingboxSubscriptionUpdate(now)
        }
        if (groups.isEmpty()) return Result.success()
        val results = groups.map { group ->
            rawUpdater.refresh(group)
        }
        return if (
            results.any { result ->
                result.exceptionOrNull()?.let(::isTransientSubscriptionRefreshFailure) == true
            }
        ) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "singbox_subscription_update"
        private const val IMMEDIATE_WORK_NAME = "singbox_subscription_update_immediate"
        internal const val MIN_UPDATE_DELAY_MINUTES = 15L
        internal const val MAX_UPDATE_DELAY_MINUTES = 7L * 24 * 60
        internal const val FAILURE_RETRY_DELAY_MINUTES = 15L

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val immediateRequest = OneTimeWorkRequestBuilder<SubscriptionUpdateWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    FAILURE_RETRY_DELAY_MINUTES,
                    TimeUnit.MINUTES,
                )
                .build()
            val periodicRequest = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
                MIN_UPDATE_DELAY_MINUTES,
                TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    FAILURE_RETRY_DELAY_MINUTES,
                    TimeUnit.MINUTES,
                )
                .build()
            workManager.enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.KEEP, immediateRequest)
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest,
            )
        }
    }
}

internal fun SubscriptionGroup.shouldRunSingboxSubscriptionUpdate(now: Long): Boolean =
    autoUpdate &&
        subscriptionUrl.isNotBlank() &&
        hasElapsed(now, lastUpdated, singboxSubscriptionUpdateIntervalMs()) &&
        hasElapsed(
            now,
            lastAttemptAt,
            TimeUnit.MINUTES.toMillis(SubscriptionUpdateWorker.FAILURE_RETRY_DELAY_MINUTES),
        )

internal fun SubscriptionGroup.singboxSubscriptionUpdateIntervalMs(): Long =
    TimeUnit.MINUTES.toMillis(
        autoUpdateDelay.toLong().coerceIn(
            SubscriptionUpdateWorker.MIN_UPDATE_DELAY_MINUTES,
            SubscriptionUpdateWorker.MAX_UPDATE_DELAY_MINUTES,
        ),
    )

private fun hasElapsed(now: Long, timestamp: Long, delayMs: Long): Boolean =
    timestamp <= 0L || now < timestamp || now - timestamp >= delayMs
