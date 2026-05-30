package com.goodwy.dialer.services

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.extensions.canUseFullScreenIntent
import com.goodwy.commons.extensions.hasPermission
import com.goodwy.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.isOutgoing
import com.goodwy.dialer.extensions.keyguardManager
import com.goodwy.dialer.extensions.powerManager
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.Events
import org.greenrobot.eventbus.EventBus

class CallService : InCallService() {
    private val context = this
    private val callNotificationManager by lazy { CallNotificationManager(this) }

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)

            callNotificationManager.setupNotification()

            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            }

            try {
                if (baseConfig.flashForAlerts) MyCameraImpl.newInstance(context).stopSOS()
            } catch (_: Exception) { }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        // Incoming/Outgoing (locked): high priority (FSI)
        // Incoming (unlocked): if user opted in, low priority ➜ manual activity start, otherwise high priority (FSI)
        // Outgoing (unlocked): low priority ➜ manual activity start
        val isOutgoing = call.isOutgoing()
        val isIncoming = !isOutgoing
        // Use both checks so a phone that is locked but with the screen on (e.g. user
        // tapped power to look at the lock screen) still counts as locked and gets
        // the high-priority/FSI treatment.
        val isDeviceLocked = !powerManager.isInteractive || keyguardManager.isDeviceLocked
        val lowPriority = when {
            isDeviceLocked -> false // High priority on locked screen
            isIncoming && !isDeviceLocked -> config.showIncomingCallsFullScreen
            else -> true
        }

        // When the device is locked we ALSO start the activity directly, not just rely on
        // the full-screen-intent notification. Some OEM skins (e.g. Samsung OneUI) demote
        // the FSI to a silent notification, so the lock-screen call UI never appears and
        // the user has to unlock to answer. The default dialer gets a brief background-
        // activity-start grace window during onCallAdded, so this direct start is allowed.
        // CallActivity is singleTask + REORDER_TO_FRONT, so if both the direct start and
        // the FSI fire we still end up with a single instance.
        if (
            lowPriority
            || isDeviceLocked
            || !hasPermission(PERMISSION_POST_NOTIFICATIONS)
            || !canUseFullScreenIntent()
        ) {
            try {
                val needSelectSIM = isOutgoing && call.details.accountHandle == null
                startActivity(CallActivity.getStartIntent(this, needSelectSIM = needSelectSIM))
            } catch (e: Exception) {
                // seems like startActivity can throw AndroidRuntimeException and
                // ActivityNotFoundException, not yet sure when and why, lets show a notification
//                callNotificationManager.setupNotification()
                context.baseConfig.lastError = "CallService: $e"
            }
        }
        callNotificationManager.setupNotification(lowPriority)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        callNotificationManager.cancelNotification()
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)
        EventBus.getDefault().post(Events.RefreshCallLog)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
//            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            if (wasPrimaryCall) {
                startActivity(CallActivity.getStartIntent(this))
            }
        }

        try {
            if (baseConfig.flashForAlerts) MyCameraImpl.newInstance(this).stopSOS()
        } catch (_: Exception) { }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()

        try {
            if (baseConfig.flashForAlerts) MyCameraImpl.newInstance(this).stopSOS()
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        callNotificationManager.cancelNotification()

        try {
            if (baseConfig.flashForAlerts) MyCameraImpl.newInstance(this).stopSOS()
        } catch (_: Exception) { }
    }
}

