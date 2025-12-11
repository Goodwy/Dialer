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
import android.view.View.VISIBLE
import android.widget.RemoteViews
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.isSPlus
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.extensions.audioManager
import com.goodwy.dialer.extensions.getCountryByNumber
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

    fun updateNotification() {
        setupNotification(false)
    }

    fun setupNotification(lowPriority: Boolean = false) {
        if (isSPlus()) setupNotificationNew(lowPriority) else setupNotificationOld(lowPriority)
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

            var callerName = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }
            if (callContact.numberLabel.isNotEmpty()) {
                callerName += " - ${callContact.numberLabel}"
            }

            val callerNumberType = if (callContact.name == callContact.number) {
                callContact.number.getCountryByNumber()
            } else callContact.numberLabel

            val contentTextId = when (callState) {
                Call.STATE_RINGING -> R.string.is_calling
                Call.STATE_DIALING -> R.string.dialing
                Call.STATE_DISCONNECTED -> R.string.call_ended
                Call.STATE_DISCONNECTING -> R.string.call_ending
                else -> R.string.ongoing_call
            }

            val isMicrophoneMute = context.audioManager.isMicrophoneMute
            val isSpeakerOn = CallManager.getCallAudioRoute() == AudioRoute.SPEAKER

            val collapsedView = RemoteViews(context.packageName, R.layout.call_notification).apply {
                setText(R.id.notification_caller_name, callerName)
                if (callerNumberType != "") {
                    setViewVisibility(R.id.notification_caller_number_type, VISIBLE)
                    setText(R.id.notification_caller_number_type, callerNumberType)
                }
                setText(R.id.notification_call_status, context.getString(contentTextId))
                // Incoming call (accept/reject)
                setVisibleIf(R.id.notification_actions_holder, callState == Call.STATE_RINGING)
                setOnClickPendingIntent(R.id.notification_decline_call, declinePendingIntent)
                setOnClickPendingIntent(R.id.notification_accept_call, acceptPendingIntent)

                // Active call (microphone/speaker)
                setVisibleIf(R.id.notification_actions_call_holder, callState != Call.STATE_RINGING)
                setOnClickPendingIntent(R.id.notification_decline_call_button, declinePendingIntent)
                 // Speaker button settings
                val speakerIcon =
                    if (isSpeakerOn) R.drawable.ic_volume_up_vector else R.drawable.ic_volume_down_vector
                setImageViewResource(R.id.notification_speaker_button, speakerIcon)
                val speakerLabel =
                    if (isSpeakerOn) context.getString(R.string.turn_speaker_off)
                    else context.getString(R.string.turn_speaker_on)
                setContentDescription(R.id.notification_speaker_button, speakerLabel)
                setOnClickPendingIntent(R.id.notification_speaker_button, speakerPendingIntent)
                 // Microphone button settings
                val microphoneIcon =
                    if (isMicrophoneMute) R.drawable.ic_microphone_off_vector else R.drawable.ic_microphone_vector
                setImageViewResource(R.id.notification_mute_button, microphoneIcon)
                val microphoneLabel =
                    if (isMicrophoneMute) context.getString(R.string.unmute)
                    else context.getString(R.string.mute)
                setContentDescription(R.id.notification_mute_button, microphoneLabel)
                setOnClickPendingIntent(R.id.notification_mute_button, microphonePendingIntent)

                if (callContactAvatar != null) {
                    setImageViewBitmap(
                        R.id.notification_thumbnail,
                        callContactAvatarHelper.getCircularBitmap(callContactAvatar))
                }
            }

            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_phone_vector)
                .setContentIntent(openAppPendingIntent)
                .setCategory(Notification.CATEGORY_CALL)
                .setCustomContentView(collapsedView)
                .setOngoing(true)
                .setTimeoutAfter(-1)
                .setUsesChronometer(callState == Call.STATE_ACTIVE)
                .setChannelId(channelId)
                .setStyle(Notification.DecoratedCustomViewStyle())

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
                    .setUsesChronometer(callState == Call.STATE_ACTIVE)
                    .setChannelId(channelId)
                    .setStyle(style)
                    .addPerson(person)

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
                if (CallManager.getState() == callState) {
                    notificationManager.notify(CALL_NOTIFICATION_ID, notification)
                }
            }
        } catch (_: Exception) {
            setupNotificationOld(lowPriority)
        }
    }
}
