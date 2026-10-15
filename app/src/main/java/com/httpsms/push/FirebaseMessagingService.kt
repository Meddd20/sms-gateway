package com.httpsms.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.httpsms.background.DeviceRegistration
import com.httpsms.background.HeartbeatReporter
import com.httpsms.background.WorkQueue
import com.httpsms.core.Constants
import com.httpsms.core.GatewayLogging
import timber.log.Timber

/**
 * The backend's push channel into this device. It recognises the two payloads the
 * contract defines (`docs/API-CONTRACT.md` §8) and hands each to the module that owns
 * the work - sending lives in [com.httpsms.sms.SendMessageWorker], the heartbeat in
 * [HeartbeatReporter], token registration in [DeviceRegistration].
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {
    // [START receive_message]
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        initTimber()

        // First line to look for when a push seems missing: it proves FCM reached
        // the app and shows what it carried. Logged before any branching, so even
        // an unexpected payload leaves a trace instead of being dropped silently.
        Timber.i(
            "FCM received: data=${remoteMessage.data} priority=${remoteMessage.priority} " +
                "from=${remoteMessage.from} messageId=${remoteMessage.messageId} sentTime=${remoteMessage.sentTime}"
        )

        // Any push - a heartbeat request or a new message to send - means the
        // backend is waiting on this device, so refresh the heartbeat first.
        sendHeartbeat()

        if (remoteMessage.data.containsKey(Constants.KEY_HEARTBEAT_ID)) {
            Timber.w("received heartbeat message with ID [${remoteMessage.data[Constants.KEY_HEARTBEAT_ID]}] and priority [${remoteMessage.priority}] and original priority [${remoteMessage.originalPriority}]")
            return
        }

        val messageID = remoteMessage.data[Constants.KEY_MESSAGE_ID]
        if (messageID == null)  {
            Timber.e("cannot get message id from notification data with key [${Constants.KEY_MESSAGE_ID}]")
            return
        }

        scheduleJob(messageID)
    }
    // [END receive_message]

    // [START on_new_token]
    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        initTimber()
        Timber.d("Refreshed token: $token")

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        sendRegistrationToServer(token)
    }
    // [END on_new_token]

    private fun sendHeartbeat() {
        Timber.d("sending heartbeat from FCM notification")
        // Forced: a heartbeat ping is the backend explicitly asking for a fresh
        // status (contract §9.3), so the rate limit must not swallow it - and as an
        // explicit request it does not move the automatic 15-minute window either.
        HeartbeatReporter.sendAsync(applicationContext, "fcm trigger", force = true)
    }

    private fun scheduleJob(messageID: String) {
        WorkQueue.sendMessage(this, messageID)
    }

    private fun sendRegistrationToServer(token: String) {
        Timber.d("sendRegistrationTokenToServer($token)")
        DeviceRegistration.onTokenRefreshedAsync(this, token)
    }

    private fun initTimber() {
        GatewayLogging.init(applicationContext)
    }
}
