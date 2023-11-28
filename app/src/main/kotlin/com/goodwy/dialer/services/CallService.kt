package com.goodwy.dialer.services

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.CallAudioState
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.ActivityCompat
import com.goodwy.commons.helpers.isPiePlus
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.helpers.CallManager.Companion.getPhoneSize
import com.goodwy.dialer.receivers.CallActionReceiver

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

    fun sendBroadcastAccept(context: Context) {
        val intent = Intent(context, CallActionReceiver::class.java)
        intent.action = ACCEPT_CALL
        context.sendBroadcast(intent)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val phone = CallManager.getPhoneNumber()
            val isKnownContact = isKnownContact(phone ?: "")
            if (isPiePlus() && !isKnownContact) {
                sendBroadcastAccept(this)
            }
        }
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
            config.showIncomingCallsFullScreen && getPhoneSize() < 2 -> {
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
            if (call.details.disconnectCause.code == DisconnectCause.MISSED && config.missedCallNotifications) {
                getCallContact(this.applicationContext, call) { callContact ->
                    showMessageNotification(callContact)
                }
            }
            if (config.flashForAlerts) MyCameraImpl.newInstance(this).stopSOS()
        }
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

