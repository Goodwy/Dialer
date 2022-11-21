package com.goodwy.dialer.services

import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.extensions.getMyContactsCursor
import com.goodwy.commons.extensions.isNumberBlocked
import com.goodwy.commons.extensions.normalizePhoneNumber
import com.goodwy.commons.helpers.SimpleContactsHelper

@RequiresApi(Build.VERSION_CODES.N)
class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = Uri.decode(callDetails.handle?.toString())?.substringAfter("tel:")
        if (number != null && isNumberBlocked(number.normalizePhoneNumber())) {
            respondToCall(callDetails, isBlocked = true)
        } else if (number != null && baseConfig.blockUnknownNumbers) {
            val simpleContactsHelper = SimpleContactsHelper(this)
            val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            simpleContactsHelper.exists(number, privateCursor) { exists ->
                respondToCall(callDetails, isBlocked = !exists)
            }
        } else {
            respondToCall(callDetails, isBlocked = false)
        }
    }

    private fun respondToCall(callDetails: Call.Details, isBlocked: Boolean) {
        val response = CallResponse.Builder()
            .setDisallowCall(isBlocked)
            .setRejectCall(isBlocked)
            .setSkipCallLog(isBlocked)
            .setSkipNotification(isBlocked)
            .build()

        respondToCall(callDetails, response)
    }
}
