package com.oneclickalias.app.platform

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oneclickalias.app.OcaApplication
import com.oneclickalias.app.data.aliases.ClaimOutcome
import com.oneclickalias.app.data.session.SessionState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Delivers pending claims of copied aliases in the background as soon as the
 * phone is online (WorkManager, no Play Services). Retries with backoff while
 * a claim fails; does nothing once the session has ended.
 */
class ClaimWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as OcaApplication).container
        val session = container.sessionManager.state.first { it !is SessionState.Loading }
        if (session !is SessionState.SignedIn) return Result.success()
        val outcomes = container.claimDelivery.deliverAll()
        return if (outcomes.values.any { it is ClaimOutcome.Failed }) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "claim-ready-aliases"
        private const val BACKOFF_SECONDS = 30L

        /** Starts the worker once the phone is online; a worker already waiting is kept. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ClaimWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
