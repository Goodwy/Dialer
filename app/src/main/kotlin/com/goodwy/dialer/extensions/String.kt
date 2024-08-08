package com.goodwy.dialer.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import android.text.BidiFormatter
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.*

@SuppressLint("ConstantLocale")
private val countryCode = Locale.getDefault().country

fun getCountryByNumber(number: String): String {
    return try {
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
