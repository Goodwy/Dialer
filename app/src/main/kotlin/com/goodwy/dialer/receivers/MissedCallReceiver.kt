package com.goodwy.dialer.receivers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.CURRENT_PHONE_NUMBER
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.helpers.isOreoPlus
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.NotificationActivity
import com.goodwy.dialer.activities.SplashActivity
import com.goodwy.dialer.extensions.clearMissedCalls
import com.goodwy.dialer.extensions.getNotificationBitmap
import com.goodwy.dialer.extensions.getOpenTimerTabIntent
import com.goodwy.dialer.extensions.updateUnreadCountBadge
import com.goodwy.dialer.helpers.*

@RequiresApi(Build.VERSION_CODES.O)
class MissedCallReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val notificationManager = context.notificationManager

        when (intent.action) {
            TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION -> {
                val notificationCount = extras.getInt(TelecomManager.EXTRA_NOTIFICATION_COUNT)
                if (notificationCount != 0) {
                    val phoneNumber = extras.getString(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER) ?: context.getString(R.string.unknown_caller)
                    val notificationId = 420 //if you need to group = phoneNumber.hashCode()
                    if (isOreoPlus()) createNotificationChannel(context)
                    //notificationManager.notify(MISSED_CALLS.hashCode(), getNotificationGroup(context))
                    ensureBackgroundThread { //ensureBackgroundThread is needed to generate a round contact icon
                        notificationManager.notify(
                            notificationId,
                            buildNotification(context, notificationId, phoneNumber, notificationCount)
                        )
                    }
                }
            }

            MISSED_CALL_CANCEL -> {
                context.clearMissedCalls()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        val notificationManager = context.notificationManager
        val name = context.getString(R.string.missed_call_notifications_g)
        val importance = NotificationManager.IMPORTANCE_HIGH
        NotificationChannel("right_dialer_missed_call", name, importance).apply {
            setBypassDnd(false)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun launchIntent(context: Context): PendingIntent {
        val intent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
    }

//    private fun getNotificationGroup(context: Context): Notification {
//        return NotificationCompat.Builder(context, "right_dialer_missed_call")
//            .setSmallIcon(R.drawable.ic_call_missed_vector)
//            .setAutoCancel(true)
//            .setGroupSummary(true)
//            .setGroup(MISSED_CALLS)
//            .setContentIntent(launchIntent(context))
//            .build()
//    }

    private fun buildNotification(context: Context, notificationId: Int, phoneNumber: String, count: Int): Notification {
        val helper = SimpleContactsHelper(context)
        val name = helper.getNameFromPhoneNumber(phoneNumber)
        val photoUri = helper.getPhotoUriFromPhoneNumber(phoneNumber)

        val callBack = Intent(context, NotificationActivity::class.java).apply {
            action = MISSED_CALL_BACK
            putExtra(MISSED_CALL_NOTIFICATION_ID, notificationId)
            putExtra(CURRENT_PHONE_NUMBER, phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val callBackIntent = PendingIntent.getActivity(
            context,  notificationId,  callBack, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)

        val smsIntent = Intent(context, NotificationActivity::class.java).apply {
            action = MISSED_CALL_MESSAGE
            putExtra(MISSED_CALL_NOTIFICATION_ID, notificationId)
            putExtra(CURRENT_PHONE_NUMBER, phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val messageIntent = PendingIntent.getActivity(
            context,  notificationId,  smsIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val cancel = Intent(context, MissedCallReceiver::class.java).apply {
            action = MISSED_CALL_CANCEL
            putExtra("notificationId", notificationId)
            putExtra("phoneNumber", phoneNumber)
        }
        val cancelIntent = PendingIntent.getBroadcast(
            context, notificationId, cancel, PendingIntent.FLAG_IMMUTABLE
        )
        val bitmap = context.getNotificationBitmap(photoUri)
        val builder =  NotificationCompat.Builder(context, "right_dialer_missed_call").apply {
            if (count == 1) {
                setContentTitle(context.getString(R.string.missed_call_g))
                setContentText(name)
                setLargeIcon(bitmap)
                addAction(
                    R.drawable.ic_messages,
                    context.getString(R.string.message),
                    messageIntent
                )
                addAction(
                    R.drawable.ic_phone_vector,
                    context.getString(R.string.call_back_g),
                    callBackIntent
                )
            } else {
                setContentTitle(context.resources.getQuantityString(R.plurals.missed_calls, count, count).lowercase())
            }
            color = context.getProperPrimaryColor()
            setSmallIcon(R.drawable.ic_call_missed_vector)
            setAutoCancel(true)
            setGroup(MISSED_CALLS)
            setContentIntent(launchIntent(context))
            setDeleteIntent(cancelIntent)
        }

        context.updateUnreadCountBadge(count)
        return builder.build()
    }
}
