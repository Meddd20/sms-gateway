package com.httpsms

import android.app.Application
import com.httpsms.core.GatewayLogging

/**
 * The process-wide entry point, and the one place that guarantees logging exists.
 *
 * Before this class, [GatewayLogging.init] was only called from the login screen,
 * the main screen and the FCM service - so a process that Android cold-started for
 * an incoming SMS broadcast (the inbound gateway's most important path) ran with no
 * Timber tree planted and every log line silently dropped. Planting here covers
 * every entry point: activities, services, receivers and workers.
 */
class GatewayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GatewayLogging.init(this)
    }
}
