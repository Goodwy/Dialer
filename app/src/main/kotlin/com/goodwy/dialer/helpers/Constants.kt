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
const val FAVORITES_CONTACTS_ORDER = "favorites_contacts_order"
const val FAVORITES_CUSTOM_ORDER_SELECTED = "favorites_custom_order_selected"
const val WAS_OVERLAY_SNACKBAR_CONFIRMED = "was_overlay_snackbar_confirmed"
const val DIALPAD_VIBRATION = "dialpad_vibration"
const val DIALPAD_BEEPS = "dialpad_beeps"
const val HIDE_DIALPAD_NUMBERS = "hide_dialpad_numbers"
const val ALWAYS_SHOW_FULLSCREEN = "always_show_fullscreen" // not used SHOW_INCOMING_CALLS_FULL_SCREEN
const val SHOW_INCOMING_CALLS_FULL_SCREEN = "show_incoming_calls_full_screen"
const val TRANSPARENT_CALL_SCREEN = "transparent_call_screen"
const val NUMBER_MISSED_CALLS = "number_missed_calls"
const val MISSED_CALL_NOTIFICATIONS = "missed_call_notifications"
const val SHOW_CONTACT_THUMBNAILS = "show_contact_thumbnails"
const val HIDE_DIALPAD_LETTERS = "hide_dialpad_letters"
const val BACKGROUND_CALL_SCREEN = "background_call_screen"

const val ALL_TABS_MASK = TAB_CONTACTS or TAB_FAVORITES or TAB_CALL_HISTORY

val tabsList = arrayListOf(TAB_FAVORITES, TAB_CALL_HISTORY, TAB_CONTACTS)

private const val PATH = "com.goodwy.dialer.action."
const val ACCEPT_CALL = PATH + "accept_call"
const val DECLINE_CALL = PATH + "decline_call"

const val DIALPAD_TONE_LENGTH_MS = 150L // The length of DTMF tones in milliseconds

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
