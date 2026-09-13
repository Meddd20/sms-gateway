package com.httpsms.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
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
        private val SMS_PERMISSIONS = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        fun read(context: Context): DeviceStatus {
            val smsPermissionGranted = SMS_PERMISSIONS.all {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryOptimizationDisabled = powerManager.isIgnoringBatteryOptimizations(context.packageName)

            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            val activeSubscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
            val subscriptionManager: SubscriptionManager = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SubscriptionManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SubscriptionManager.from(context)
            }
            val simCarrier = subscriptionManager.activeSubscriptionInfoList
                ?.firstOrNull { it.subscriptionId == activeSubscriptionId }
                ?.carrierName?.toString().orEmpty()

            return DeviceStatus(
                deviceId = Settings.getDeviceId(context),
                smsPermissionGranted = smsPermissionGranted,
                batteryOptimizationDisabled = batteryOptimizationDisabled,
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
