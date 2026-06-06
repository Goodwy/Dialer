package com.goodwy.dialer.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.telecom.Call
import android.view.View.VISIBLE
import android.widget.RemoteViews
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.isSPlus
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.extensions.audioManager
import com.goodwy.dialer.models.AudioRoute
import com.goodwy.dialer.receivers.CallActionReceiver

class CallNotificationManager(private val context: Context) {
    companion object {
        private const val CALL_NOTIFICATION_ID = 42
        private const val ACCEPT_CALL_CODE = 0
        private const val DECLINE_CALL_CODE = 1
        private const val MICROPHONE_CALL_CODE = 2
        private const val SPEAKER_CALL_CODE = 3
    }

    private val notificationManager = context.notificationManager
    private val callContactAvatarHelper = CallContactAvatarHelper(context)
    // Flag to prevent notification display after service closure
    private var isServiceActive = false

    fun updateNotification() {
        setupNotification(false)
    }

    fun setupNotification(lowPriority: Boolean = false) {
        isServiceActive = true
        try {
            // Ringing: CallStyle (Android 12+) gives the WhatsApp avatar layout and
            // system-themed, always-readable Answer/Decline buttons.
            // Ongoing: the custom RemoteView path is used instead, because CallStyle's
            // extra Speaker/Mute actions get rendered by some OEM skins (Samsung OneUI)
            // as white-on-white text pills with no icons. The custom view draws those
            // as readable colored buttons with icons and still shows the avatar on the
            // left of the content.
            val isRinging = CallManager.getState() == Call.STATE_RINGING
            if (isSPlus() && isRinging) {
                setupNotificationNew(lowPriority)
            } else {
                setupNotificationOld(lowPriority)
            }
        } catch (e: Exception) {
            cancelNotification()
            context.baseConfig.lastError = "CallNotificationManager().setupNotification(): $e"
            setupNotificationForError(lowPriority)
        }
    }

    @SuppressLint("NewApi")
    fun setupNotificationOld(lowPriority: Boolean) {
        getCallContact(context.applicationContext, CallManager.getPrimaryCall()) { callContact ->
            val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact.photoUri)
            val callState = CallManager.getState()
            val isHighPriority = callState == Call.STATE_RINGING && !lowPriority
            val channelId =
                if (isHighPriority) "right_dialer_call_high_priority" else "right_dialer_call"
            createNotificationChannel(isHighPriority, channelId)

            val openAppIntent = CallActivity.getStartIntent(context)
            //requestCode - NON_FULL_SCREEN = 0, FULL_SCREEN = 1, BUBBLE = 2
            val openAppPendingIntent =
                PendingIntent.getActivity(context, 1, openAppIntent, PendingIntent.FLAG_MUTABLE)

            val acceptCallIntent = Intent(context, CallActionReceiver::class.java)
            acceptCallIntent.action = ACCEPT_CALL
            val acceptPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    ACCEPT_CALL_CODE,
                    acceptCallIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)

