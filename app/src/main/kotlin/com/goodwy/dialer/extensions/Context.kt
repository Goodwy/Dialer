package com.goodwy.dialer.extensions

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.telephony.SubscriptionManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.dialer.R
import com.goodwy.dialer.models.SIMAccount
import com.goodwy.commons.helpers.isOreoPlus
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.activities.MainActivity
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.activities.SplashActivity
import com.goodwy.dialer.databases.AppDatabase
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.interfaces.TimerDao
import com.goodwy.dialer.models.Timer
import com.goodwy.dialer.models.TimerState
import com.goodwy.dialer.receivers.TimerReceiver
import me.leolin.shortcutbadger.ShortcutBadger
import androidx.core.net.toUri

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
    try {
        // notification cancellation triggers MissedCallNotifier.clearMissedCalls() which, in turn,
        // should update the database and reset the cached missed call count in MissedCallNotifier.java
        // https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#170
        telecomManager.cancelMissedCallsNotification()

        notificationManager.cancel(420)
        updateUnreadCountBadge(0)
        RecentsHelper(this).markMissedCallsAsRead()
    } catch (ignored: Exception) {
    }
}

fun Context.updateUnreadCountBadge(count: Int) {
    if (count == 0) {
        ShortcutBadger.removeCount(this)
    } else {
        ShortcutBadger.applyCount(this, count)
    }
}

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
val Context.callerNotesHelper: CallerNotesHelper get() = CallerNotesHelper(this)

fun Context.getOpenTimerTabIntent(timerId: Int): PendingIntent {
    val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
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
    val channelId = timer.channelId ?: "right_dialer_timer_channel_${soundUri}_${System.currentTimeMillis()}"
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
            setSound(soundUri.toUri(), audioAttributes)

            if (!timer.vibrate) {
                vibrationPattern = longArrayOf(0L)
            }

            enableVibration(timer.vibrate)
            notificationManager.createNotificationChannel(this)
        }
    }

    val restart = Intent(this, TimerReceiver::class.java).apply {
//        action = TIMER_RESTART
        putExtra(TIMER_ID, timer.id!!)
    }
    val cancelIntent = PendingIntent.getBroadcast(
        this, timer.id!!, restart, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val collapsedView = RemoteViews(this.packageName, R.layout.timer_notification).apply {
        setText(R.id.timer_title, getString(R.string.remind_me))
        setText(R.id.timer_content, String.format(getString(R.string.call_back_person_g), timer.title))
        setOnClickPendingIntent(R.id.timer_repeat, cancelIntent)
    }

    val reminderActivityIntent = getOpenTimerTabIntent(timer.id!!)
    val builder = NotificationCompat.Builder(this)
//        .setContentTitle(getString(R.string.remind_me))
//        .setContentText(String.format(getString(R.string.call_back_person_g), timer.title))
        .setCategory(Notification.CATEGORY_REMINDER)
        .setCustomContentView(collapsedView)
        .setSmallIcon(R.drawable.ic_remind_call)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setDefaults(Notification.DEFAULT_LIGHTS)
        .setAutoCancel(true)
        .setSound(soundUri.toUri(), AudioManager.STREAM_ALARM)
        .setChannelId(channelId)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
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
            startCallPendingIntent(timer.label, BuildConfig.RIGHT_APP_KEY)
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
    val intent = Intent(this, TimerReceiver::class.java)
    intent.action = TIMER_HIDE
    intent.putExtra(TIMER_ID, timerId)
    return PendingIntent.getBroadcast(this, timerId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun Context.startCallPendingIntentUpdateCurrent(recipient: String): PendingIntent {
    return PendingIntent.getActivity(
        this,
        0,
        Intent(Intent.ACTION_CALL, Uri.fromParts("tel", recipient, null))
            .putExtra(IS_RIGHT_APP, BuildConfig.RIGHT_APP_KEY),
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

fun Context.getNotificationBitmap(photoUri: String): Bitmap? {
    val size = resources.getDimension(R.dimen.contact_photo_size).toInt()
    if (photoUri.isEmpty()) {
        return null
    }

    val options = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .centerCrop()

    return try {
        Glide.with(this)
            .asBitmap()
            .load(photoUri)
            .apply(options)
            .apply(RequestOptions.circleCropTransform())
            .into(size, size)
            .get()
    } catch (e: Exception) {
        null
    }
}

// You need to run it in ensureBackgroundThread {}
fun Context.getShortcutImageNeedBackground(path: String, placeholderName: String, callback: (image: Bitmap) -> Unit) {
    val placeholder = BitmapDrawable(resources, SimpleContactsHelper(this).getContactLetterIcon(placeholderName))
    try {
        val options = RequestOptions()
            .format(DecodeFormat.PREFER_ARGB_8888)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .error(placeholder)
            .centerCrop()

        val size = resources.getDimension(R.dimen.shortcut_size).toInt()
        val bitmap = Glide.with(this).asBitmap()
            .load(path)
            //.placeholder(placeholder)
            .apply(options)
            .apply(RequestOptions.circleCropTransform())
            .into(size, size)
            .get()

        callback(bitmap)
    } catch (ignored: Exception) {
        callback(placeholder.bitmap)
    }
}
