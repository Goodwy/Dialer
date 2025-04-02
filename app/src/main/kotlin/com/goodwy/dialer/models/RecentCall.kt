package com.goodwy.dialer.models

import android.telephony.PhoneNumberUtils
import com.goodwy.commons.extensions.normalizePhoneNumber
import com.goodwy.commons.extensions.toDayCode

/**
 * Used at displaying recent calls.
 * For contacts with multiple numbers specify the number and type
 */
@kotlinx.serialization.Serializable
data class RecentCall(
    var id: Int,
    var phoneNumber: String,
    var name: String,
    var nickname: String = "",
    var company: String = "",
    var jobPosition: String = "",
    var photoUri: String,
    val startTS: Long,
    var duration: Int,
    var type: Int,
    val simID: Int,
    var specificNumber: String,
    var specificType: String,
    val isUnknownNumber: Boolean,
    val groupedCalls: MutableList<RecentCall>? = null,
    var contactID: Int? = null,
    var features: Int? = null,
    val isVoiceMail: Boolean,
) : CallLogItem() {
    fun doesContainPhoneNumber(text: String): Boolean {
        return if (text.toIntOrNull() != null) {
            val normalizedText = text.normalizePhoneNumber()
            PhoneNumberUtils.compare(phoneNumber.normalizePhoneNumber(), normalizedText) ||
                phoneNumber.contains(text) ||
                phoneNumber.normalizePhoneNumber().contains(normalizedText) ||
                phoneNumber.contains(normalizedText)
        } else {
            false
        }
    }

    fun getDayCode() = startTS.toDayCode()

    fun isABusinessCall() = name.contains(company) && company.isNotEmpty()
}
