package com.goodwy.dialer.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.*

@SuppressLint("ConstantLocale")
private val countryCode = Locale.getDefault().country

fun getPhoneNumberFormat(context: Context, number: String): String {
    return try {
        val phoneUtil = PhoneNumberUtil.getInstance()
        //val nameCode = number.getCountryIsoCode(context)
        val numberParse = phoneUtil.parse(number, countryCode) //context.sysLocale()!!.language //nameCode
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
        //val nameCode = number.getCountryIsoCode(context)
        val numberParse = phoneUtil.parse(number, countryCode) //context.sysLocale()!!.language
        geocoder.getDescriptionForNumber(numberParse, Locale.getDefault())
    } catch (e: NumberParseException) {
        System.err.println("getCountryByNumber() was thrown: $e")
        ""
    }
}

fun getPhoneNumberType(context: Context, number: String): String? {
    return try {
        val phoneUtil = PhoneNumberUtil.getInstance()
        //val nameCode = number.getCountryIsoCode(context)
        val numberParse = phoneUtil.parse(number, countryCode) //context.sysLocale()!!.language
        phoneUtil.getNumberType(numberParse).toString()
    } catch (e: NumberParseException) {
        System.err.println("getPhoneNumberType() was thrown: $e")
        null
    }
}

/*fun String.getCountryIsoCode(context: Context?): String? {
    val ctx = context ?: return null

    val validateNumber = if (this.startsWith("+")) this else {
        return  (ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.networkCountryIso?.uppercase()
    }

    val phoneUtil = PhoneNumberUtil.getInstance()
    val phoneNumber = try {
        phoneUtil.parse(validateNumber, null)
    } catch (e: NumberParseException) {
        System.err.println("getCountryIsoCode() was thrown: $e")
        null
    } ?: return null

    return phoneUtil.getRegionCodeForCountryCode(phoneNumber.countryCode)
}*/
