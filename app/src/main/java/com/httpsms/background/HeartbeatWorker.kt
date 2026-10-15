package com.httpsms.background

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import timber.log.Timber

/**
 * The periodic heartbeat. It keeps its retry: a run that could not reach the backend
 * reports failure so WorkManager tries again, which is why it calls the blocking
 * [HeartbeatReporter.send] and reads the outcome rather than the fire-and-forget form.
 */
class HeartbeatWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        Timber.d("executing heartbeat worker")

        return when (val outcome = HeartbeatReporter.send(applicationContext, "worker")) {
            is HeartbeatReporter.Outcome.Sent -> Result.success()
            is HeartbeatReporter.Outcome.NoActiveLine -> {
                Timber.w("no active line, nothing to report")
                Result.success()
            }
            is HeartbeatReporter.Outcome.Skipped -> {
                Timber.d("heartbeat sent within the last interval, nothing to report")
                Result.success()
            }
            is HeartbeatReporter.Outcome.NotLoggedIn -> {
                Timber.w("user is not logged in, stopping processing")
                Result.failure()
            }
            is HeartbeatReporter.Outcome.Failed -> {
                Timber.e("could not send heartbeat: [${outcome.message}]")
                Result.failure()
            }
        }
    }
}
