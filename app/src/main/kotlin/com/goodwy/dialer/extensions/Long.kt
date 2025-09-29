package com.goodwy.dialer.extensions

import com.goodwy.commons.extensions.getFormattedDuration
import com.goodwy.commons.extensions.toDayCodeGregorian
import kotlin.math.roundToInt

fun Long.getDayCode(): String {
    return toDayCodeGregorian("yyyy-MM-dd") // format helps with sorting in call log
}
fun Long.getFormattedDuration(forceShowHours: Boolean = false): String {
    return this.div(1000F).roundToInt().getFormattedDuration(forceShowHours)
}
