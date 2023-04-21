package com.goodwy.dialer.helpers

import android.content.Context
import android.net.Uri
import android.telecom.Call
import com.goodwy.commons.extensions.getMyContactsCursor
import com.goodwy.commons.extensions.getPhoneNumberTypeText
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.MyContactsContentProvider
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.dialer.R
import com.goodwy.dialer.extensions.isConference
import com.goodwy.dialer.models.CallContact

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", ""))
        return
    }

    val privateCursor = context.getMyContactsCursor(false, true)
    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "")
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
            ContactsHelper(context).getContacts{ contacts ->
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

                callContact.number = number
                val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                if (contact != null) {
                    callContact.name = contact.getNameToDisplay()
                    callContact.photoUri = contact.photoUri

                    if (contact.phoneNumbers.size > 1) {
                        val specificPhoneNumber = contact.phoneNumbers.firstOrNull { it.value == number }
                        if (specificPhoneNumber != null) {
                            callContact.numberLabel = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                        }
                    }
                } else {
                    callContact.name = number
                }
                callback(callContact)
            }
        }
    }
}
