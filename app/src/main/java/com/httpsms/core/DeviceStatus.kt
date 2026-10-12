package com.httpsms.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.telephony.SubscriptionManager

/**
 * A snapshot of the device state the backend cares about, read from the platform
 * on demand.
 *
 * Kept out of the API layer so the readings (and what they mean) can be reasoned
 * about - or tested - without a network client, and so the heartbeat body is just
 * a mapping from this to JSON.
 */
data class DeviceStatus(
    val deviceId: String,
    val smsPermissionGranted: Boolean,
    val batteryOptimizationDisabled: Boolean,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val activeSubscriptionId: Int,
    val simCarrier: String,
    val networkType: String
) {
    companion object {
        /**
         * True when the app is exempt from battery optimization.
         *
         * The one reading of this platform fact, shared by the heartbeat body and the
         * home screen's warning, so the two can never disagree about the same device.
         */
        fun batteryOptimizationDisabled(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        fun read(context: Context): DeviceStatus {
            val smsPermissionGranted = PermissionGate.messagingGranted(context)
            val batteryOptimizationExempt = batteryOptimizationDisabled(context)

            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            val activeSubscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
            val simCarrier = SimLines.carrierFor(context, activeSubscriptionId)

            return DeviceStatus(
                deviceId = Settings.getDeviceId(context),
                smsPermissionGranted = smsPermissionGranted,
                batteryOptimizationDisabled = batteryOptimizationExempt,
                batteryLevel = batteryLevel,
                isCharging = batteryManager.isCharging,
                activeSubscriptionId = activeSubscriptionId,
                simCarrier = simCarrier,
                networkType = networkType(context)
            )
        }

        private fun networkType(context: Context): String {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return "NONE"
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "NONE"
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                else -> "NONE"
            }
        }
    }
}
