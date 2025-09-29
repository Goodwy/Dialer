package com.goodwy.dialer

import android.app.NotificationManager
import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.goodwy.commons.RightApp
import com.goodwy.commons.extensions.isRuStoreInstalled
import com.goodwy.commons.extensions.showErrorToast
import com.goodwy.commons.helpers.rustore.RuStoreModule
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.models.TimerEvent
import com.goodwy.dialer.models.TimerState
import com.goodwy.dialer.services.TimerStopService
import com.goodwy.dialer.services.startTimerService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.TimeUnit

class App : RightApp(), LifecycleObserver {

    private var countDownTimers = mutableMapOf<Int, CountDownTimer>()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        EventBus.getDefault().register(this)
        if (isRuStoreInstalled()) RuStoreModule.install(this, "309929407") //TODO rustore
    }

    override fun onTerminate() {
        EventBus.getDefault().unregister(this)
        super.onTerminate()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun onAppBackgrounded() {
        timerHelper.getTimers { timers ->
            if (timers.any { it.state is TimerState.Running }) {
                startTimerService(this)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun onAppForegrounded() {
        EventBus.getDefault().post(TimerStopService)
        timerHelper.getTimers { timers ->
            val runningTimers = timers.filter { it.state is TimerState.Running }
            runningTimers.forEach { timer ->
                if (countDownTimers[timer.id] == null) {
                    EventBus.getDefault().post(TimerEvent.Start(timer.id!!, (timer.state as TimerState.Running).tick))
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Reset) {
        updateTimerState(event.timerId, TimerState.Idle)
        countDownTimers[event.timerId]?.cancel()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Restart) {
        timerHelper.getTimer(event.timerId) { timer ->
            val duration = TimeUnit.SECONDS.toMillis(timer.seconds.toLong())
            updateTimerState(event.timerId, TimerState.Running(duration, duration))
        }
        countDownTimers[event.timerId]?.cancel()
        countDownTimers[event.timerId]?.start()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Delete) {
        countDownTimers[event.timerId]?.cancel()
        timerHelper.deleteTimer(event.timerId) {
            EventBus.getDefault().post(TimerEvent.Refresh)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Start) {
        val countDownTimer = object : CountDownTimer(event.duration, 1000) {
            override fun onTick(tick: Long) {
                updateTimerState(event.timerId, TimerState.Running(event.duration, tick))
            }

            override fun onFinish() {
                EventBus.getDefault().post(TimerEvent.Finish(event.timerId, event.duration))
                EventBus.getDefault().post(TimerStopService)
            }
        }.start()
        countDownTimers[event.timerId] = countDownTimer
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Finish) {
        timerHelper.getTimer(event.timerId) { timer ->
            val pendingIntent = getOpenTimerTabIntent(event.timerId)
            val notification = getTimerNotification(timer, pendingIntent, false)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            try {
                notificationManager.notify(event.timerId, notification)
            } catch (e: Exception) {
                showErrorToast(e)
            }

            updateTimerState(event.timerId, TimerState.Finished)
            Handler(Looper.getMainLooper()).postDelayed({
                hideNotification(event.timerId)
            }, config.timerMaxReminderSecs * 1000L)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Pause) {
        timerHelper.getTimer(event.timerId) { timer ->
            updateTimerState(event.timerId, TimerState.Paused(event.duration, (timer.state as TimerState.Running).tick))
            countDownTimers[event.timerId]?.cancel()
        }
    }

    private fun updateTimerState(timerId: Int, state: TimerState) {
        timerHelper.getTimer(timerId) { timer ->
            val newTimer = timer.copy(state = state)
            if (newTimer.oneShot && state is TimerState.Idle) {
                timerHelper.deleteTimer(newTimer.id!!) {
                    EventBus.getDefault().post(TimerEvent.Refresh)
                }
            } else {
                timerHelper.insertOrUpdateTimer(newTimer) {
                    EventBus.getDefault().post(TimerEvent.Refresh)
                }
            }
        }
    }
}
