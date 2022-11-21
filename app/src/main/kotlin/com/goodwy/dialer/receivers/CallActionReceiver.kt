package com.goodwy.dialer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.dialer.activities.CallActivity
import com.goodwy.dialer.helpers.ACCEPT_CALL
import com.goodwy.dialer.helpers.CallManager
import com.goodwy.dialer.helpers.DECLINE_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> {
                context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
            }
            DECLINE_CALL -> CallManager.reject()
        }
    }
}
