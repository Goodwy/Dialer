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
import android.graphics.drawable.Icon
import android.telecom.Call
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
            // Android 12+ : the official CallStyle template for both incoming and ongoing calls
            // (avatar with app badge, "Incoming/Ongoing call" header, and icon+text call buttons
            // — Decline/Answer when ringing, Hang up + Speaker/Mute while in a call).
            // Android 8-11 : a standard Material notification, since CallStyle needs API 31+.
            if (isSPlus()) {
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

            // Standard Material notification anatomy (no custom view), so the system renders
            // everything and it stays readable + correctly badged on every OEM skin:
            //   small icon = app icon, app name = automatic, sub text = call type (header),
            //   content title = contact name (empty when not saved), content text = phone number,
            //   large icon = contact avatar / general icon (system badges the app icon on it),
            //   actions = Speaker / Mute / Hang up (Decline / Accept while ringing).
            val isSavedContact = callContact.name.isNotEmpty() && callContact.name != callContact.number
            val contentTitle = if (isSavedContact) callContact.name else ""
            val phoneNumber = callContact.number

            val callTypeId = when (callState) {
                Call.STATE_RINGING -> R.string.is_calling
                Call.STATE_DIALING -> R.string.dialing
                Call.STATE_DISCONNECTED -> R.string.call_ended
                Call.STATE_DISCONNECTING -> R.string.call_ending
                else -> R.string.ongoing_call
            }
            val callType = context.getString(callTypeId)

            val isMicrophoneMute = context.audioManager.isMicrophoneMute
            val isSpeakerOn = CallManager.getCallAudioRoute() == AudioRoute.SPEAKER
            val connectTime = CallManager.getCallConnectTime()
            val showChronometer = callState == Call.STATE_ACTIVE && connectTime > 0

            // Large icon: the contact photo, otherwise a colored general icon. The system draws
            // the small (app) icon badged on its corner automatically, so no manual compositing.
            val largeIcon: android.graphics.Bitmap? = try {
                if (callContactAvatar != null) {
                    callContactAvatarHelper.getCircularBitmap(callContactAvatar)
                } else {
                    val placeholderName = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }
                    when {
                        callContact.number == callContact.name -> SimpleContactsHelper(context).getColoredContactIcon(placeholderName).convertToBitmap()
                        callContact.isABusinessCall -> SimpleContactsHelper(context).getColoredCompanyIcon(placeholderName).convertToBitmap()
                        else -> SimpleContactsHelper(context).getContactLetterIcon(placeholderName)
                    }
                }
            } catch (_: Exception) {
                null
            }

            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_phone_vector)
                .setLargeIcon(largeIcon)
                .setSubText(callType)
                .setContentTitle(contentTitle)
                .setContentText(phoneNumber)
                .setContentIntent(openAppPendingIntent)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(-1)
                .setChannelId(channelId)

            // Live call duration in the time slot for active calls.
            if (showChronometer) {
                builder.setWhen(connectTime)
                builder.setUsesChronometer(true)
                builder.setShowWhen(true)
            } else {
                builder.setUsesChronometer(false)
                builder.setShowWhen(false)
            }

            // Behavior area: actions. While ringing show Decline / Accept; otherwise Speaker /
            // Mute / Hang up. Icons reflect the current speaker/mute state.
            if (callState == Call.STATE_RINGING) {
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_phone_down_vector),
                        context.getString(R.string.decline), declinePendingIntent
                    ).build()
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_phone_vector),
                        context.getString(R.string.accept), acceptPendingIntent
                    ).build()
                )
            } else {
                val speakerIcon = if (isSpeakerOn) R.drawable.ic_volume_up_vector else R.drawable.ic_volume_down_vector
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, speakerIcon),
                        context.getString(R.string.audio_route_speaker), speakerPendingIntent
                    ).build()
                )
                val microphoneIcon = if (isMicrophoneMute) R.drawable.ic_microphone_off_vector else R.drawable.ic_microphone_vector
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, microphoneIcon),
                        context.getString(if (isMicrophoneMute) R.string.unmute else R.string.mute), microphonePendingIntent
                    ).build()
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_phone_down_vector),
                        context.getString(R.string.hang_up), declinePendingIntent
                    ).build()
                )
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
