package com.goodwy.dialer.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.provider.CallLog.Calls
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getAvailableSIMCardLabels
import com.goodwy.dialer.models.RecentCall

class RecentsHelper(private val context: Context) {
    private val COMPARABLE_PHONE_NUMBER_LENGTH = 9
    private val QUERY_LIMIT = 200
    private val contentUri = Calls.CONTENT_URI

    fun getRecentCalls(groupSubsequentCalls: Boolean, maxSize: Int = QUERY_LIMIT, callback: (List<RecentCall>) -> Unit) {
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
                callback(ArrayList())
                return@ensureBackgroundThread
            }

            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                }

                getRecents(contacts, groupSubsequentCalls, maxSize, callback = callback)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getRecents(contacts: List<Contact>, groupSubsequentCalls: Boolean, maxSize: Int, callback: (List<RecentCall>) -> Unit) {

        val recentCalls = mutableListOf<RecentCall>()
        var previousRecentCallFrom = ""
        var previousStartTS = 0
        val contactsNumbersMap = HashMap<String, String>()
        val contactPhotosMap = HashMap<String, String>()

        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.CACHED_NAME,
            Calls.CACHED_PHOTO_URI,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.PHONE_ACCOUNT_ID
        )

        val accountIdToSimIDMap = HashMap<String, Int>()
        context.getAvailableSIMCardLabels().forEach {
            accountIdToSimIDMap[it.handle.id] = it.id
        }

        val cursor = if (isNougatPlus()) {
            // https://issuetracker.google.com/issues/175198972?pli=1#comment6
            val limitedUri = contentUri.buildUpon()
                .appendQueryParameter(Calls.LIMIT_PARAM_KEY, QUERY_LIMIT.toString())
                .build()
            val sortOrder = "${Calls.DATE} DESC" //TODO Sort call
            context.contentResolver.query(limitedUri, projection, null, null, sortOrder)
        } else {
            val sortOrder = "${Calls.DATE} DESC LIMIT $QUERY_LIMIT"
            context.contentResolver.query(contentUri, projection, null, null, sortOrder)
        }

        //not work
