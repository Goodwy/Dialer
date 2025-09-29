package com.goodwy.dialer.extensions

import android.text.BidiFormatter
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.*

fun getCountryByNumber(number: String): String {
    return try {
        val locale = Locale.getDefault()
        val countryCode = locale.country
        val phoneUtil = PhoneNumberUtil.getInstance()
        val geocoder = PhoneNumberOfflineGeocoder.getInstance()
        val numberParse = phoneUtil.parse(number, countryCode)
        geocoder.getDescriptionForNumber(numberParse, Locale.getDefault())
    } catch (e: NumberParseException) {
        System.err.println("getCountryByNumber() was thrown: $e")
        ""
    }
}

//This converts the string to RTL and left-aligns it if there is at least one RTL-language character in the string, and returns to LTR otherwise.
fun formatterUnicodeWrap(text: String): String {
    return BidiFormatter.getInstance().unicodeWrap(text)
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
