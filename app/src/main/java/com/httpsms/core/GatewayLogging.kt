package com.httpsms.core

import android.content.Context
import com.sevanam.androidsmsgateway.BuildConfig
import timber.log.Timber

/**
 * Single place that plants the Timber trees.
 *
 * It deliberately does NOT gate on `Timber.treeCount`: the Sentry SDK is bundled
 * in this app (`libsentry.so` is loaded at startup) and plants its own tree during
 * auto-init. That tree captures events into Sentry and never writes to Logcat, so
 * `treeCount` was already 1 before any of our code ran - the old "treeCount == 0"
 * guard never planted the Logcat tree and every Timber line was silently dropped.
 *
 * Calling it from several entry points (login, main, FCM service) is safe: the
 * flag below makes sure our trees are planted exactly once per process.
 */
object GatewayLogging {
    private var planted = false

    fun init(context: Context) {
        if (planted) {
            return
        }
        planted = true

        val applicationContext = context.applicationContext

        // Debug builds always log to Logcat. The debug-log toggle lives on the
        // Settings screen, which is currently disabled, so gating on it alone
        // would leave the app with no readable logs at all.
        if (BuildConfig.DEBUG || Settings.isDebugLogEnabled(applicationContext)) {
            Timber.plant(Timber.DebugTree())
        }

        // Remote sink stays opt-in: it ships logs off the device.
        if (Settings.isDebugLogEnabled(applicationContext)) {
            Timber.plant(LogzTree(applicationContext))
        }
    }
}
