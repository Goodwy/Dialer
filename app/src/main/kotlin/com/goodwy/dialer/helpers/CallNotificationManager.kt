package com.goodwy.dialer.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.telecom.Call
import android.telecom.DisconnectCause
import android.view.View.VISIBLE
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.isOreoPlus
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.extensions.getCountryByNumber
import com.goodwy.dialer.extensions.powerManager
import com.goodwy.dialer.receivers.CallActionReceiver
import kotlinx.android.synthetic.main.activity_call_history.*

class CallNotificationManager(private val context: Context) {
    private val CALL_NOTIFICATION_ID = 42
    private val ACCEPT_CALL_CODE = 0
    private val DECLINE_CALL_CODE = 1
    private val notificationManager = context.notificationManager
    private val callContactAvatarHelper = CallContactAvatarHelper(context)

    @SuppressLint("NewApi")
    fun setupNotification(forceLowPriority: Boolean = false) {
        getCallContact(context.applicationContext, CallManager.getPrimaryCall()) { callContact ->
            val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact)
            val callState = CallManager.getState()
            val isHighPriority = context.powerManager.isInteractive && callState == Call.STATE_RINGING && !forceLowPriority
            val channelId = if (isHighPriority) "right_dialer_call_high_priority" else "right_dialer_call"
            if (isOreoPlus()) {
                val importance = if (isHighPriority) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                val name = if (isHighPriority) context.getString(R.string.call_notification_channel_high_priority_g)
                                else context.getString(R.string.call_notification_channel_g)

                NotificationChannel(channelId, name, importance).apply {
                    setSound(null, null)
                    notificationManager.createNotificationChannel(this)
                }
            }

            val openAppIntent = CallActivity.getStartIntent(context)
            //requestCode - NON_FULL_SCREEN = 0, FULL_SCREEN = 1, BUBBLE = 2
            val openAppPendingIntent = PendingIntent.getActivity(context, 1, openAppIntent, PendingIntent.FLAG_MUTABLE)

            val acceptCallIntent = Intent(context, CallActionReceiver::class.java)
            acceptCallIntent.action = ACCEPT_CALL
            val acceptPendingIntent =
                PendingIntent.getBroadcast(context, ACCEPT_CALL_CODE, acceptCallIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)

            val declineCallIntent = Intent(context, CallActionReceiver::class.java)
            declineCallIntent.action = DECLINE_CALL
            val declinePendingIntent =
                PendingIntent.getBroadcast(context, DECLINE_CALL_CODE, declineCallIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)

            var callerName = if (callContact.name.isNotEmpty()) callContact.name else context.getString(R.string.unknown_caller)
            if (callContact.numberLabel.isNotEmpty()) {
                callerName += " - ${callContact.numberLabel}"
            }

            var callerNumberType = ""
            if (callContact.name == callContact.number) {
                val country = if (callContact.number.startsWith("+")) getCountryByNumber(context, callContact.number) else ""
                if (country != "") callerNumberType = country
            } else callerNumberType = callContact.numberLabel

            val contentTextId = when (callState) {
                Call.STATE_RINGING -> R.string.is_calling
                Call.STATE_DIALING -> R.string.dialing
                Call.STATE_DISCONNECTED -> R.string.call_ended
                Call.STATE_DISCONNECTING -> R.string.call_ending
                else -> R.string.ongoing_call
            }

            val collapsedView = RemoteViews(context.packageName, R.layout.call_notification).apply {
                setText(R.id.notification_caller_name, callerName)
                if (callerNumberType != "") {
                    setViewVisibility(R.id.notification_caller_number_type, VISIBLE)
                    setText(R.id.notification_caller_number_type, callerNumberType)
                }
                setText(R.id.notification_call_status, context.getString(contentTextId))
                setVisibleIf(R.id.notification_accept_call, callState == Call.STATE_RINGING)

                setOnClickPendingIntent(R.id.notification_decline_call, declinePendingIntent)
                setOnClickPendingIntent(R.id.notification_accept_call, acceptPendingIntent)

                if (callContactAvatar != null) {
                    setImageViewBitmap(R.id.notification_thumbnail, callContactAvatarHelper.getCircularBitmap(callContactAvatar))
                }
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_phone_vector)
                .setContentIntent(openAppPendingIntent)
                .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_CALL)
                .setCustomContentView(collapsedView)
                .setOngoing(true)
                .setTimeoutAfter(-1)
                .setSound(null)
                .setUsesChronometer(callState == Call.STATE_ACTIVE)
                .setChannelId(channelId)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())

            if (isHighPriority) {
                builder.setFullScreenIntent(openAppPendingIntent, true)
            }

            val notification = builder.build()
            // it's rare but possible for the call state to change by now
            if (CallManager.getState() == callState) {
                notificationManager.notify(CALL_NOTIFICATION_ID, notification)
            }
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }
}
