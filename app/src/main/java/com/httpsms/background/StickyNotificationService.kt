package com.httpsms.background

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.httpsms.MainActivity
import com.httpsms.core.SimLines
import timber.log.Timber
import com.sevanam.androidsmsgateway.R

class StickyNotificationService: Service() {
    companion object {
        /**
         * Brings the foreground service up, unless no line is switched on - in which
         * case there is nothing to keep alive. The one definition of that gate, so the
         * boot receiver and the main screen cannot disagree about when to start it.
         */
        fun start(context: Context) {
            if (!SimLines.anyActive(context)) {
                Timber.d("no active line, not starting the foreground service")
                return
            }

            Timber.d("starting foreground service")
            val notificationIntent = Intent(context, StickyNotificationService::class.java)
            val service = context.startForegroundService(notificationIntent)
            Timber.d("foreground service started [${service?.className}]")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("Some component want to bind with the service [${intent?.action}]")
        return null
    }

    override fun onCreate() {
        Timber.d("The service has been created")
        super.onCreate()
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand executed with startId: $startId")
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("The service has been destroyed")
        Toast.makeText(this, getString(R.string.listener_service_destroyed), Toast.LENGTH_SHORT).show()
    }


    private fun createNotification(): Notification {
        val notificationChannelId = "sticky_notification_channel"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            notificationChannelId,
            notificationChannelId,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let {
                notificationIntent -> PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val builder: Notification.Builder = Notification.Builder(
            this,
            notificationChannelId
        )

        return builder
            .setContentTitle(getString(R.string.listener_notification_title))
            .setContentText(getString(R.string.listener_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_stat_name)
            .build()
    }
}
