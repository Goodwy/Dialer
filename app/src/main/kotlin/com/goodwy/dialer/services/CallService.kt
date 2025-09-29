package com.goodwy.dialer.services

import android.telecom.CallAudioState
import android.telecom.Call
import android.telecom.InCallService
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.isOutgoing
import com.goodwy.dialer.extensions.powerManager
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.Events
import org.greenrobot.eventbus.EventBus

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        //val isScreenLocked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        when {
            !powerManager.isInteractive /*|| isScreenLocked*/ -> {
                try {
                    startActivity(CallActivity.getStartIntent(this))
                    callNotificationManager.setupNotification(true)
                } catch (e: Exception) {
                    // seems like startActivity can throw AndroidRuntimeException and ActivityNotFoundException, not yet sure when and why, lets show a notification
                    callNotificationManager.setupNotification()
                }
            }
            call.isOutgoing() -> {
                try {
                    startActivity(CallActivity.getStartIntent(this, needSelectSIM = call.details.accountHandle == null))
                    callNotificationManager.setupNotification(true)
                } catch (e: Exception) {
                    // seems like startActivity can throw AndroidRuntimeException and ActivityNotFoundException, not yet sure when and why, lets show a notification
                    callNotificationManager.setupNotification()
                }
            }
            config.showIncomingCallsFullScreen /*&& getPhoneSize() < 2*/ -> {
                try {
                    startActivity(CallActivity.getStartIntent(this))
                    callNotificationManager.setupNotification(true)
                } catch (e: Exception) {
                    // seems like startActivity can throw AndroidRuntimeException and ActivityNotFoundException, not yet sure when and why, lets show a notification
                    callNotificationManager.setupNotification()
                }
            }
            else -> callNotificationManager.setupNotification()
        }
        if (!call.isOutgoing() && !powerManager.isInteractive && config.flashForAlerts) MyCameraImpl.newInstance(this).toggleSOS()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        callNotificationManager.cancelNotification()
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            if (wasPrimaryCall) {
                startActivity(CallActivity.getStartIntent(this))
            }
        }
        call.details?.let {
            if (config.flashForAlerts) MyCameraImpl.newInstance(this).stopSOS()
        }

        EventBus.getDefault().post(Events.RefreshCallLog)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callNotificationManager.cancelNotification()
        if (config.flashForAlerts) MyCameraImpl.newInstance(this).stopSOS()
    }
}

