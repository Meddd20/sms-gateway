package com.httpsms.core

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import timber.log.Timber

/** One SIM line as the platform reports it. */
data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val number: String,
    val carrierName: String = ""
)

/**
 * The one place that answers "which SIM lines does this device have".
 *
 * Reading the lines needs READ_PHONE_STATE, so every lookup is permission-guarded
 * and `SecurityException`-safe here rather than at each of the call sites that used
 * to repeat the subscription-manager lookup. This module reports what the *platform*
 * exposes; the number the user configured for a line is [Settings]' business, and the
 * two are deliberately not conflated.
 */
object SimLines {

    /**
     * The active subscriptions, or an empty list when they cannot be read.
     *
     * Never throws: READ_PHONE_STATE is revocable while the app runs, so the read is
     * both guarded and caught.
     */
    fun active(context: Context): List<SimInfo> {
        if (!PermissionGate.phoneStateGranted(context)) {
            Timber.w("cannot read the SIM lines without READ_PHONE_STATE")
            return emptyList()
        }

        // Checked just above, but READ_PHONE_STATE is revocable while the app runs -
        // including between that check and this read - so the catch is load-bearing
        // rather than defensive.
        return try {
            val manager: SubscriptionManager = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SubscriptionManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SubscriptionManager.from(context)
            }

            manager.activeSubscriptionInfoList.orEmpty().map { subscription ->
                SimInfo(
                    subscriptionId = subscription.subscriptionId,
                    slotIndex = subscription.simSlotIndex,
                    displayName = subscription.displayName?.toString() ?: "SIM ${subscription.simSlotIndex + 1}",
                    number = subscription.number.orEmpty(),
                    carrierName = subscription.carrierName?.toString().orEmpty()
                )
            }
        } catch (e: SecurityException) {
            Timber.w(e, "cannot read the SIM lines without READ_PHONE_STATE")
            emptyList()
        }
    }

    /**
     * True when at least one line is switched on for sending.
     *
     * The single definition of "the gateway has a line to work with", which the boot
     * receiver, the main screen and the status-report gate all ask.
     */
    fun anyActive(context: Context): Boolean {
        return Settings.getActiveStatus(context, Constants.SIM1) ||
            Settings.getActiveStatus(context, Constants.SIM2)
    }

    /** The other line's label, for the "switch the other one off" rule. */
    fun otherLabel(label: String): String {
        return if (label == Constants.SIM1) Constants.SIM2 else Constants.SIM1
    }

    /**
     * Which configured line an inbound broadcast's slot index refers to.
     *
     * Receiving cannot use the subscription order that [subscriptionFor] uses, because
     * a broadcast carries only its slot - so this is where that separate rule lives.
     */
    fun lineForSlot(context: Context, slotIndex: Int): String {
        return if (slotIndex > 0 && Settings.isDualSIM(context)) Constants.SIM2 else Constants.SIM1
    }

    /** The carrier of the line with [subscriptionId], or "" when it cannot be read. */
    fun carrierFor(context: Context, subscriptionId: Int): String {
        return active(context).firstOrNull { it.subscriptionId == subscriptionId }?.carrierName.orEmpty()
    }

    /**
     * The subscription to send on for [sim], or `INVALID_SUBSCRIPTION_ID` when the
     * platform cannot resolve one (the caller then falls back to the default manager).
     */
    fun subscriptionFor(context: Context, sim: String): Int {
        val subscriptions = active(context)

        return when {
            sim == Constants.SIM1 && subscriptions.isNotEmpty() -> subscriptions[0].subscriptionId
            sim == Constants.SIM2 && subscriptions.size > 1 -> subscriptions[1].subscriptionId
            else -> SubscriptionManager.getDefaultSmsSubscriptionId()
        }
    }
}
