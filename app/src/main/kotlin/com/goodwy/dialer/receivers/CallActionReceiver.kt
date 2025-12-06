package com.goodwy.dialer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.extensions.audioManager
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.helpers.ACCEPT_CALL
import com.goodwy.dialer.helpers.CallManager
import com.goodwy.dialer.helpers.CallNotificationManager
import com.goodwy.dialer.helpers.DECLINE_CALL
import com.goodwy.dialer.helpers.MICROPHONE_CALL
import com.goodwy.dialer.helpers.SPEAKER_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE_CALL -> CallManager.reject()
            ACCEPT_CALL -> {
                if (!context.config.keepCallsInPopUp) context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
                if (context.config.keepCallsInPopUp) CallManager.toggleSpeakerRoute(true)
            }

            MICROPHONE_CALL -> {
//                val isMicrophoneMute = context.audioManager.isMicrophoneMute
//                CallManager.inCallService?.setMuted(!isMicrophoneMute)

                val audioManager = context.audioManager
                audioManager.isMicrophoneMute = !audioManager.isMicrophoneMute
                CallNotificationManager(context).updateNotification()
            }

            SPEAKER_CALL -> {
//                val callManager = CallManager
//                val currentRoute = callManager.getCallAudioRoute()
//                val newRoute = if (currentRoute == AudioRoute.SPEAKER) {
//                    CallAudioState.ROUTE_WIRED_OR_EARPIECE
//                } else {
//                    CallAudioState.ROUTE_SPEAKER
//                }
//                callManager.setAudioRoute(newRoute)
//                CallNotificationManager(context).updateNotification()

                CallManager.toggleSpeakerRoute()
                CallNotificationManager(context).updateNotification()
            }
        }
    }
}
