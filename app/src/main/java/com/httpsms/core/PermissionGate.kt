package com.httpsms.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The one place that knows which runtime permissions the gateway needs, and the one
 * way to ask for them.
 *
 * Android requires the request contract to be registered before its owner reaches
 * STARTED, so [attach] has to run at construction or early in `onCreate`.
 * Registering it from `onStart` throws `IllegalStateException`; keeping that timing
 * here is the point, because callers then only ever call [Request.request] and
 * cannot get it wrong.
 */
object PermissionGate {

    /** True when the permissions needed to send and receive SMS are held. */
    fun messagingGranted(context: Context): Boolean {
        return MESSAGING.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * True when READ_PHONE_STATE is held, so the device's SIM lines may be read.
     *
     * The grant can be revoked at any time - including between this check and the
     * call it guards - so callers must still handle `SecurityException`.
     */
    fun phoneStateGranted(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Every permission the gateway asks for.
     *
     * READ_PHONE_STATE is included because reading the device's SIM lines - which
     * the home screen, the heartbeat body and the login screen all rely on - needs it.
     * POST_NOTIFICATIONS is only a runtime permission from API 33.
     */
    fun required(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ALL + Manifest.permission.POST_NOTIFICATIONS
        } else {
            ALL
        }
    }

    /**
     * Registers the request contract once against [owner] and returns the single call
     * the caller needs to keep. Must be invoked before [owner] reaches STARTED.
     */
    fun attach(owner: ComponentActivity, onResult: (Map<String, Boolean>) -> Unit): Request {
        val launcher = owner.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result -> onResult(result) }
        return Request(launcher)
    }

    /** The one call a caller makes: ask for everything the gateway needs. */
    class Request internal constructor(private val launcher: ActivityResultLauncher<Array<String>>) {
        fun request() {
            launcher.launch(required())
        }
    }

    private val MESSAGING = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    )

    private val ALL = MESSAGING + Manifest.permission.READ_PHONE_STATE
}
