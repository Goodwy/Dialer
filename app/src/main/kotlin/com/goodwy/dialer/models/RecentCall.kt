package com.goodwy.dialer.models

import android.telephony.PhoneNumberUtils
import com.goodwy.commons.extensions.normalizePhoneNumber

data class RecentCall(
    var id: Int, var phoneNumber: String, var name: String, var photoUri: String, var startTS: Int, var duration: Int, var type: Int,
    var neighbourIDs: ArrayList<Int>, val simID: Int, var phoneNumberType: Int?, var phoneNumberLabel: String?
) {
    fun doesContainPhoneNumber(text: String): Boolean {
        val normalizedText = text.normalizePhoneNumber()
        return PhoneNumberUtils.compare(phoneNumber.normalizePhoneNumber(), normalizedText) ||
                phoneNumber.contains(text) ||
                phoneNumber.normalizePhoneNumber().contains(normalizedText) ||
                phoneNumber.contains(normalizedText)
    }
}
