package com.goodwy.dialer.services

import android.telecom.Call
import android.telecom.CallScreeningService
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.extensions.getMyContactsCursor
import com.goodwy.commons.extensions.isNumberBlocked
import com.goodwy.commons.extensions.normalizePhoneNumber
import com.goodwy.commons.helpers.BLOCKING_TYPE_REJECT
import com.goodwy.commons.helpers.BLOCKING_TYPE_SILENCE
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.isQPlus
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.models.Events
import org.greenrobot.eventbus.EventBus

class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        when {
            //To speed things up, we will not check any conditions if blocking is disabled
            !baseConfig.blockingEnabled -> {
                respondToCall(callDetails, isBlocked = false)
            }

            number != null && isNumberBlocked(number.normalizePhoneNumber()) && baseConfig.blockingEnabled -> {
                if (baseConfig.doNotBlockContactsAndRecent) {
                    val simpleContactsHelper = SimpleContactsHelper(this)
                    val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                    simpleContactsHelper.exists(number, privateCursor) { exists ->
                        if (number in config.recentOutgoingNumbers) respondToCall(callDetails, isBlocked = false)
                        else respondToCall(callDetails, isBlocked = !exists)
                    }
                } else {
                    respondToCall(callDetails, isBlocked = true)
                }
            }

            number != null && baseConfig.blockUnknownNumbers && baseConfig.blockingEnabled -> {
                val simpleContactsHelper = SimpleContactsHelper(this)
                val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                simpleContactsHelper.exists(number, privateCursor) { exists ->
                    if (number in config.recentOutgoingNumbers) respondToCall(callDetails, isBlocked = false)
                    else respondToCall(callDetails, isBlocked = !exists)
                }
            }

            number == null && baseConfig.blockHiddenNumbers && baseConfig.blockingEnabled -> {
                respondToCall(callDetails, isBlocked = true)
            }

            else -> {
                respondToCall(callDetails, isBlocked = false)
            }
        }
    }

    private fun respondToCall(callDetails: Call.Details, isBlocked: Boolean) {
        val response = if (isBlocked) {
            if (isQPlus() && baseConfig.blockingType == BLOCKING_TYPE_SILENCE) {
                CallResponse.Builder()
                    .setSilenceCall(true)
                    .build()
            } else {
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(baseConfig.blockingType == BLOCKING_TYPE_REJECT)
                    .setSkipCallLog(false) // not work https://issuetracker.google.com/issues/130081372
                    .setSkipNotification(true)
                    .build()
            }
        } else {
            CallResponse.Builder()
                .build()
        }

        respondToCall(callDetails, response)

        // setSkipCallLog() does not work on many versions of Android, so let's update the list after blocking the call
        // https://issuetracker.google.com/issues/130081372
        if (isBlocked) EventBus.getDefault().post(Events.RefreshCallLog)
    }
}
