package com.goodwy.dialer.extensions

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.telecomManager
import com.goodwy.commons.helpers.isNougatPlus
import com.goodwy.dialer.R
import com.goodwy.dialer.helpers.Config
import com.goodwy.dialer.models.SIMAccount
import com.goodwy.commons.helpers.isOreoPlus
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.helpers.CallContactAvatarHelper
import com.goodwy.dialer.helpers.CallManager
import com.goodwy.dialer.helpers.getCallContact
import com.goodwy.dialer.models.CallContact
import me.leolin.shortcutbadger.ShortcutBadger
import java.util.*
import kotlin.collections.ArrayList

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.audioManager: AudioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

val Context.powerManager: PowerManager get() = getSystemService(Context.POWER_SERVICE) as PowerManager

@SuppressLint("MissingPermission")
fun Context.getAvailableSIMCardLabels(): ArrayList<SIMAccount> {
    val SIMAccounts = ArrayList<SIMAccount>()
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
    val id = 420// если нужно группировать = callContact.number.hashCode()
    val callContactAvatarHelper = CallContactAvatarHelper(this)
    val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact)
    val callerName = if (callContact != null && callContact.name.isNotEmpty()) callContact.name
    else if (callContact.numberLabel.isNotEmpty()) " - ${callContact.numberLabel}"
    else this.getString(R.string.unknown_caller)
    config.numberMissedCalls = config.numberMissedCalls + 1

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    if (isOreoPlus()) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        val name = getString(R.string.missed_call_notifications_g)
        val importance = NotificationManager.IMPORTANCE_HIGH
        NotificationChannel("right_dialer_missed_call", name, importance).apply {
            setBypassDnd(false)
            notificationManager.createNotificationChannel(this)
        }
    }

    val openAppIntent = CallActivity.getStartIntent(this)

    val pendingIntent = PendingIntent.getActivity(this, id, openAppIntent, PendingIntent.FLAG_MUTABLE)
    val summaryText = getString(R.string.missed_call_g)
    /*val markAsReadIntent = Intent(this, MarkAsReadReceiver::class.java).apply {
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
        setSmallIcon(R.drawable.ic_missed_call_vector)
        setContentIntent(pendingIntent)
        priority = NotificationCompat.PRIORITY_MAX
        setDefaults(Notification.DEFAULT_LIGHTS)
        setCategory(Notification.CATEGORY_MISSED_CALL)
        setAutoCancel(true)
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
