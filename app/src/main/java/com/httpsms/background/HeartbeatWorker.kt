package com.httpsms.background

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.httpsms.core.Constants
import com.httpsms.core.DeviceStatus
import com.httpsms.core.Settings
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi
import timber.log.Timber

class HeartbeatWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        Timber.d("executing heartbeat worker")
        if (!Settings.isLoggedIn(applicationContext)) {
            Timber.w("user is not logged in, stopping processing")
            return Result.failure()
        }

        val phoneNumbers = mutableListOf<String>()
        if (Settings.getActiveStatus(applicationContext, Constants.SIM1)) {
            phoneNumbers.add(Settings.getSIM1PhoneNumber(applicationContext))
        }
        if (Settings.getActiveStatus(applicationContext, Constants.SIM2)) {
            phoneNumbers.add(Settings.getSIM2PhoneNumber(applicationContext))
        }

        if (phoneNumbers.isEmpty()) {
            Timber.w("both [SIM1] and [SIM2] are inactive stopping processing.")
            return Result.success()
        }

        return when (val result = SmsGatewayApi.from(applicationContext).storeHeartbeat(DeviceStatus.read(applicationContext), phoneNumbers)) {
            is ApiResult.Success -> {
                Settings.setHeartbeatTimestampAsync(applicationContext, System.currentTimeMillis())
                Timber.d("finished sending heartbeats to server")
                Result.success()
            }
            is ApiResult.Failure -> {
                Timber.e("Failed to send [${phoneNumbers.joinToString()}] heartbeats to server: [${result.message}]")
                Result.failure()
            }
        }
    }
}
