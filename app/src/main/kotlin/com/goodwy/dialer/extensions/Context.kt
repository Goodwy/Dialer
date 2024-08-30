package com.goodwy.dialer.extensions

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.dialer.R
import com.goodwy.dialer.models.SIMAccount
import com.goodwy.commons.helpers.isOreoPlus
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.activities.SplashActivity
import com.goodwy.dialer.databases.AppDatabase
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.interfaces.TimerDao
import com.goodwy.dialer.models.CallContact
import com.goodwy.dialer.models.Timer
import com.goodwy.dialer.models.TimerState
import com.goodwy.dialer.receivers.HideTimerReceiver
import me.leolin.shortcutbadger.ShortcutBadger
import java.util.*

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.audioManager: AudioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

val Context.powerManager: PowerManager get() = getSystemService(Context.POWER_SERVICE) as PowerManager

@SuppressLint("MissingPermission")
fun Context.getAvailableSIMCardLabels(): List<SIMAccount> {
    val SIMAccounts = mutableListOf<SIMAccount>()
    try {
        telecomManager.callCapablePhoneAccounts.forEachIndexed { index, account ->
            val phoneAccount = telecomManager.getPhoneAccount(account)
            var label = phoneAccount.label.toString()
            var address = phoneAccount.address.toString()
            if (address.startsWith("tel:") && address.substringAfter("tel:").isNotEmpty()) {
                address = Uri.decode(address.substringAfter("tel:"))
                label += " ($address)"
            }

            val SIM = SIMAccount(index + 1, phoneAccount.accountHandle, label, address.substringAfter("tel:"))
            SIMAccounts.add(SIM)
        }
    } catch (ignored: Exception) {
    }
    return SIMAccounts
}

@SuppressLint("MissingPermission")
fun Context.areMultipleSIMsAvailable(): Boolean {
    return try {
        telecomManager.callCapablePhoneAccounts.size > 1
    } catch (ignored: Exception) {
        false
    }
}

@SuppressLint("MissingPermission")
fun Context.clearMissedCalls() {
    ensureBackgroundThread {
        try {
            // notification cancellation triggers MissedCallNotifier.clearMissedCalls() which, in turn,
            // should update the database and reset the cached missed call count in MissedCallNotifier.java
            // https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#170
            telecomManager.cancelMissedCallsNotification()

            notificationManager.cancel(420)
            config.numberMissedCalls = 0
            updateUnreadCountBadge(0)
        } catch (ignored: Exception) {
        }
    }
}

fun Context.updateUnreadCountBadge(count: Int) {//conversations: List<RecentCall>) {
    //val unreadCount = conversations.count { it.type ==  CallLog.Calls.MISSED_TYPE }
    if (count == 0) {
        ShortcutBadger.removeCount(this)
    } else {
        ShortcutBadger.applyCount(this, count)
    }
}

