package com.goodwy.dialer.receivers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.CURRENT_PHONE_NUMBER
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.NotificationActivity
import com.goodwy.dialer.activities.SplashActivity
import com.goodwy.dialer.extensions.clearMissedCalls
import com.goodwy.dialer.extensions.getContactFromAddress
import com.goodwy.dialer.extensions.getNotificationBitmap
import com.goodwy.dialer.extensions.updateUnreadCountBadge
import com.goodwy.dialer.helpers.*

class MissedCallReceiver : BroadcastReceiver() {

    companion object {
        // We keep track of missed calls by number
        private val missedCallCounts = mutableMapOf<String, Int>()

        // Get the current number of missed calls for a number
//        fun getMissedCallCount(phoneNumber: String): Int {
//            return missedCallCounts[phoneNumber] ?: 0
//        }

        // Clear the counter for the room
        fun clearMissedCallCount(phoneNumber: String) {
            missedCallCounts.remove(phoneNumber)
        }

        // Clear all counters
        fun clearAllMissedCallCounts() {
            missedCallCounts.clear()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val notificationManager = context.notificationManager

        when (intent.action) {
            TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION -> {
                val notificationCount = extras.getInt(TelecomManager.EXTRA_NOTIFICATION_COUNT)
                if (notificationCount != 0) {
                    val phoneNumber = extras.getString(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
                        ?: context.getString(R.string.unknown_caller)

                    // Increase the counter for this number
                    val currentCount = missedCallCounts[phoneNumber] ?: 0
                    val newCount = currentCount + 1
                    missedCallCounts[phoneNumber] = newCount

                    createNotificationChannel(context)

                    val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                    try {
                        //ensureBackgroundThread is needed to generate a round contact icon
                        context.getContactFromAddress(phoneNumber) { simpleContact ->
                            val helper = SimpleContactsHelper(context)
                            val name = helper.getNameFromPhoneNumber(phoneNumber)
                            val photoUri = helper.getPhotoUriFromPhoneNumber(phoneNumber)
                            val bitmap = context.getNotificationBitmap(photoUri) ?: if (simpleContact != null) {
                                if (simpleContact.isABusinessContact()) {
                                    SimpleContactsHelper(context).getColoredCompanyIcon(name).convertToBitmap()
                                } else {
                                    SimpleContactsHelper(context).getContactLetterIcon(name)
                                }
                            } else  {
                                SimpleContactsHelper(context).getColoredContactIcon(name).convertToBitmap()
                            }

                            var callerName = name
                            if (simpleContact != null) {
                                val specificPhoneNumber = simpleContact.phoneNumbers.firstOrNull { it.normalizedNumber == phoneNumber.normalizePhoneNumber() }
                                if (specificPhoneNumber != null) {
                                    val label = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                                    if (label.isNotEmpty()) {
                                        callerName += " - $label"
                                    }
                                }
                            }

                            Handler(Looper.getMainLooper()).post {
                                notificationManager.notify(
                                    phoneNumber.hashCode(),
                                    buildNotification(context, callerName, phoneNumber, bitmap, newCount)
                                )
                            }
                        }
                    } catch (_: Exception) {
                        //ensureBackgroundThread is needed to generate a round contact icon
                        ensureBackgroundThread {
                            val helper = SimpleContactsHelper(context)
                            val name = helper.getNameFromPhoneNumber(phoneNumber)
                            val photoUri = helper.getPhotoUriFromPhoneNumber(phoneNumber)
                            val bitmap = context.getNotificationBitmap(photoUri)

                            Handler(Looper.getMainLooper()).post {
                                notificationManager.notify(
                                    phoneNumber.hashCode(),
                                    buildNotification(context, name, phoneNumber, bitmap, newCount)
                                )
                            }
                        }
                    }
                }
            }

            MISSED_CALL_CANCEL -> {
                val canceledPhoneNumber = extras.getString("phoneNumber")
                if (canceledPhoneNumber != null) {
                    // We reset the meter only for a specific number.
                    clearMissedCallCount(canceledPhoneNumber)
                    // We only delete notifications from this number.
                    context.notificationManager.cancel(canceledPhoneNumber.hashCode())

                    // Updating group notification and checking system omissions
                    updateUnreadCountAndGroup(context)
                } else {
                    // Clear everything (only if phoneNumber is not passed)
                    clearAllMissedCallCounts()
                    context.clearMissedCalls()
                    context.notificationManager.cancel(MISSED_CALLS.hashCode())
                    context.updateUnreadCountBadge(0)
                }
            }

            // You have to launch an activity so that the notification bar automatically disappears
            //https://stackoverflow.com/questions/18261969/clicking-android-notification-actions-does-not-close-notification-drawer?noredirect=1&lq=1
//            MISSED_CALL_BACK, MISSED_CALL_MESSAGE -> {
//                // When answering a call or message, we clear the counter for that number.
//                val respondedPhoneNumber = extras.getString(CURRENT_PHONE_NUMBER)
//                respondedPhoneNumber?.let {
//                    clearMissedCallCount(it)
//                    context.notificationManager.cancel(it.hashCode())
//                    updateUnreadCountAndGroup(context)
//                }
//            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        val notificationManager = context.notificationManager
        val name = context.getString(R.string.missed_call_notifications_g)
        val importance = NotificationManager.IMPORTANCE_HIGH

        val existingChannel = notificationManager.getNotificationChannel("right_dialer_missed_call")
        if (existingChannel == null) {
            NotificationChannel("right_dialer_missed_call", name, importance).apply {
                setBypassDnd(false)
                setSound(null, null)
                enableVibration(false)
                notificationManager.createNotificationChannel(this)
            }
        } else {
            existingChannel.setSound(null, null)
            existingChannel.enableVibration(false)
            notificationManager.createNotificationChannel(existingChannel)
        }
    }

    private fun launchIntent(context: Context): PendingIntent {
        val intent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(context: Context, name: String, phoneNumber: String, bitmap: Bitmap?, count: Int): Notification {
        val callBack = Intent(context, NotificationActivity::class.java).apply {
            action = MISSED_CALL_BACK
            putExtra(CURRENT_PHONE_NUMBER, phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val callBackIntent = PendingIntent.getActivity(
            context,  phoneNumber.hashCode(),  callBack, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)

        val smsIntent = Intent(context, NotificationActivity::class.java).apply {
            action = MISSED_CALL_MESSAGE
            putExtra(CURRENT_PHONE_NUMBER, phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val messageIntent = PendingIntent.getActivity(
            context,  phoneNumber.hashCode(),  smsIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val cancel = Intent(context, MissedCallReceiver::class.java).apply {
            action = MISSED_CALL_CANCEL
            putExtra("phoneNumber", phoneNumber)
        }
        val cancelIntent = PendingIntent.getBroadcast(
            context, phoneNumber.hashCode(), cancel, PendingIntent.FLAG_IMMUTABLE
        )

        val builder =  NotificationCompat.Builder(context, "right_dialer_missed_call").apply {
            if (count == 1) {
                setContentTitle(context.getString(R.string.missed_call_g))
                setContentText(name)
                setLargeIcon(bitmap)
                setStyle(getCallStyle(name, phoneNumber, context.getString(R.string.missed_call_g)))
            } else {
                setContentTitle(name)
                setContentText(context.resources.getQuantityString(R.plurals.missed_calls, count, count))
                setLargeIcon(bitmap)
                setStyle(getCallStyle(name, phoneNumber,
                    context.resources.getQuantityString(R.plurals.missed_calls, count, count)))
            }
            color = context.getProperPrimaryColor()
            setSmallIcon(R.drawable.ic_call_missed_vector)
            setAutoCancel(true)
            setGroup(MISSED_CALLS)
            setContentIntent(launchIntent(context))
            setDeleteIntent(cancelIntent)
            setCategory(Notification.CATEGORY_CALL)
            setSound(null)

            addAction(
                R.drawable.ic_phone_vector,
                context.getString(R.string.call_back_g),
                callBackIntent
            )
            addAction(
                R.drawable.ic_messages,
                context.getString(R.string.message),
                messageIntent
            )
        }

        val totalMissedCalls = missedCallCounts.values.sum()
        context.updateUnreadCountBadge(totalMissedCalls)

        return builder.build()
    }

    private fun getCallStyle(
        name: String,
        address: String,
        message: String,
    ): NotificationCompat.MessagingStyle {
        val caller = Person.Builder()
            .setName(name)
            .setKey(address)
            .build()

        return NotificationCompat.MessagingStyle(caller).also { style ->
            val callMessage = NotificationCompat.MessagingStyle.Message(
                message,
                System.currentTimeMillis(),
                caller
            )
            style.addMessage(callMessage)
        }
    }

    private fun updateUnreadCountAndGroup(context: Context) {
        val totalMissedCalls = missedCallCounts.values.sum()
        context.updateUnreadCountBadge(totalMissedCalls)

        if (totalMissedCalls == 0) {
            // Clear all notifications and group notifications
            context.notificationManager.cancel(MISSED_CALLS.hashCode())
            // Clearing system missed calls
            context.clearMissedCalls()
        }
    }
}
