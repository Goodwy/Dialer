package com.goodwy.dialer.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.telecom.Call
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.MyContactsContentProvider
import com.goodwy.commons.helpers.PERMISSION_READ_PHONE_STATE
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.dialer.R
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.isConference
import com.goodwy.dialer.models.CallContact

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", "", ""))
        return
    }

    val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "", "")
        val handle = try {
            call?.details?.handle?.toString()
        } catch (e: NullPointerException) {
            null
        }

        if (handle == null) {
            callback(callContact)
            return@ensureBackgroundThread
        }

        val uri = Uri.decode(handle)
        if (uri.startsWith("tel:")) {
            val number = uri.substringAfter("tel:")

            @SuppressLint("MissingPermission")
            if (context.hasPermission(PERMISSION_READ_PHONE_STATE)) {
                try {
                    val telecomManager = context.telecomManager
                    telecomManager.callCapablePhoneAccounts.forEachIndexed { _, account ->
                        val phoneAccount = telecomManager.getPhoneAccount(account)
                        val voiceMailNumber = telecomManager.getVoiceMailNumber(phoneAccount.accountHandle)
                        if (voiceMailNumber == number) {
                            callContact.isVoiceMail = true
                        }
                    }
                } catch (ignored: Exception) {
                }
            }

            ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                }

                val contactsWithMultipleNumbers = contacts.filter { it.phoneNumbers.size > 1 }
                val numbersToContactIDMap = HashMap<String, Int>()
                contactsWithMultipleNumbers.forEach { contact ->
                    contact.phoneNumbers.forEach { phoneNumber ->
                        numbersToContactIDMap[phoneNumber.value] = contact.contactId
                        numbersToContactIDMap[phoneNumber.normalizedNumber] = contact.contactId
                    }
                }

                callContact.number = if (context.config.formatPhoneNumbers) {
                    number.formatPhoneNumber()
                } else {
                    number
                }

                val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                if (contact != null) {
                    callContact.name = contact.getNameToDisplay()
                    callContact.photoUri = contact.photoUri

//                    if (contact.phoneNumbers.size > 1) {
                        val specificPhoneNumber = contact.phoneNumbers.firstOrNull { it.normalizedNumber == number }
                        if (specificPhoneNumber != null) {
                            callContact.numberLabel = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                        }
//                    }

                    val showCallerDescription = context.config.showCallerDescription
                    if (showCallerDescription != SHOW_CALLER_NOTHING) {
                        if (contact.organization.company.isNotEmpty() && showCallerDescription == SHOW_CALLER_COMPANY) {
                            if (contact.organization.jobPosition.isNotEmpty()) {
                                callContact.description = contact.organization.company + " (" + contact.organization.jobPosition + ")"
                            } else {
                                callContact.description = contact.organization.company
                            }
                        }
                        if (contact.nickname.isNotEmpty() && showCallerDescription == SHOW_CALLER_NICKNAME) {
                            callContact.description = contact.nickname
                        }
                    }

                    callContact.isABusinessCall = contact.organization.company.isNotEmpty() &&  contact.getNameToDisplay().contains(contact.organization.company)
                } else {
                    callContact.name = callContact.number
                }

                callback(callContact)
            }
        }
    }
}
