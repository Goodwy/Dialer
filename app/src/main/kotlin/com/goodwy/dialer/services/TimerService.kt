package com.goodwy.dialer.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.goodwy.commons.extensions.*
import com.goodwy.dialer.R
import com.goodwy.dialer.helpers.INVALID_TIMER_ID
import com.goodwy.dialer.helpers.TIMER_RUNNING_NOTIF_ID
import com.goodwy.dialer.models.TimerEvent
import com.goodwy.dialer.models.TimerState
import com.goodwy.commons.helpers.isOreoPlus
import com.goodwy.dialer.extensions.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class TimerService : Service() {
    private val bus = EventBus.getDefault()
    private var isStopping = false

    override fun onCreate() {
        super.onCreate()
        bus.register(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        isStopping = false
        updateNotification()
        startForeground(TIMER_RUNNING_NOTIF_ID, notification(getString(R.string.app_launcher_name), getString(R.string.timers_notification_msg), INVALID_TIMER_ID))
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        timerHelper.getTimers { timers ->
            val runningTimers = timers.filter { it.state is TimerState.Running }
            if (runningTimers.isNotEmpty()) {
                val firstTimer = runningTimers.first()
                val formattedDuration = (firstTimer.state as TimerState.Running).tick.getFormattedDuration()
                val contextText = when {
                    firstTimer.label.isNotEmpty() -> getString(R.string.call_back_person_g, firstTimer.title)
                    else -> resources.getQuantityString(R.plurals.timer_notification_msg, runningTimers.size, runningTimers.size)
                }

                Handler(Looper.getMainLooper()).post {
                    try {
                        startForeground(TIMER_RUNNING_NOTIF_ID, notification(formattedDuration, contextText, firstTimer.id!!, firstTimer.label))
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            } else {
                stopService()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerStopService) {
        stopService()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Refresh) {
        if (!isStopping) {
            updateNotification()
        }
    }

    private fun stopService() {
        isStopping = true
        if (isOreoPlus()) {
            stopForeground(true)
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bus.unregister(this)
    }

    private fun notification(title: String, contentText: String, firstRunningTimerId: Int, number: String = ""): Notification {
        val channelId = "simple_alarm_timer"
        val label = getString(R.string.timer)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isOreoPlus()) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            NotificationChannel(channelId, label, importance).apply {
                setSound(null, null)
                notificationManager.createNotificationChannel(this)
            }
        }

        val builder = NotificationCompat.Builder(this)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_remind_call)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setSound(null)
            .setOngoing(true)
            .setAutoCancel(true)
            .setChannelId(channelId)
            .addAction(
                R.drawable.ic_cross_vector,
                getString(R.string.dismiss),
                getHideTimerPendingIntent(firstRunningTimerId)
            )
        if (number != "") {
            builder.addAction(
                R.drawable.ic_messages,
                getString(R.string.message),
                sendSMSPendingIntentUpdateCurrent(number)
            )
        }
        if (number != "") {
            builder.addAction(
                R.drawable.ic_phone_vector,
                getString(R.string.call_back_g),
                startCallPendingIntentUpdateCurrent(number)
            )
        }

        if (firstRunningTimerId != INVALID_TIMER_ID) {
            builder.setContentIntent(this.getOpenTimerTabIntent(firstRunningTimerId))
        }

        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return builder.build()
    }
}

fun startTimerService(context: Context) {
    Handler(Looper.getMainLooper()).post {
        try {
            ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
        } catch (e: Exception) {
            context.showErrorToast(e)
        }
    }
}

object TimerStopService
