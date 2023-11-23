package com.goodwy.dialer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.dialer.extensions.hideTimerNotification
import com.goodwy.dialer.helpers.INVALID_TIMER_ID
import com.goodwy.dialer.helpers.TIMER_ID
import com.goodwy.dialer.models.TimerEvent
import org.greenrobot.eventbus.EventBus

class HideTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getIntExtra(TIMER_ID, INVALID_TIMER_ID)
        context.hideTimerNotification(timerId)
        EventBus.getDefault().post(TimerEvent.Reset(timerId))
    }
}
