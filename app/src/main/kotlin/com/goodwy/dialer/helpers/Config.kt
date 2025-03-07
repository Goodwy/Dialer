package com.goodwy.dialer.helpers

import android.content.ComponentName
import android.content.Context
import android.graphics.Typeface
import android.media.RingtoneManager
import android.telecom.PhoneAccountHandle
import androidx.core.content.ContextCompat
import com.goodwy.commons.extensions.getDefaultAlarmSound
import com.goodwy.commons.extensions.getDefaultAlarmTitle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.goodwy.commons.helpers.BaseConfig
import com.goodwy.dialer.extensions.getPhoneAccountHandleModel
import com.goodwy.dialer.extensions.putPhoneAccountHandle
import com.goodwy.dialer.models.CallerNote
import com.goodwy.dialer.models.RecentCall
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

    fun saveCustomSIM(number: String, handle: PhoneAccountHandle) {
        prefs.edit().putPhoneAccountHandle(REMEMBER_SIM_PREFIX + number, handle).apply()
    }

    fun getCustomSIM(number: String): PhoneAccountHandle? {
        val myPhoneAccountHandle = prefs.getPhoneAccountHandleModel(REMEMBER_SIM_PREFIX + number, null)
        return if (myPhoneAccountHandle != null) {
            val packageName = myPhoneAccountHandle.packageName
            val className = myPhoneAccountHandle.className
            val componentName = ComponentName(packageName, className)
            val id = myPhoneAccountHandle.id
            PhoneAccountHandle(componentName, id)
        } else {
            null
        }
    }

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

    var dialpadSecondaryLanguage: String?
        get() = prefs.getString(DIALPAD_SECONDARY_LANGUAGE, LANGUAGE_SYSTEM)
        set(dialpadSecondaryLanguage) = prefs.edit().putString(DIALPAD_SECONDARY_LANGUAGE, dialpadSecondaryLanguage).apply()

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

    var missedCallNotifications: Boolean
        get() = prefs.getBoolean(MISSED_CALL_NOTIFICATIONS, false)
        set(missedCallNotifications) = prefs.edit().putBoolean(MISSED_CALL_NOTIFICATIONS, missedCallNotifications).apply()

    var hideDialpadLetters: Boolean
        get() = prefs.getBoolean(HIDE_DIALPAD_LETTERS, false)
        set(hideDialpadLetters) = prefs.edit().putBoolean(HIDE_DIALPAD_LETTERS, hideDialpadLetters).apply()

    var backgroundCallScreen: Int
        get() = prefs.getInt(BACKGROUND_CALL_SCREEN, BLUR_AVATAR)
        set(backgroundCallScreen) = prefs.edit().putInt(BACKGROUND_CALL_SCREEN, backgroundCallScreen).apply()

    var showAllRecentInHistory: Boolean
        get() = prefs.getBoolean(SHOW_ALL_RECENT_IN_HISTORY, true)
        set(showAllRecentInHistory) = prefs.edit().putBoolean(SHOW_ALL_RECENT_IN_HISTORY, showAllRecentInHistory).apply()

    var dialpadStyle: Int
        get() = prefs.getInt(DIALPAD_STYLE, DIALPAD_ORIGINAL)
        set(dialpadStyle) = prefs.edit().putInt(DIALPAD_STYLE, dialpadStyle).apply()

    var dialpadSize: Int
        get() = prefs.getInt(DIALPAD_SIZE, 100)
        set(dialpadStyle) = prefs.edit().putInt(DIALPAD_SIZE, dialpadStyle).apply()

    var callButtonPrimarySize: Int
        get() = prefs.getInt(CALL_BUTTON_PRIMARY_SIZE, 100)
        set(callButtonPrimarySize) = prefs.edit().putInt(CALL_BUTTON_PRIMARY_SIZE, callButtonPrimarySize).apply()

    var callButtonSecondarySize: Int
        get() = prefs.getInt(CALL_BUTTON_SECONDARY_SIZE, 100)
        set(callButtonSecondarySize) = prefs.edit().putInt(CALL_BUTTON_SECONDARY_SIZE, callButtonSecondarySize).apply()

    var answerStyle: Int
        get() = prefs.getInt(ANSWER_STYLE, ANSWER_BUTTON)
        set(answerStyle) = prefs.edit().putInt(ANSWER_STYLE, answerStyle).apply()

    var showCallerDescription: Int
        get() = prefs.getInt(SHOW_CALLER_DESCRIPTION, SHOW_CALLER_COMPANY)
        set(answerStyle) = prefs.edit().putInt(SHOW_CALLER_DESCRIPTION, answerStyle).apply()

    var showWarningAnonymousCall: Boolean
        get() = prefs.getBoolean(SHOW_WARNING_ANONYMOUS_CALL, true)
        set(showWarningAnonymousCall) = prefs.edit().putBoolean(SHOW_WARNING_ANONYMOUS_CALL, showWarningAnonymousCall).apply()

    var callVibration: Boolean
        get() = prefs.getBoolean(CALL_VIBRATION, true)
        set(callVibration) = prefs.edit().putBoolean(CALL_VIBRATION, callVibration).apply()

    var callStartEndVibration: Boolean
        get() = prefs.getBoolean(CALL_START_END_VIBRATION, true)
        set(callStartEndVibration) = prefs.edit().putBoolean(CALL_START_END_VIBRATION, callStartEndVibration).apply()

    var toneVolume: Int
        get() = prefs.getInt(TONE_VOLUME, 80)
        set(toneVolume) = prefs.edit().putInt(TONE_VOLUME, toneVolume).apply()

    var groupAllCalls: Boolean
        get() = prefs.getBoolean(GROUP_ALL_CALLS, false)
        set(groupAllCalls) = prefs.edit().putBoolean(GROUP_ALL_CALLS, groupAllCalls).apply()

    var showRecentCallsOnDialpad: Boolean
        get() = prefs.getBoolean(SHOW_RECENT_CALLS_ON_DIALPAD, false)
        set(groupCallsByDate) = prefs.edit().putBoolean(SHOW_RECENT_CALLS_ON_DIALPAD, groupCallsByDate).apply()

    var blockCallFromAnotherApp: Boolean
        get() = prefs.getBoolean(BLOCK_CALL_FROM_ANOTHER_APP, false)
        set(blockCallFromAnotherApp) = prefs.edit().putBoolean(BLOCK_CALL_FROM_ANOTHER_APP, blockCallFromAnotherApp).apply()

    var needUpdateRecents: Boolean
        get() = prefs.getBoolean(NEED_UPDATE_RECENTS, false)
        set(needUpdateRecents) = prefs.edit().putBoolean(NEED_UPDATE_RECENTS, needUpdateRecents).apply()

    var recentCallsCache: String
        get() = prefs.getString(RECENT_CALL, "")!!
        set(recentCallsCache) = prefs.edit().putString(RECENT_CALL, recentCallsCache).apply()

    fun parseRecentCallsCache(): ArrayList<RecentCall> {
        val listType = object : TypeToken<List<RecentCall>>() {}.type
        return Gson().fromJson<ArrayList<RecentCall>>(recentCallsCache, listType) ?: ArrayList(1)
    }

    var queryLimitRecent: Int
        get() = prefs.getInt(QUERY_LIMIT_RECENT, QUERY_LIMIT_SMALL_VALUE)
        set(queryLimitRecent) = prefs.edit().putInt(QUERY_LIMIT_RECENT, queryLimitRecent).apply()

    var callButtonStyle: Int
        get() = prefs.getInt(CALL_BUTTON_STYLE, IOS16)
        set(callButtonStyle) = prefs.edit().putInt(CALL_BUTTON_STYLE, callButtonStyle).apply()

    var quickAnswers: ArrayList<String>
        get(): ArrayList<String> {
            val defaultList = arrayListOf(
                ContextCompat.getString(context, com.goodwy.dialer.R.string.message_call_later),
                ContextCompat.getString(context, com.goodwy.dialer.R.string.message_on_my_way),
                ContextCompat.getString(context, com.goodwy.dialer.R.string.message_cant_talk_right_now)
            )
            return ArrayList(prefs.getString(QUICK_ANSWERS, null)?.lines()?.map { it } ?: defaultList)
        }
        set(quickAnswers) = prefs.edit().putString(QUICK_ANSWERS, quickAnswers.joinToString(separator = "\n")).apply()

    var callUsingSameSim: Boolean
        get() = prefs.getBoolean(CALL_USING_SAME_SIM, false)
        set(callUsingSameSim) = prefs.edit().putBoolean(CALL_USING_SAME_SIM, callUsingSameSim).apply()

    var showVoicemailIcon: Boolean
        get() = prefs.getBoolean(SHOW_VOICEMAIL_ICON, false)
        set(showVoicemailIcon) = prefs.edit().putBoolean(SHOW_VOICEMAIL_ICON, showVoicemailIcon).apply()

    var simDialogStyle: Int
        get() = prefs.getInt(SIM_DIALOG_STYLE, SIM_DIALOG_STYLE_LIST)
        set(simDialogStyle) = prefs.edit().putInt(SIM_DIALOG_STYLE, simDialogStyle).apply()

    var dialpadSecondaryTypeface: Int
        get() = prefs.getInt(DIALPAD_SECONDARY_TYPEFACE, Typeface.NORMAL)
        set(dialpadSecondaryTypeface) = prefs.edit().putInt(DIALPAD_SECONDARY_TYPEFACE, dialpadSecondaryTypeface).apply()

    var dialpadHashtagLongClick: Int
        get() = prefs.getInt(DIALPAD_HASHTAG_LONG_CLICK, DIALPAD_LONG_CLICK_WAIT)
        set(dialpadHashtagLongClick) = prefs.edit().putInt(DIALPAD_HASHTAG_LONG_CLICK, dialpadHashtagLongClick).apply()

    var dialpadClearWhenStartCall: Boolean
        get() = prefs.getBoolean(DIALPAD_CLEAR_WHEN_START_CALL, false)
        set(dialpadClearWhenStartCall) = prefs.edit().putBoolean(DIALPAD_CLEAR_WHEN_START_CALL, dialpadClearWhenStartCall).apply()

    var callerNotes: String
        get() = prefs.getString(CALLER_NOTES, "")!!
        set(callerNotes) = prefs.edit().putString(CALLER_NOTES, callerNotes).apply()

    fun parseCallerNotes(): ArrayList<CallerNote> {
        val notesType = object : TypeToken<List<CallerNote>>() {}.type
        return Gson().fromJson<ArrayList<CallerNote>>(callerNotes, notesType) ?: ArrayList(1)
    }

    var backPressedEndCall: Boolean
        get() = prefs.getBoolean(BACK_PRESSED_END_CALL, false)
        set(backPressedEndCall) = prefs.edit().putBoolean(BACK_PRESSED_END_CALL, backPressedEndCall).apply()

    var callBlockButton: Boolean
        get() = prefs.getBoolean(CALL_BLOCK_BUTTON, false)
        set(callBlockButton) = prefs.edit().putBoolean(CALL_BLOCK_BUTTON, callBlockButton).apply()

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

    //Swipe
    var swipeRightAction: Int
        get() = prefs.getInt(SWIPE_RIGHT_ACTION, SWIPE_ACTION_MESSAGE)
        set(swipeRightAction) = prefs.edit().putInt(SWIPE_RIGHT_ACTION, swipeRightAction).apply()

    var swipeLeftAction: Int
        get() = prefs.getInt(SWIPE_LEFT_ACTION, SWIPE_ACTION_DELETE)
        set(swipeLeftAction) = prefs.edit().putInt(SWIPE_LEFT_ACTION, swipeLeftAction).apply()

    var swipeVibration: Boolean
        get() = prefs.getBoolean(SWIPE_VIBRATION, true)
        set(swipeVibration) = prefs.edit().putBoolean(SWIPE_VIBRATION, swipeVibration).apply()

    var swipeRipple: Boolean
        get() = prefs.getBoolean(SWIPE_RIPPLE, false)
        set(swipeRipple) = prefs.edit().putBoolean(SWIPE_RIPPLE, swipeRipple).apply()
}

