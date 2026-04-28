package ru.ozero.app.subscription

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.coresubscriptions.harvester.LiveProber
import ru.ozero.coresubscriptions.SubscriptionFilter
import ru.ozero.coresubscriptions.harvester.PublicProxyHarvester
import java.util.concurrent.TimeUnit
import javax.inject.Named

@HiltWorker
class HarvestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val registry: ProxySourceRegistry,
    private val dao: ServerDao,
    @Named("harvester") private val httpClient: OkHttpClient,
    private val filter: SubscriptionFilter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sources = registry.load()
        if (sources.isEmpty()) {
            Log.w(TAG, "no sources in registry → noop")
            return Result.success()
        }
        val harvester = PublicProxyHarvester(httpClient, dao, filter = filter)
        val r = harvester.harvest(sources)
        Log.i(TAG, "doWork harvest: parsed=${r.totalParsed} failed=${r.failedSources}")

        val all = dao.getLiveServers()
        val prober = LiveProber(dao)
        val ps = prober.probeAll(all)
        Log.i(TAG, "doWork probe: live=${ps.live} dead=${ps.dead} of ${ps.total}")

        return if (r.totalParsed > 0) Result.success() else Result.retry()
    }

    companion object {
        const val NAME = "ozero-harvest"
        private const val TAG = "HarvestWorker"
        private const val INTERVAL_HOURS = 6L

        fun enqueueUnique(context: Context) {
            val req = PeriodicWorkRequestBuilder<HarvestWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5L, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}
