package com.goodwy.dialer.helpers

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.goodwy.commons.extensions.getDefaultAlarmSound
import com.goodwy.commons.extensions.getDefaultAlarmTitle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.goodwy.commons.helpers.BaseConfig
import com.goodwy.dialer.models.SpeedDial

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    fun getSpeedDialValues(): ArrayList<SpeedDial> {
        val speedDialType = object : TypeToken<List<SpeedDial>>() {}.type
        val speedDialValues = Gson().fromJson<ArrayList<SpeedDial>>(speedDial, speedDialType) ?: ArrayList(1)

        for (i in 1..9) {
            val speedDial = SpeedDial(i, "", "")
            if (speedDialValues.firstOrNull { it.id == i } == null) {
                speedDialValues.add(speedDial)
            }
        }

        return speedDialValues
    }

    fun saveCustomSIM(number: String, SIMlabel: String) {
        prefs.edit().putString(REMEMBER_SIM_PREFIX + number, Uri.encode(SIMlabel)).apply()
    }

    fun getCustomSIM(number: String) = prefs.getString(REMEMBER_SIM_PREFIX + number, "")

    fun removeCustomSIM(number: String) {
        prefs.edit().remove(REMEMBER_SIM_PREFIX + number).apply()
    }
    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    var groupSubsequentCalls: Boolean
        get() = prefs.getBoolean(GROUP_SUBSEQUENT_CALLS, true)
        set(groupSubsequentCalls) = prefs.edit().putBoolean(GROUP_SUBSEQUENT_CALLS, groupSubsequentCalls).apply()

    var openDialPadAtLaunch: Boolean
        get() = prefs.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH, false)
        set(openDialPad) = prefs.edit().putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, openDialPad).apply()

    var disableProximitySensor: Boolean
        get() = prefs.getBoolean(DISABLE_PROXIMITY_SENSOR, false)
        set(disableProximitySensor) = prefs.edit().putBoolean(DISABLE_PROXIMITY_SENSOR, disableProximitySensor).apply()

    var disableSwipeToAnswer: Boolean
        get() = prefs.getBoolean(DISABLE_SWIPE_TO_ANSWER, true)
        set(disableSwipeToAnswer) = prefs.edit().putBoolean(DISABLE_SWIPE_TO_ANSWER, disableSwipeToAnswer).apply()

    var wasOverlaySnackbarConfirmed: Boolean
        get() = prefs.getBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, false)
        set(wasOverlaySnackbarConfirmed) = prefs.edit().putBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, wasOverlaySnackbarConfirmed).apply()

    var dialpadVibration: Boolean
        get() = prefs.getBoolean(DIALPAD_VIBRATION, true)
        set(dialpadVibration) = prefs.edit().putBoolean(DIALPAD_VIBRATION, dialpadVibration).apply()

    var hideDialpadNumbers: Boolean
        get() = prefs.getBoolean(HIDE_DIALPAD_NUMBERS, false)
        set(hideDialpadNumbers) = prefs.edit().putBoolean(HIDE_DIALPAD_NUMBERS, hideDialpadNumbers).apply()

    var dialpadBeeps: Boolean
        get() = prefs.getBoolean(DIALPAD_BEEPS, false)
        set(dialpadBeeps) = prefs.edit().putBoolean(DIALPAD_BEEPS, dialpadBeeps).apply()

    var alwaysShowFullscreen: Boolean // not used showIncomingCallsFullScreen
        get() = prefs.getBoolean(ALWAYS_SHOW_FULLSCREEN, false)
        set(alwaysShowFullscreen) = prefs.edit().putBoolean(ALWAYS_SHOW_FULLSCREEN, alwaysShowFullscreen).apply()

    //Goodwy
    var showIncomingCallsFullScreen: Boolean
        get() = prefs.getBoolean(SHOW_INCOMING_CALLS_FULL_SCREEN, false)
        set(showIncomingCallsFullScreen) = prefs.edit().putBoolean(SHOW_INCOMING_CALLS_FULL_SCREEN, showIncomingCallsFullScreen).apply()

    var transparentCallScreen: Boolean  //not used
        get() = prefs.getBoolean(TRANSPARENT_CALL_SCREEN, false)
        set(transparentCallScreen) = prefs.edit().putBoolean(TRANSPARENT_CALL_SCREEN, transparentCallScreen).apply()

    var numberMissedCalls: Int
        get() = prefs.getInt(NUMBER_MISSED_CALLS, 0)
        set(numberMissedCalls) = prefs.edit().putInt(NUMBER_MISSED_CALLS, numberMissedCalls).apply()

    var missedCallNotifications: Boolean
        get() = prefs.getBoolean(MISSED_CALL_NOTIFICATIONS, false)
        set(missedCallNotifications) = prefs.edit().putBoolean(MISSED_CALL_NOTIFICATIONS, missedCallNotifications).apply()

    var hideDialpadLetters: Boolean
        get() = prefs.getBoolean(HIDE_DIALPAD_LETTERS, false)
        set(hideDialpadLetters) = prefs.edit().putBoolean(HIDE_DIALPAD_LETTERS, hideDialpadLetters).apply()

    var backgroundCallScreen: Int
        get() = prefs.getInt(BACKGROUND_CALL_SCREEN, BLUR_AVATAR)
        set(backgroundCallScreen) = prefs.edit().putInt(BACKGROUND_CALL_SCREEN, backgroundCallScreen).apply()

    //Timer
    var timerSeconds: Int
        get() = prefs.getInt(TIMER_SECONDS, 300)
        set(lastTimerSeconds) = prefs.edit().putInt(TIMER_SECONDS, lastTimerSeconds).apply()

    var timerVibrate: Boolean
        get() = prefs.getBoolean(TIMER_VIBRATE, false)
        set(timerVibrate) = prefs.edit().putBoolean(TIMER_VIBRATE, timerVibrate).apply()

    var timerSoundUri: String
        get() = prefs.getString(TIMER_SOUND_URI, context.getDefaultAlarmSound(RingtoneManager.TYPE_ALARM).uri)!!
        set(timerSoundUri) = prefs.edit().putString(TIMER_SOUND_URI, timerSoundUri).apply()

    var timerSoundTitle: String
        get() = prefs.getString(TIMER_SOUND_TITLE, context.getDefaultAlarmTitle(RingtoneManager.TYPE_ALARM))!!
        set(timerSoundTitle) = prefs.edit().putString(TIMER_SOUND_TITLE, timerSoundTitle).apply()

    var timerTitle: String?
        get() = prefs.getString(TIMER_TITLE, null)
        set(label) = prefs.edit().putString(TIMER_TITLE, label).apply()

    var timerLabel: String?
        get() = prefs.getString(TIMER_LABEL, null)
        set(label) = prefs.edit().putString(TIMER_LABEL, label).apply()

    var timerDescription: String?
        get() = prefs.getString(TIMER_DESCRIPTION, null)
        set(label) = prefs.edit().putString(TIMER_DESCRIPTION, label).apply()

    var timerChannelId: String?
        get() = prefs.getString(TIMER_CHANNEL_ID, null)
        set(id) = prefs.edit().putString(TIMER_CHANNEL_ID, id).apply()

    var timerMaxReminderSecs: Int
        get() = prefs.getInt(TIMER_MAX_REMINDER_SECS, DEFAULT_MAX_TIMER_REMINDER_SECS)
        set(timerMaxReminderSecs) = prefs.edit().putInt(TIMER_MAX_REMINDER_SECS, timerMaxReminderSecs).apply()
}

