package com.goodwy.dialer.helpers

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CallLog.Calls
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.SimpleContact
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.extensions.getAvailableSIMCardLabels
import com.goodwy.dialer.models.RecentCall

class RecentsHelper(private val context: Context) {
    private val COMPARABLE_PHONE_NUMBER_LENGTH = 9

    @SuppressLint("MissingPermission")
    fun getRecentCalls(groupSubsequentCalls: Boolean, callback: (ArrayList<RecentCall>) -> Unit) {
        val privateCursor = context.getMyContactsCursor(false, true)?.loadInBackground()
        ensureBackgroundThread {
            if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
                callback(ArrayList())
                return@ensureBackgroundThread
            }

            SimpleContactsHelper(context).getAvailableContacts(false) { contacts ->
                val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                }

                getRecents(contacts, groupSubsequentCalls, callback)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getRecents(contacts: ArrayList<SimpleContact>, groupSubsequentCalls: Boolean, callback: (ArrayList<RecentCall>) -> Unit) {
        var recentCalls = ArrayList<RecentCall>()
        var previousRecentCallFrom = ""
        var previousStartTS = 0
        val contactsNumbersMap = HashMap<String, String>()
        val contactPhotosMap = HashMap<String, String>()

        val uri = Calls.CONTENT_URI
        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.CACHED_NAME,
            Calls.CACHED_PHOTO_URI,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            "phone_account_address"
        )

        val numberToSimIDMap = HashMap<String, Int>()
        context.getAvailableSIMCardLabels().forEach {
            numberToSimIDMap[it.phoneNumber] = it.id
        }

        val cursor = if (isRPlus()) {
            val bundle = Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(Calls._ID))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, 100)
            }

            context.contentResolver.query(uri, projection, bundle, null)
        } else {
            val sortOrder = "${Calls._ID} DESC LIMIT 100"
            context.contentResolver.query(uri, projection, null, null, sortOrder)
        }

        if (cursor?.moveToFirst() == true) {
            do {
            val id = cursor.getIntValue(Calls._ID)
            val number = cursor.getStringValue(Calls.NUMBER)
            var name = cursor.getStringValue(Calls.CACHED_NAME)
            if (name == null || name.isEmpty()) {
                name = number
            }

            if (name == number) {
                if (contactsNumbersMap.containsKey(number)) {
                    name = contactsNumbersMap[number]!!
                } else {
                    val normalizedNumber = number.normalizePhoneNumber()
                    if (normalizedNumber!!.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                        name = contacts.firstOrNull { contact ->
                            val curNumber = contact.phoneNumbers.first().normalizePhoneNumber()
                            if (curNumber!!.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                                if (curNumber.substring(curNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH) == normalizedNumber.substring(normalizedNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH)) {
                                    contactsNumbersMap[number] = contact.name
                                    return@firstOrNull true
                                }
                            }
                            false
                        }?.name ?: number
                    }
                }
            }

            if (name.isEmpty()) {
                name = context.getString(R.string.unknown)
            }

            var photoUri = cursor.getStringValue(Calls.CACHED_PHOTO_URI) ?: ""
            if (photoUri.isEmpty()) {
                 if (contactPhotosMap.containsKey(number)) {
                     photoUri = contactPhotosMap[number]!!
                 } else {
                     val contact = contacts.firstOrNull { it.doesContainPhoneNumber(number) }
                     if (contact != null) {
                         photoUri = contact.photoUri
                         contactPhotosMap[number] = contact.photoUri
                     }
                 }
            }

            var numberType : Int? = null
            var numberLabel : String? = null
                val contact = contacts.firstOrNull { it.doesContainPhoneNumber(number) }
                if (contact != null && contact.phoneNumbersInfo.filter { it.normalizedNumber == number }.firstOrNull() != null) {
                    val phoneNumberType = contact.phoneNumbersInfo.filter { it.normalizedNumber == number }.first().type
                    val phoneNumberLabel = contact.phoneNumbersInfo.filter { it.normalizedNumber == number }.first().label
                    numberType = phoneNumberType
                    numberLabel = phoneNumberLabel
                }


            val startTS = (cursor.getLongValue(Calls.DATE) / 1000L).toInt()
            if (previousStartTS == startTS) {
                continue
            } else {
                previousStartTS = startTS
            }

            val duration = cursor.getIntValue(Calls.DURATION)
            val type = cursor.getIntValue(Calls.TYPE)
            val accountAddress = cursor.getStringValue("phone_account_address")
            val simID = numberToSimIDMap[accountAddress] ?: 1
            val neighbourIDs = ArrayList<Int>()
            val recentCall = RecentCall(id, number, name, photoUri, startTS, duration, type, neighbourIDs, simID, numberType, numberLabel)

            // if we have multiple missed calls from the same number, show it just once
            if (!groupSubsequentCalls || "$number$name" != previousRecentCallFrom) {
                recentCalls.add(recentCall)
            } else {
                recentCalls.lastOrNull()?.neighbourIDs?.add(id)
            }

            previousRecentCallFrom = "$number$name"
            } while (cursor.moveToNext())
        }

        val blockedNumbers = context.getBlockedNumbers()
        recentCalls = recentCalls.filter { !context.isNumberBlocked(it.phoneNumber, blockedNumbers) }.toMutableList() as ArrayList<RecentCall>
        callback(recentCalls)
    }

    @SuppressLint("MissingPermission")
    fun removeRecentCalls(ids: ArrayList<Int>, callback: () -> Unit) {
        ensureBackgroundThread {
            val uri = Calls.CONTENT_URI
            ids.chunked(30).forEach { chunk ->
                val selection = "${Calls._ID} IN (${getQuestionMarks(chunk.size)})"
                val selectionArgs = chunk.map { it.toString() }.toTypedArray()
                context.contentResolver.delete(uri, selection, selectionArgs)
            }
            callback()
        }
    }

    @SuppressLint("MissingPermission")
    fun removeAllRecentCalls(activity: SimpleActivity, callback: () -> Unit) {
        activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
            if (it) {
                ensureBackgroundThread {
                    val uri = Calls.CONTENT_URI
                    context.contentResolver.delete(uri, null, null)
                    callback()
                }
            }
        }
    }
}