            val declineCallIntent = Intent(context, CallActionReceiver::class.java)
            declineCallIntent.action = DECLINE_CALL
            val declinePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    DECLINE_CALL_CODE,
                    declineCallIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)

            val microphoneCallIntent = Intent(context, CallActionReceiver::class.java)
            microphoneCallIntent.action = MICROPHONE_CALL
            val microphonePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    MICROPHONE_CALL_CODE,
                    microphoneCallIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)

            val speakerCallIntent = Intent(context, CallActionReceiver::class.java)
            speakerCallIntent.action = SPEAKER_CALL
            val speakerPendingIntent = PendingIntent.getBroadcast(
                context,
                SPEAKER_CALL_CODE,
                speakerCallIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val callerName = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }

            val contentTextId = when (callState) {
                Call.STATE_RINGING -> R.string.is_calling
                Call.STATE_DIALING -> R.string.dialing
                Call.STATE_DISCONNECTED -> R.string.call_ended
                Call.STATE_DISCONNECTING -> R.string.call_ending
                else -> R.string.ongoing_call
            }

            val isMicrophoneMute = context.audioManager.isMicrophoneMute
            val isSpeakerOn = CallManager.getCallAudioRoute() == AudioRoute.SPEAKER
            val connectTime = CallManager.getCallConnectTime()
            val showChronometer = callState == Call.STATE_ACTIVE && connectTime > 0

            // The avatar is shown as the system LARGE ICON so the collapsed notification is
            // fully system-rendered (readable in any theme, a single icon with the app badge),
            // WhatsApp style. Fall back to a colored letter/company icon when there is no photo.
            val avatarBitmap: android.graphics.Bitmap? = try {
                if (callContactAvatar != null) {
                    callContactAvatarHelper.getCircularBitmap(callContactAvatar)
                } else {
                    val contactName = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }
                    when {
                        callContact.number == callContact.name -> SimpleContactsHelper(context).getColoredContactIcon(contactName).convertToBitmap()
                        callContact.isABusinessCall -> SimpleContactsHelper(context).getColoredCompanyIcon(contactName).convertToBitmap()
                        else -> SimpleContactsHelper(context).getContactLetterIcon(contactName)
                    }
                }
            } catch (_: Exception) {
                null
            }
            // Badge the app icon onto the avatar's bottom-right corner (WhatsApp / CallStyle look).
            val badgedAvatar = avatarBitmap?.let { callContactAvatarHelper.getBadgedAvatar(it) }

            val statusText = context.getString(contentTextId)

            // A custom RemoteView does not reliably inherit the notification-shade theme, so set
            // the expanded-view text colors explicitly from the system dark/light mode.
            val isNightMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val primaryTextColor = if (isNightMode) Color.WHITE else Color.parseColor("#DD000000")
            val secondaryTextColor = if (isNightMode) Color.parseColor("#B3FFFFFF") else Color.parseColor("#8A000000")

            // Expanded ("big") view only: the caller line plus the three action buttons. The
            // collapsed view is the standard system template built from the fields below.
            val bigView = RemoteViews(context.packageName, R.layout.call_notification).apply {
                if (badgedAvatar != null) {
                    setImageViewBitmap(R.id.notification_thumbnail, badgedAvatar)
                }
                setTextViewText(R.id.notification_caller_name, callerName)
                setTextColor(R.id.notification_caller_name, primaryTextColor)
                setTextColor(R.id.notification_call_status, secondaryTextColor)
                setTextColor(R.id.notification_chronometer, secondaryTextColor)
                if (showChronometer) {
                    setViewVisibility(R.id.notification_chronometer, VISIBLE)
                    setViewVisibility(R.id.notification_call_status, android.view.View.GONE)
                    val base = android.os.SystemClock.elapsedRealtime() -
                        (System.currentTimeMillis() - connectTime)
                    setChronometer(R.id.notification_chronometer, base, null, true)
                } else {
                    setViewVisibility(R.id.notification_chronometer, android.view.View.GONE)
                    setViewVisibility(R.id.notification_call_status, VISIBLE)
                    setTextViewText(R.id.notification_call_status, statusText)
                }

                // Incoming call (pre-Android-12 fallback): accept/reject
                setVisibleIf(R.id.notification_actions_holder, callState == Call.STATE_RINGING)
                setOnClickPendingIntent(R.id.notification_decline_call, declinePendingIntent)
                setOnClickPendingIntent(R.id.notification_accept_call, acceptPendingIntent)

                // Ongoing call: speaker / mute / hang up
                setVisibleIf(R.id.notification_actions_call_holder, callState != Call.STATE_RINGING)
                setOnClickPendingIntent(R.id.notification_decline_call_button, declinePendingIntent)

                val speakerIcon =
                    if (isSpeakerOn) R.drawable.ic_volume_up_vector else R.drawable.ic_volume_down_vector
                setImageViewResource(R.id.notification_speaker_button, speakerIcon)
                val speakerDescription =
                    if (isSpeakerOn) context.getString(R.string.turn_speaker_off)
                    else context.getString(R.string.turn_speaker_on)
                setContentDescription(R.id.notification_speaker_button, speakerDescription)
                setOnClickPendingIntent(R.id.notification_speaker_button, speakerPendingIntent)

                val microphoneIcon =
                    if (isMicrophoneMute) R.drawable.ic_microphone_off_vector else R.drawable.ic_microphone_vector
                setImageViewResource(R.id.notification_mute_button, microphoneIcon)
                val microphoneDescription =
                    if (isMicrophoneMute) context.getString(R.string.unmute)
                    else context.getString(R.string.mute)
                setContentDescription(R.id.notification_mute_button, microphoneDescription)
                setOnClickPendingIntent(R.id.notification_mute_button, microphonePendingIntent)
            }

            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_phone_vector)
                .setLargeIcon(badgedAvatar)
                .setContentTitle(callerName)
                .setContentText(statusText)
                .setContentIntent(openAppPendingIntent)
                .setCategory(Notification.CATEGORY_CALL)
                // Standard collapsed view (system-rendered: large-icon avatar + title + text,
                // readable in any theme) plus a custom EXPANDED view that carries the buttons.
                // No DecoratedCustomViewStyle, so the collapsed view is never a custom RemoteView.
                .setCustomBigContentView(bigView)
                .setOngoing(true)
                .setTimeoutAfter(-1)
                .setChannelId(channelId)

            // Pre-Android-12 ringing fallback: also use the custom view collapsed so Accept /
            // Decline stay inline in the heads-up (Android 12+ ringing uses CallStyle instead,
            // and ongoing calls keep the clean standard collapsed view with buttons-on-expand).
            if (callState == Call.STATE_RINGING) {
                builder.setCustomContentView(bigView)
            }

            // Show the live call duration in the collapsed view's time slot for active calls.
            if (showChronometer) {
                builder.setWhen(connectTime)
                builder.setUsesChronometer(true)
                builder.setShowWhen(true)
            } else {
                builder.setUsesChronometer(false)
                builder.setShowWhen(false)
            }

            if (isHighPriority) {
                builder.setFullScreenIntent(openAppPendingIntent, true)
            }

            val notification = builder.build()
            // it's rare but possible for the call state to change by now
            // We verify that the call still exists in CallManager.
            if (isServiceActive && CallManager.getPrimaryCall() != null && CallManager.getState() == callState) {
                notificationManager.notify(CALL_NOTIFICATION_ID, notification)
            } else {
                // If the call is no longer there, we turn off the notification just in case
                notificationManager.cancel(CALL_NOTIFICATION_ID)
            }
        }
    }

    fun createNotificationChannel(isHighPriority: Boolean, channelId: String) {
        val name = if (isHighPriority) {
            context.getString(R.string.call_notification_channel_high_priority)
        } else {
            context.getString(R.string.call_notification_channel)
        }

        val importance = if (isHighPriority) IMPORTANCE_HIGH else IMPORTANCE_DEFAULT
        NotificationChannel(channelId, name, importance).apply {
//            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }
    }

    fun cancelNotification() {
        isServiceActive = false
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }

    @SuppressLint("NewApi")
    fun setupNotificationNew(lowPriority: Boolean) {
        try {
            getCallContact(context.applicationContext, CallManager.getPrimaryCall()) { callContact ->
                val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact.photoUri)
                val callState = CallManager.getState()
                val isHighPriority = callState == Call.STATE_RINGING && !lowPriority
                val channelId =
                    if (isHighPriority) "right_dialer_call_high_priority" else "right_dialer_call"
                createNotificationChannel(isHighPriority, channelId)

                val openAppIntent = CallActivity.getStartIntent(context)
                //requestCode - NON_FULL_SCREEN = 0, FULL_SCREEN = 1, BUBBLE = 2
                val openAppPendingIntent =
                    PendingIntent.getActivity(context, 1, openAppIntent, PendingIntent.FLAG_MUTABLE)

                val acceptCallIntent = Intent(context, CallActionReceiver::class.java)
                acceptCallIntent.action = ACCEPT_CALL
                val acceptPendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        ACCEPT_CALL_CODE,
                        acceptCallIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                val declineCallIntent = Intent(context, CallActionReceiver::class.java)
                declineCallIntent.action = DECLINE_CALL
                val declinePendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        DECLINE_CALL_CODE,
                        declineCallIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                val name = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }
                var callerName = name
                if (callContact.numberLabel.isNotEmpty()) {
                    callerName += " - ${callContact.numberLabel}"
                }

                val icon: Icon? =
                    try {
                        if (callContactAvatar == null) {
                            val image =
                                if (callContact.number == callContact.name) SimpleContactsHelper(context).getColoredContactIcon(name).convertToBitmap()
                                else if (callContact.isABusinessCall) SimpleContactsHelper(context).getColoredCompanyIcon(name).convertToBitmap()
                                else SimpleContactsHelper(context).getContactLetterIcon(name)
                            Icon.createWithBitmap(image)
                        } else {
                            Icon.createWithBitmap(callContactAvatar)
                        }
                    } catch (_: Exception) {
                        null
                    }

                val person: Person = Person.Builder()
                    .setName(callerName)
                    .setIcon(icon)
                    .build()

                val style = if (callState == Call.STATE_RINGING) {
                    Notification.CallStyle.forIncomingCall(person, declinePendingIntent, acceptPendingIntent)
                } else {
                    Notification.CallStyle.forOngoingCall(person, declinePendingIntent)
                }

                val builder = Notification.Builder(context, channelId)
                    .setFullScreenIntent(openAppPendingIntent, isHighPriority)
                    .setSmallIcon(R.drawable.ic_phone_vector)
                    .setContentIntent(openAppPendingIntent)
                    .setCategory(Notification.CATEGORY_CALL)
                    .setOngoing(true)
                    .setTimeoutAfter(-1)
//                    .setUsesChronometer(callState == Call.STATE_ACTIVE)
                    .setChannelId(channelId)
                    .setColorized(true)
                    // Give the system (and OEM skins like Samsung OneUI) an explicit accent
                    // color so action chips don't end up rendered as white-on-white in dark mode.
                    .setColor(context.getProperPrimaryColor())
                    .setStyle(style)
                    .addPerson(person)

                // Surface accept/decline directly to Wear OS / Galaxy Watch so the watch
                // shows the call even when its companion app's per-app allow-list
                // doesn't auto-enroll a non-stock dialer. The CallStyle pending intents
                // are still used for the phone side; this extender just duplicates them
                // explicitly for the wearable surface.
                if (callState == Call.STATE_RINGING) {
                    val acceptAction = Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_phone_vector),
                        context.getString(R.string.accept),
                        acceptPendingIntent
                    ).build()
                    val declineAction = Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_phone_down_vector),
                        context.getString(R.string.decline),
                        declinePendingIntent
                    ).build()
                    @Suppress("DEPRECATION")
                    Notification.WearableExtender()
                        .addAction(acceptAction)
                        .addAction(declineAction)
                        .extend(builder)
                }

                if (callState == Call.STATE_ACTIVE) {
                    val connectTime = CallManager.getCallConnectTime()
                    if (connectTime > 0) {
                        builder.setWhen(connectTime)
                        builder.setUsesChronometer(true)
                        builder.setShowWhen(true)
                    } else {
                        builder.setUsesChronometer(false)
                        builder.setShowWhen(false)
                    }
                } else {
                    builder.setUsesChronometer(false)
                    builder.setShowWhen(false)
                }

                // Dynamic microphone and speaker icon for active call
                if (callState != Call.STATE_RINGING) {
                    // Action speaker
                    val isSpeakerOn = CallManager.getCallAudioRoute() == AudioRoute.SPEAKER
                    val speakerIcon = if (isSpeakerOn) {
                        R.drawable.ic_volume_up_vector
                    } else {
                        R.drawable.ic_volume_down_vector
                    }

                    val speakerLabel = context.getString(R.string.audio_route_speaker)
//                    if (isSpeakerOn) {
//                    context.getString(R.string.turn_speaker_off)
//                } else {
//                    context.getString(R.string.turn_speaker_on)
//                }

                    val speakerCallIntent = Intent(context, CallActionReceiver::class.java)
                    speakerCallIntent.action = SPEAKER_CALL
                    val speakerPendingIntent = PendingIntent.getBroadcast(
                        context,
                        SPEAKER_CALL_CODE,
                        speakerCallIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    val speakerCallAction = Notification.Action.Builder(
                        Icon.createWithResource(context, speakerIcon),
                        speakerLabel,
                        speakerPendingIntent
                    ).build()

                    // Action mute
                    val isMicrophoneMute = context.audioManager.isMicrophoneMute
                    val microphoneMuteIcon = if (isMicrophoneMute) {
                        R.drawable.ic_microphone_off_vector
                    } else {
                        R.drawable.ic_microphone_vector
                    }

                    val microphoneLabel = if (isMicrophoneMute) {
                        context.getString(R.string.unmute)
                    } else {
                        context.getString(R.string.mute)
                    }

                    val microphoneCallIntent = Intent(context, CallActionReceiver::class.java)
                    microphoneCallIntent.action = MICROPHONE_CALL
                    val microphonePendingIntent = PendingIntent.getBroadcast(
                        context,
                        MICROPHONE_CALL_CODE,
                        microphoneCallIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    val microphoneCallAction = Notification.Action.Builder(
                        Icon.createWithResource(context, microphoneMuteIcon),
                        microphoneLabel,
                        microphonePendingIntent
                    ).build()

                    // Add both buttons to the notification
                    builder.addAction(speakerCallAction)
                    builder.addAction(microphoneCallAction)
                }

                val notification = builder.build()
                // it's rare but possible for the call state to change by now
                // We verify that the call still exists in CallManager.
                if (isServiceActive && CallManager.getPrimaryCall() != null && CallManager.getState() == callState) {
                    notificationManager.notify(CALL_NOTIFICATION_ID, notification)
                } else {
                    // If the call is no longer there, we turn off the notification just in case
                    notificationManager.cancel(CALL_NOTIFICATION_ID)
                }
            }
        } catch (e: Exception) {
            cancelNotification()
            context.baseConfig.lastError = "CallNotificationManager().setupNotificationNew(): $e"
            setupNotificationOld(lowPriority)
        }
    }

    @SuppressLint("NewApi")
    fun setupNotificationForError(lowPriority: Boolean) {
        getCallContact(context.applicationContext, CallManager.getPrimaryCall()) { callContact ->
            val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact.photoUri)
            val callState = CallManager.getState()
            val isHighPriority = callState == Call.STATE_RINGING && !lowPriority
            val channelId = if (isHighPriority) "right_dialer_call_high_priority" else "right_dialer_call"
            val importance = if (isHighPriority) IMPORTANCE_HIGH else IMPORTANCE_DEFAULT
            val name = if (isHighPriority) {
                context.getString(R.string.call_notification_channel_high_priority)
            } else {
                context.getString(R.string.call_notification_channel)
            }

            NotificationChannel(channelId, name, importance).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                notificationManager.createNotificationChannel(this)
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

            var callerName = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }
            if (callContact.numberLabel.isNotEmpty()) {
                callerName += " - ${callContact.numberLabel}"
            }

            val icon: Icon? = if (callContactAvatar == null) null
            else Icon.createWithAdaptiveBitmap(callContactAvatar)

            val person: Person = Person.Builder()
                .setName(callerName)
                .setIcon(icon)
                .build()

            val style = if (callState == Call.STATE_RINGING) {
                Notification.CallStyle.forIncomingCall(person, declinePendingIntent, acceptPendingIntent)
            } else {
                Notification.CallStyle.forOngoingCall(person, declinePendingIntent)
            }

            val builder = Notification.Builder(context, channelId)
                .setFullScreenIntent(openAppPendingIntent, isHighPriority)
                .setSmallIcon(R.drawable.ic_phone_vector)
                .setContentIntent(openAppPendingIntent)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setTimeoutAfter(-1)
                .setUsesChronometer(callState == Call.STATE_ACTIVE)
                .setChannelId(channelId)
                .setStyle(style)
                .addPerson(person)

            if (false /*callState != Call.STATE_RINGING*/) {
                val microphoneCallIntent = Intent(context, CallActionReceiver::class.java)
                microphoneCallIntent.action = MICROPHONE_CALL
                val microphonePendingIntent =
                    PendingIntent.getBroadcast(context, DECLINE_CALL_CODE, microphoneCallIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)
                val microphoneMuteIcon = if (context.audioManager.isMicrophoneMute) R.drawable.ic_microphone_off_vector else R.drawable.ic_microphone_vector
                val microphoneCallAction = Notification.Action.Builder(microphoneMuteIcon, context.getString(R.string.mute), microphonePendingIntent)
                builder.addAction(microphoneCallAction.build())
            }

            val notification = builder.build()
            // it's rare but possible for the call state to change by now
            // We verify that the call still exists in CallManager.
            if (isServiceActive && CallManager.getPrimaryCall() != null && CallManager.getState() == callState) {
                notificationManager.notify(CALL_NOTIFICATION_ID, notification)
            } else {
                // If the call is no longer there, we turn off the notification just in case
                notificationManager.cancel(CALL_NOTIFICATION_ID)
            }
        }
    }
}
