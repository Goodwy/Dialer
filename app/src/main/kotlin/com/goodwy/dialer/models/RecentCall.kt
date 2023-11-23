package com.goodwy.dialer.models

import android.telephony.PhoneNumberUtils
import com.goodwy.commons.extensions.normalizePhoneNumber

/**
 * Used at displaying recent calls.
 * For contacts with multiple numbers specify the number and type
 */
@kotlinx.serialization.Serializable
data class RecentCall(
    var id: Int,
    var phoneNumber: String,
    var name: String,
    var photoUri: String,
    var startTS: Int,
    var duration: Int,
    var type: Int,
    val neighbourIDs: MutableList<Int>,
    val simID: Int,
    var specificNumber: String,
    var specificType: String,
    val isUnknownNumber: Boolean,
) {
    fun doesContainPhoneNumber(text: String): Boolean {
        val normalizedText = text.normalizePhoneNumber()
        return PhoneNumberUtils.compare(phoneNumber.normalizePhoneNumber(), normalizedText) ||
                phoneNumber.contains(text) ||
                phoneNumber.normalizePhoneNumber().contains(normalizedText) ||
                phoneNumber.contains(normalizedText)
    }
}