//        val contactsWithMultipleNumbers = contacts.filter { it.phoneNumbers.size > 1 }
//        val numbersToContactIDMap = HashMap<String, Int>()
//        contactsWithMultipleNumbers.forEach { contact ->
//            contact.phoneNumbers.forEach { phoneNumber ->
//                numbersToContactIDMap[phoneNumber.value] = contact.contactId
//                numbersToContactIDMap[phoneNumber.normalizedNumber] = contact.contactId
//            }
//        }

        cursor?.use {
            if (!cursor.moveToFirst()) {
                return@use
            }

            do {
                val id = cursor.getIntValue(Calls._ID)
                var isUnknownNumber = false
                val number = cursor.getStringValueOrNull(Calls.NUMBER)
                if (number == null || number == "-1") {
                    isUnknownNumber = true
                }

                var name = cursor.getStringValueOrNull(Calls.CACHED_NAME)
                if (name.isNullOrEmpty() || name == "-1") {
                    name = number.orEmpty()
                }

                if (name == number && !isUnknownNumber) {
                    if (contactsNumbersMap.containsKey(number)) {
                        name = contactsNumbersMap[number]!!
                    } else {
                        val normalizedNumber = number.normalizePhoneNumber()
                        if (normalizedNumber!!.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                            name = contacts.filter { it.phoneNumbers.isNotEmpty() }.firstOrNull { contact ->
                                val curNumber = contact.phoneNumbers.first().normalizedNumber
                                if (curNumber != "") {
                                    if (curNumber.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                                        if (curNumber.substring(curNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH) == normalizedNumber.substring(
                                                normalizedNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH
                                            )
                                        ) {
                                            contactsNumbersMap[number] = contact.getNameToDisplay()
                                            return@firstOrNull true
                                        }
                                    }
                                }
                                false
                            }?.name ?: number
                        }
                    }
                }

                var contact: Contact? = null
                if (number != null) contact = contacts.firstOrNull { it.doesContainPhoneNumber(number) }

                //Without this, the call history does not reflect the contact name for the contact's second and subsequent numbers
                //TODO try again to find the contact name
                if (name == number) {
                    if (contact != null) name = contact.name
                }

                if (name.isEmpty() || name == "-1") {
                    name = context.getString(R.string.unknown)
                }

                var photoUri = cursor.getStringValue(Calls.CACHED_PHOTO_URI) ?: ""
                if (photoUri.isEmpty() && !number.isNullOrEmpty()) {
                    if (contactPhotosMap.containsKey(number)) {
                        photoUri = contactPhotosMap[number]!!
                    } else {
                        if (contact != null) {
                            photoUri = contact.photoUri
                            contactPhotosMap[number] = contact.photoUri
                        }
                    }
                }

                val startTS = (cursor.getLongValue(Calls.DATE) / 1000L).toInt()
                if (previousStartTS == startTS) {
                    continue
                } else {
                    previousStartTS = startTS
                }

                val duration = cursor.getIntValue(Calls.DURATION)
                val type = cursor.getIntValue(Calls.TYPE)
                val accountId = cursor.getStringValue(Calls.PHONE_ACCOUNT_ID)
                val simID = accountIdToSimIDMap[accountId] ?: -1
                val neighbourIDs = mutableListOf<Int>()
                var specificNumber = ""
                var specificType = ""

                //not work
//                val contactIdWithMultipleNumbers = numbersToContactIDMap[number]
//                if (contactIdWithMultipleNumbers != null) {
//                    val specificPhoneNumber =
//                        contacts.firstOrNull { it.contactId == contactIdWithMultipleNumbers }?.phoneNumbers?.firstOrNull { it.value == number }
//                    if (specificPhoneNumber != null) {
//                        specificNumber = specificPhoneNumber.value
//                        specificType = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
//                    }
//                }

                var nickname = ""
                var company = ""
                var jobPosition = ""
                if (contact != null) {
                    val phoneNumbers = contact.phoneNumbers.firstOrNull { it.normalizedNumber == number }
                    if (phoneNumbers != null) {
                        specificNumber = contact.phoneNumbers.first { it.normalizedNumber == number }.value
                        specificType = context.getPhoneNumberTypeText(phoneNumbers.type, phoneNumbers.label)
                    }

                    nickname = contact.nickname
                    company = contact.organization.company
                    jobPosition = contact.organization.jobPosition
                }

                val contactID = contact?.id

                val recentCall = RecentCall(
                    id = id,
                    phoneNumber = number.orEmpty(),
                    name = name,
                    nickname = nickname,
                    company = company,
                    jobPosition = jobPosition,
                    photoUri = photoUri,
                    startTS = startTS,
                    duration = duration,
                    type = type,
                    neighbourIDs = neighbourIDs,
                    simID = simID,
                    specificNumber = specificNumber,
                    specificType = specificType,
                    isUnknownNumber = isUnknownNumber,
                    contactID = contactID
                )

                // if we have multiple missed calls from the same number, show it just once
                if (!groupSubsequentCalls || "$number$name$simID" != previousRecentCallFrom) {
                    recentCalls.add(recentCall)
                } else {
                    recentCalls.lastOrNull()?.neighbourIDs?.add(id)
                }

                previousRecentCallFrom = "$number$name$simID"
            } while (cursor.moveToNext() && recentCalls.size < maxSize)
        }

        val blockedNumbers = context.getBlockedNumbers()

        val recentResult = if (context.config.showBlockedNumbers) recentCalls else
            recentCalls.filter { !context.isNumberBlocked(it.phoneNumber, blockedNumbers) }

        callback(recentResult)
    }

    fun removeRecentCalls(ids: List<Int>, callback: () -> Unit) {
        ensureBackgroundThread {
            ids.chunked(30).forEach { chunk ->
                val selection = "${Calls._ID} IN (${getQuestionMarks(chunk.size)})"
                val selectionArgs = chunk.map { it.toString() }.toTypedArray()
                context.contentResolver.delete(contentUri, selection, selectionArgs)
            }
            callback()
        }
    }

    @SuppressLint("MissingPermission")
    fun removeAllRecentCalls(activity: SimpleActivity, callback: () -> Unit) {
        activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
            if (it) {
                ensureBackgroundThread {
                    context.contentResolver.delete(contentUri, null, null)
                    callback()
                }
            }
        }
    }

    fun restoreRecentCalls(activity: SimpleActivity, objects: List<RecentCall>, callback: () -> Unit) {
        activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
            if (it) {
                ensureBackgroundThread {
                    val values = objects
                        .sortedBy { it.startTS }
                        .map {
                            ContentValues().apply {
                                put(Calls.NUMBER, it.phoneNumber)
                                put(Calls.TYPE, it.type)
                                put(Calls.DATE, it.startTS.toLong() * 1000L)
                                put(Calls.DURATION, it.duration)
                                put(Calls.CACHED_NAME, it.name)
                            }
                        }.toTypedArray()

                    context.contentResolver.bulkInsert(contentUri, values)
                    callback()
                }
            }
        }
    }
}