@SuppressLint("NewApi")
fun Context.showMessageNotification(callContact: CallContact) {//(address: String, body: String, threadId: Long, bitmap: Bitmap?, sender: String) {
    val id = 420 //if you need to group = callContact.number.hashCode()
//    val callContactAvatarHelper = CallContactAvatarHelper(this)
//    val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact)
    val callerName = if (callContact != null && callContact.name.isNotEmpty()) callContact.name
                            else if (callContact.numberLabel.isNotEmpty()) " - ${callContact.numberLabel}"
                            else this.getString(R.string.unknown_caller)
    config.numberMissedCalls = config.numberMissedCalls + 1

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    if (isOreoPlus()) {
//        val audioAttributes = AudioAttributes.Builder()
//            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
//            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
//            .build()

        val name = getString(R.string.missed_call_notifications_g)
        val importance = NotificationManager.IMPORTANCE_HIGH
        NotificationChannel("right_dialer_missed_call", name, importance).apply {
            setBypassDnd(false)
            notificationManager.createNotificationChannel(this)
        }
    }

    val openAppIntent = CallActivity.getStartIntent(this)

    val pendingIntent = PendingIntent.getActivity(this, id, openAppIntent, PendingIntent.FLAG_MUTABLE)
    /*val summaryText = getString(R.string.missed_call_g)
    val markAsReadIntent = Intent(this, MarkAsReadReceiver::class.java).apply {
    action = MARK_AS_READ
    putExtra(THREAD_ID, threadId)
}

val markAsReadPendingIntent = PendingIntent.getBroadcast(this, threadId.hashCode(), markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT)
var replyAction: NotificationCompat.Action? = null

if (isNougatPlus()) {
    val replyLabel = getString(R.string.call_back_g)
    val remoteInput = RemoteInput.Builder(REPLY)
        .setLabel(replyLabel)
        .build()

    val replyIntent = Intent(this, DirectReplyReceiver::class.java).apply {
        putExtra(THREAD_ID, threadId)
        putExtra(THREAD_NUMBER, address)
    }

    val replyPendingIntent = PendingIntent.getBroadcast(applicationContext, threadId.hashCode(), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
    replyAction = NotificationCompat.Action.Builder(R.drawable.ic_send_vector, replyLabel, replyPendingIntent)
        .addRemoteInput(remoteInput)
        .build()
}*/

    //val largeIcon = bitmap ?: SimpleContactsHelper(this).getContactLetterIcon(sender)
    val builder = NotificationCompat.Builder(this, "right_dialer_missed_call").apply {
        if (config.numberMissedCalls == 1) {
            setContentTitle(getString(R.string.missed_call_g))
            setContentText(callerName)
        } else {
            setContentTitle(config.numberMissedCalls.toString() + " " + getString(R.string.missed_calls_g).lowercase())
        }
        color = getProperPrimaryColor()
        setSmallIcon(R.drawable.ic_call_missed_vector)
        setContentIntent(pendingIntent)
        priority = NotificationCompat.PRIORITY_MAX
        setDefaults(Notification.DEFAULT_LIGHTS)
        setCategory(Notification.CATEGORY_MISSED_CALL)
        setAutoCancel(true)
        addAction(
            R.drawable.ic_messages,
            getString(R.string.message),
            sendSMSPendingIntent(callContact.number)
        )
        addAction(
            R.drawable.ic_phone_vector,
            getString(R.string.call_back_g),
            startCallPendingIntent(callContact.number)
        )
    }

    /*if (replyAction != null && config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
    builder.addAction(replyAction)
}

builder.addAction(R.drawable.ic_check_vector, getString(R.string.mark_as_read), markAsReadPendingIntent)
    .setChannelId("right_dialer_call")*/

    notificationManager.notify(id, builder.build())
    updateUnreadCountBadge(config.numberMissedCalls)
}

fun Context.sysLocale(): Locale? {
    val config = this.resources.configuration
    return if (isNougatPlus()) {
        getSystemLocale(config)
    } else {
        getSystemLocaleLegacy(config)
    }
}

private fun getSystemLocaleLegacy(config: Configuration) = config.locale

@TargetApi(Build.VERSION_CODES.N)
private fun getSystemLocale(config: Configuration) = config.locales.get(0)

@SuppressLint("UseCompatLoadingForDrawables")
fun Context.getPackageDrawable(packageName: String): Drawable {
    return resources.getDrawable(
        when (packageName) {
            TELEGRAM_PACKAGE -> R.drawable.ic_telegram_vector
            SIGNAL_PACKAGE -> R.drawable.ic_signal_vector
            WHATSAPP_PACKAGE -> R.drawable.ic_whatsapp_vector
            VIBER_PACKAGE -> R.drawable.ic_viber_vector
            else -> R.drawable.ic_threema_vector
        }, theme
    )
}

//Timer
val Context.timerDb: TimerDao get() = AppDatabase.getInstance(applicationContext).TimerDao()
val Context.timerHelper: TimerHelper get() = TimerHelper(this)

