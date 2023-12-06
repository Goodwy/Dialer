package com.goodwy.dialer.helpers

import com.goodwy.commons.helpers.TAB_CALL_HISTORY
import com.goodwy.commons.helpers.TAB_CONTACTS
import com.goodwy.commons.helpers.TAB_FAVORITES

// shared prefs
const val SPEED_DIAL = "speed_dial"
const val REMEMBER_SIM_PREFIX = "remember_sim_"
const val GROUP_SUBSEQUENT_CALLS = "group_subsequent_calls"
const val OPEN_DIAL_PAD_AT_LAUNCH = "open_dial_pad_at_launch"
const val DISABLE_PROXIMITY_SENSOR = "disable_proximity_sensor"
const val DISABLE_SWIPE_TO_ANSWER = "disable_swipe_to_answer"
const val SHOW_TABS = "show_tabs"
const val WAS_OVERLAY_SNACKBAR_CONFIRMED = "was_overlay_snackbar_confirmed"
const val DIALPAD_VIBRATION = "dialpad_vibration"
const val DIALPAD_BEEPS = "dialpad_beeps"
const val HIDE_DIALPAD_NUMBERS = "hide_dialpad_numbers"
const val ALWAYS_SHOW_FULLSCREEN = "always_show_fullscreen" // not used SHOW_INCOMING_CALLS_FULL_SCREEN
//Goodwy
const val SHOW_INCOMING_CALLS_FULL_SCREEN = "show_incoming_calls_full_screen"
const val TRANSPARENT_CALL_SCREEN = "transparent_call_screen"
const val NUMBER_MISSED_CALLS = "number_missed_calls"
const val MISSED_CALL_NOTIFICATIONS = "missed_call_notifications"
const val HIDE_DIALPAD_LETTERS = "hide_dialpad_letters"
const val BACKGROUND_CALL_SCREEN = "background_call_screen"
const val SHOW_ALL_RECENT_IN_HISTORY = "show_all_recent_in_history"
const val DIALPAD_STYLE = "dialpad_style"
const val DIALPAD_SIZE = "dialpad_size"
const val CALL_BUTTON_PRIMARY_SIZE = "call_button_primary_size"
const val CALL_BUTTON_SECONDARY_SIZE = "call_button_secondary_size"
const val ANSWER_STYLE = "answer_style"
const val SHOW_CALLER_DESCRIPTION = "show_caller_description"
const val CURRENT_RECENT_CALL = "current_recent_call"
const val CURRENT_RECENT_CALL_ID = 0
const val SHOW_WARNING_ANONYMOUS_CALL = "show_warning_anonymous_call"
const val CALL_VIBRATION = "call_vibration"
const val CALL_START_END_VIBRATION = "call_start_end_vibration"
const val NEED_SELECT_SIM = "need_select_sim"

const val ALL_TABS_MASK = TAB_CONTACTS or TAB_FAVORITES or TAB_CALL_HISTORY

val tabsList = arrayListOf(TAB_FAVORITES, TAB_CALL_HISTORY, TAB_CONTACTS)

private const val PATH = "com.goodwy.dialer.action."
const val ACCEPT_CALL = PATH + "accept_call"
const val DECLINE_CALL = PATH + "decline_call"
const val MICROPHONE_CALL = PATH + "microphone_call"

const val DIALPAD_TONE_LENGTH_MS = 150L // The length of DTMF tones in milliseconds

const val MIN_RECENTS_THRESHOLD = 30

const val WHATSAPP = "whatsapp"
const val SIGNAL = "signal"
const val VIBER = "viber"
const val TELEGRAM = "telegram"
const val THREEMA = "threema"

// Background Call Screen
const val THEME_BACKGROUND = 0
const val BLUR_AVATAR = 1
const val AVATAR = 2
const val TRANSPARENT_BACKGROUND = 3
const val BLACK_BACKGROUND = 4

// Dialpad style
const val DIALPAD_ORIGINAL = 0
const val DIALPAD_GRID = 1
const val DIALPAD_IOS = 2
const val DIALPAD_CONCEPT = 3

// Timer
const val TIMER_ID = "timer_id"
const val TIMER_SECONDS = "timer_seconds"
const val TIMER_VIBRATE = "timer_vibrate"
const val TIMER_SOUND_URI = "timer_sound_uri"
const val TIMER_SOUND_TITLE = "timer_sound_title"
const val TIMER_CHANNEL_ID = "timer_channel_id"
const val TIMER_TITLE = "timer_title"
const val TIMER_LABEL = "timer_label"
const val TIMER_DESCRIPTION = "timer_description"
const val TIMER_RUNNING_NOTIF_ID = 10000
const val INVALID_TIMER_ID = -1
const val TIMER_MAX_REMINDER_SECS = "timer_max_reminder_secs"
const val DEFAULT_MAX_TIMER_REMINDER_SECS = 60

// Flashlight
const val MIN_BRIGHTNESS_LEVEL = 1
const val DEFAULT_BRIGHTNESS_LEVEL = -1

// Answer style
const val ANSWER_BUTTON = 0
const val ANSWER_SLIDER = 1
const val ANSWER_SLIDER_OUTLINE = 2
const val ANSWER_SLIDER_VERTICAL = 3

// Show caller description
const val SHOW_CALLER_NOTHING = 0
const val SHOW_CALLER_COMPANY = 1
const val SHOW_CALLER_NICKNAME = 2
