package com.goodwy.dialer.extensions

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.*

fun String.getCountryByNumber(): String {
    return try {
        val locale = Locale.getDefault()
        val countryCode = locale.country
        val phoneUtil = PhoneNumberUtil.getInstance()
        val geocoder = PhoneNumberOfflineGeocoder.getInstance()
        val numberParse = phoneUtil.parse(this, countryCode)
        geocoder.getDescriptionForNumber(numberParse, Locale.getDefault())
    } catch (_: NumberParseException) {
        ""
    }
}

// remove the pluses, spaces and hyphens.
fun String.numberForNotes() = replace("\\s".toRegex(), "")
    .replace("\\+".toRegex(), "")
    .replace("\\(".toRegex(), "")
    .replace("\\)".toRegex(), "")
    .replace("-".toRegex(), "")

fun String.removeNumberFormatting() = replace("\\s".toRegex(), "")
    .replace("\\(".toRegex(), "")
    .replace("\\)".toRegex(), "")
    .replace("-".toRegex(), "")
    .replace("\\*".toRegex(), "")
    .replace("#".toRegex(), "")
