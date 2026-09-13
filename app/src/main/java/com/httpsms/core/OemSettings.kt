package com.httpsms.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import timber.log.Timber

/**
 * Some manufacturers (Xiaomi, Oppo, Vivo, Huawei, ...) kill foreground services
 * even when battery optimization is disabled, unless the app is whitelisted in
 * their own "autostart" / "protected apps" screen. There is no public API to
 * read or change that setting, so we can only deep link the user to the right
 * screen and let them flip it manually.
 */
object OemSettings {
    private val autoStartScreens = listOf(
        // Xiaomi / Redmi / POCO
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        // Huawei / Honor
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        // Oppo / Realme
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        // Vivo / iQOO
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        // Asus
        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")
    )

    fun openAutoStartSettings(context: Context): Boolean {
        for (component in autoStartScreens) {
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                Timber.i("opened autostart settings [${component.flattenToShortString()}]")
                return true
            } catch (exception: Exception) {
                Timber.d("autostart screen [${component.flattenToShortString()}] is not available on this device")
            }
        }

        return openAppDetails(context)
    }

    fun openAppDetails(context: Context): Boolean {
        return try {
            val intent = Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (exception: Exception) {
            Timber.e(exception, "cannot open app details settings")
            false
        }
    }
}
