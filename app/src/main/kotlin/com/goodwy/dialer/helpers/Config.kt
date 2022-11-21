package com.goodwy.dialer.helpers

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.goodwy.commons.helpers.BaseConfig
import com.goodwy.dialer.models.SpeedDial

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var speedDial: String
        get() = prefs.getString(SPEED_DIAL, "")!!
        set(speedDial) = prefs.edit().putString(SPEED_DIAL, speedDial).apply()

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

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    var favoritesContactsOrder: String
        get() = prefs.getString(FAVORITES_CONTACTS_ORDER, "")!!
        set(order) = prefs.edit().putString(FAVORITES_CONTACTS_ORDER, order).apply()

    var isCustomOrderSelected: Boolean
        get() = prefs.getBoolean(FAVORITES_CUSTOM_ORDER_SELECTED, false)
        set(selected) = prefs.edit().putBoolean(FAVORITES_CUSTOM_ORDER_SELECTED, selected).apply()

    var wasOverlaySnackbarConfirmed: Boolean
        get() = prefs.getBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, false)
        set(wasOverlaySnackbarConfirmed) = prefs.edit().putBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, wasOverlaySnackbarConfirmed).apply()

    var dialpadVibration: Boolean
        get() = prefs.getBoolean(DIALPAD_VIBRATION, true)
        set(dialpadVibration) = prefs.edit().putBoolean(DIALPAD_VIBRATION, dialpadVibration).apply()

    var dialpadBeeps: Boolean
        get() = prefs.getBoolean(DIALPAD_BEEPS, false)
        set(dialpadBeeps) = prefs.edit().putBoolean(DIALPAD_BEEPS, dialpadBeeps).apply()

    var alwaysShowFullscreen: Boolean // not used showIncomingCallsFullScreen
        get() = prefs.getBoolean(ALWAYS_SHOW_FULLSCREEN, false)
        set(alwaysShowFullscreen) = prefs.edit().putBoolean(ALWAYS_SHOW_FULLSCREEN, alwaysShowFullscreen).apply()

    //My
    var showIncomingCallsFullScreen: Boolean
        get() = prefs.getBoolean(SHOW_INCOMING_CALLS_FULL_SCREEN, false)
        set(showIncomingCallsFullScreen) = prefs.edit().putBoolean(SHOW_INCOMING_CALLS_FULL_SCREEN, showIncomingCallsFullScreen).apply()

    var transparentCallScreen: Boolean
        get() = prefs.getBoolean(TRANSPARENT_CALL_SCREEN, false)
        set(transparentCallScreen) = prefs.edit().putBoolean(TRANSPARENT_CALL_SCREEN, transparentCallScreen).apply()

    var numberMissedCalls: Int
        get() = prefs.getInt(NUMBER_MISSED_CALLS, 0)
        set(numberMissedCalls) = prefs.edit().putInt(NUMBER_MISSED_CALLS, numberMissedCalls).apply()

    var missedCallNotifications: Boolean
        get() = prefs.getBoolean(MISSED_CALL_NOTIFICATIONS, false)
        set(missedCallNotifications) = prefs.edit().putBoolean(MISSED_CALL_NOTIFICATIONS, missedCallNotifications).apply()

    var showContactThumbnails: Boolean
        get() = prefs.getBoolean(SHOW_CONTACT_THUMBNAILS, true)
        set(showContactThumbnails) = prefs.edit().putBoolean(SHOW_CONTACT_THUMBNAILS, showContactThumbnails).apply()

    var hideDialpadLetters: Boolean
        get() = prefs.getBoolean(HIDE_DIALPAD_LETTERS, false)
        set(hideDialpadLetters) = prefs.edit().putBoolean(HIDE_DIALPAD_LETTERS, hideDialpadLetters).apply()
}

