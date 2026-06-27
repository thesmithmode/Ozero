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
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.enginesingbox.prioritizeSingboxAutoProfiles
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.SubscriptionGroup
import ru.ozero.singboxsubscription.RawUpdater
import java.util.concurrent.TimeUnit

@HiltWorker
class SubscriptionUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val rawUpdater: RawUpdater,
    private val groupDao: SubscriptionGroupDao,
    private val profileDao: ProxyProfileDao,
    private val probeService: SingboxProbeService,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val groups = groupDao.getAll().filter { group ->
            group.shouldRunSingboxSubscriptionUpdate(now)
        }
        if (groups.isEmpty()) return Result.success()
        val results = groups.map { group ->
            val result = rawUpdater.refresh(group)
            if (result.isSuccess) probeGroup(group.id)
            result
        }
        val allFailed = results.all { it.isFailure }
        return if (allFailed) Result.retry() else Result.success()
    }

    private suspend fun probeGroup(groupId: Long) {
        val profiles = prioritizeSingboxAutoProfiles(
            profileDao.getAutoCandidatesByGroupId(groupId, MAX_BACKGROUND_PROBE_WINDOW),
            MAX_BACKGROUND_PROBE_PROFILES,
        )
        if (profiles.isEmpty()) return
        probeService.probeAndAutoSelect(
            profiles = profiles,
            updateManualSelection = false,
        )
    }

    companion object {
        private const val WORK_NAME = "singbox_subscription_update"
        private const val INTERVAL_HOURS = 24L
        internal const val MIN_UPDATE_INTERVAL_MS = 23L * 60 * 60 * 1000
        private const val MAX_BACKGROUND_PROBE_PROFILES = 20
        private const val MAX_BACKGROUND_PROBE_WINDOW = 2_000

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

internal fun SubscriptionGroup.shouldRunSingboxSubscriptionUpdate(now: Long): Boolean =
    autoUpdate &&
        subscriptionUrl.isNotBlank() &&
        (now - lastUpdated) >= SubscriptionUpdateWorker.MIN_UPDATE_INTERVAL_MS
