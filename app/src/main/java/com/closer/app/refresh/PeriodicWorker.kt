package com.closer.app.refresh

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Eco-mode heartbeat: every 15 min, refresh both sides and re-render the widget. */
class PeriodicWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Refresher.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "closer_periodic"

        fun ensure(context: Context) {
            val request = PeriodicWorkRequestBuilder<PeriodicWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