fun Context.getOpenTimerTabIntent(timerId: Int): PendingIntent {
    val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
    //intent.putExtra(OPEN_TAB, TAB_TIMER)
    //intent.putExtra(TIMER_ID, timerId)
    return PendingIntent.getActivity(this, timerId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun Context.hideNotification(id: Int) {
    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.cancel(id)
}

fun Context.getTimerNotification(timer: Timer, pendingIntent: PendingIntent, addDeleteIntent: Boolean): Notification {
    var soundUri = timer.soundUri
    if (soundUri == SILENT) {
        soundUri = ""
    } else {
        grantReadUriPermission(soundUri)
    }

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = timer.channelId ?: "simple_timer_channel_${soundUri}_${System.currentTimeMillis()}"
    timerHelper.insertOrUpdateTimer(timer.copy(channelId = channelId))

    if (isOreoPlus()) {
        try {
            notificationManager.deleteNotificationChannel(channelId)
        } catch (e: Exception) {
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_ALARM)
            .build()

        val name = getString(R.string.timer)
        val importance = NotificationManager.IMPORTANCE_HIGH
        NotificationChannel(channelId, name, importance).apply {
            setBypassDnd(true)
            enableLights(true)
            lightColor = getProperPrimaryColor()
            setSound(Uri.parse(soundUri), audioAttributes)

            if (!timer.vibrate) {
                vibrationPattern = longArrayOf(0L)
            }

            enableVibration(timer.vibrate)
            notificationManager.createNotificationChannel(this)
        }
    }

    val reminderActivityIntent = getOpenTimerTabIntent(timer.id!!)
    val builder = NotificationCompat.Builder(this)
        .setContentTitle(getString(R.string.remind_me))
        .setContentText(String.format(this.getString(R.string.call_back_person_g), timer.title))
        .setSmallIcon(R.drawable.ic_remind_call)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setDefaults(Notification.DEFAULT_LIGHTS)
        .setCategory(Notification.CATEGORY_EVENT)
        .setAutoCancel(true)
        .setSound(Uri.parse(soundUri), AudioManager.STREAM_ALARM)
        .setChannelId(channelId)
        .addAction(
            com.goodwy.commons.R.drawable.ic_cross_vector,
            getString(com.goodwy.commons.R.string.dismiss),
            if (addDeleteIntent) {
                reminderActivityIntent
            } else {
                getHideTimerPendingIntent(timer.id!!)
            }
        )
        .addAction(
            R.drawable.ic_messages,
            getString(R.string.message),
            sendSMSPendingIntent(timer.label)
        )
        .addAction(
            R.drawable.ic_phone_vector,
            getString(R.string.call_back_g),
            startCallPendingIntent(timer.label)
        )

    if (addDeleteIntent) {
        builder.setDeleteIntent(reminderActivityIntent)
    }

    builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    if (timer.vibrate) {
        val vibrateArray = LongArray(2) { 500 }
        builder.setVibrate(vibrateArray)
    }

    val notification = builder.build()
    notification.flags = notification.flags or Notification.FLAG_INSISTENT
    return notification
}

fun Context.getHideTimerPendingIntent(timerId: Int): PendingIntent {
    val intent = Intent(this, HideTimerReceiver::class.java)
    intent.putExtra(TIMER_ID, timerId)
    return PendingIntent.getBroadcast(this, timerId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun Context.startCallPendingIntentUpdateCurrent(recipient: String): PendingIntent {
    return PendingIntent.getActivity(
        this,
        0,
        Intent(Intent.ACTION_CALL, Uri.fromParts("tel", recipient, null)).putExtra(IS_RIGHT_APP, BuildConfig.RIGHT_APP_KEY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun Context.sendSMSPendingIntentUpdateCurrent(recipient: String): PendingIntent {
    return PendingIntent.getActivity(this, 0,
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", recipient, null)), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun Context.hideTimerNotification(timerId: Int) = hideNotification(timerId)

fun Context.createNewTimer(): Timer {
    return Timer(
        1,
        config.timerSeconds,
        TimerState.Idle,
        config.timerVibrate,
        config.timerSoundUri,
        config.timerSoundTitle,
        config.timerTitle ?: "",
        config.timerLabel ?: "",
        config.timerDescription ?: "",
        System.currentTimeMillis(),
        config.timerChannelId,
    )
}

fun Context.subscriptionManagerCompat(): SubscriptionManager {
    return getSystemService(SubscriptionManager::class.java)
}
