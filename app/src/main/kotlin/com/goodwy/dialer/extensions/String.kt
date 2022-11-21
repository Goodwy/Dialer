package com.goodwy.dialer.extensions

import android.content.Context
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.*

fun getPhoneNumberFormat(context: Context, number: String): String {
    return try {
        val phoneUtil = PhoneNumberUtil.getInstance()
        val numberParse = phoneUtil.parse(number, context.sysLocale()!!.language)
        phoneUtil.format(numberParse, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    } catch (e: NumberParseException) {
        System.err.println("getPhoneNumberFormat() was thrown: $e")
        number
    }
}

fun getCountryByNumber(context: Context, number: String): String {
    return try {
        val phoneUtil = PhoneNumberUtil.getInstance()
        val geocoder = PhoneNumberOfflineGeocoder.getInstance()
        val numberParse = phoneUtil.parse(number, context.sysLocale()!!.language)
        geocoder.getDescriptionForNumber(numberParse, Locale.getDefault())
    } catch (e: NumberParseException) {
        System.err.println("getCountryByNumber() was thrown: $e")
        ""
    }
}

fun getPhoneNumberType(context: Context, number: String): String? {
    return try {
        val phoneUtil = PhoneNumberUtil.getInstance()
        val numberParse = phoneUtil.parse(number, context.sysLocale()!!.language)
        phoneUtil.getNumberType(numberParse).toString()
    } catch (e: NumberParseException) {
        System.err.println("getPhoneNumberType() was thrown: $e")
        null
    }
}
