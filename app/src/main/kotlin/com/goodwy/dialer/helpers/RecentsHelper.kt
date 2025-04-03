package com.goodwy.dialer.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteFullException
import android.net.Uri
import android.os.Build
import android.provider.CallLog.Calls
import android.telephony.PhoneNumberUtils
import android.text.TextUtils
import android.util.Log
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getAvailableSIMCardLabels
import com.goodwy.dialer.models.RecentCall
import java.util.Locale

class RecentsHelper(private val context: Context) {
    companion object {
//        private const val COMPARABLE_PHONE_NUMBER_LENGTH = 9
        const val QUERY_LIMIT = 100
    }

    private val contentUri = Calls.CONTENT_URI
    private var queryLimit = QUERY_LIMIT

    fun getRecentCalls(
        previousRecents: List<RecentCall> = ArrayList(),
        queryLimit: Int = QUERY_LIMIT,
        isDialpad: Boolean = false,
        updateCallsCache: Boolean = false,
        callback: (List<RecentCall>) -> Unit,
    ) {
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            callback(ArrayList())
            return
        }

        ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            ensureBackgroundThread {
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                }

                this.queryLimit = queryLimit
                //Do not use the current list if the recent ones have been changed in another activity
                val needUpdateRecents = context.config.needUpdateRecents
                val previousRecentsOrEmpty = if (needUpdateRecents) ArrayList() else previousRecents
                if (needUpdateRecents && !isDialpad) context.config.needUpdateRecents = false
                val recentCalls = if (previousRecentsOrEmpty.isNotEmpty()) {
                    val previousRecentCalls = previousRecentsOrEmpty.flatMap {
                        it.groupedCalls ?: listOf(it)
                    }

                    val newerRecents = getRecents(
                        contacts = contacts,
                        selection = "${Calls.DATE} > ?",
                        selectionParams = arrayOf("${previousRecentCalls.first().startTS}"),
                        updateCallsCache = updateCallsCache
                    )

                    val olderRecents = getRecents(
                        contacts = contacts,
                        selection = "${Calls.DATE} < ?",
                        selectionParams = arrayOf("${previousRecentCalls.last().startTS}"),
                        updateCallsCache = updateCallsCache
                    )

                    newerRecents + previousRecentCalls + olderRecents
                } else {
                    getRecents(contacts, updateCallsCache = updateCallsCache)
                }

                callback(recentCalls)
            }
        }
    }

    fun getGroupedRecentCalls(
        previousRecents: List<RecentCall> = ArrayList(),
        queryLimit: Int = QUERY_LIMIT,
        isDialpad: Boolean = false,
        callback: (List<RecentCall>) -> Unit,
    ) {
        getRecentCalls(previousRecents, queryLimit, isDialpad) { recentCalls ->
            callback(
                groupSubsequentCalls(calls = recentCalls)
            )
        }
    }

    private fun shouldGroupCalls(callA: RecentCall, callB: RecentCall): Boolean {
        if (
            callA.simID != callB.simID
            || (callA.name != callB.name && callA.name != callA.phoneNumber && callB.name != callB.phoneNumber)
            || callA.getDayCode() != callB.getDayCode()
        ) {
            return false
        }

        @Suppress("DEPRECATION")
        return PhoneNumberUtils.compare(callA.phoneNumber, callB.phoneNumber)
    }

    private fun groupSubsequentCalls(calls: List<RecentCall>): List<RecentCall> {
        val result = mutableListOf<RecentCall>()
        if (calls.isEmpty()) return result

        var currentCall = calls[0]
        for (i in 1 until calls.size) {
            val nextCall = calls[i]
            if (shouldGroupCalls(currentCall, nextCall)) {
                if (currentCall.groupedCalls.isNullOrEmpty()) {
                    currentCall = currentCall.copy(groupedCalls = mutableListOf(currentCall))
                }

                currentCall.groupedCalls?.add(nextCall)
            } else {
                result += currentCall
                currentCall = nextCall
            }
        }

        result.add(currentCall)
        return result
    }

    @SuppressLint("NewApi")
    private fun getRecents(
        contacts: List<Contact>,
        selection: String? = null,
        selectionParams: Array<String>? = null,
        updateCallsCache: Boolean,
    ): List<RecentCall> {
        val recentCalls = mutableListOf<RecentCall>()
        var previousStartTS = 0L
//        val contactsNumbersMap = HashMap<String, String>()
        val contactPhotosMap = HashMap<String, String>()
        val contactsMap = HashMap<String, Contact>()

        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.CACHED_NAME,
            Calls.CACHED_PHOTO_URI,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.PHONE_ACCOUNT_ID,
            Calls.FEATURES,
            Calls.COUNTRY_ISO
        )

        val accountIdToSimIDMap = HashMap<String, Int>()
        context.getAvailableSIMCardLabels().forEach {
            accountIdToSimIDMap[it.handle.id] = it.id
        }
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
        val isHuawei = manufacturer.contains(Regex(pattern = "huawei|honor"))

        val cursor = if (isNougatPlus()) {
            // https://issuetracker.google.com/issues/175198972?pli=1#comment6
            val limitedUri = contentUri.buildUpon()
                .appendQueryParameter(Calls.LIMIT_PARAM_KEY, queryLimit.toString())
                .build()
            val sortOrder = "${Calls.DATE} DESC" //TODO Sort call
            context.contentResolver.query(limitedUri, projection, selection, selectionParams, sortOrder)
        } else {
            val sortOrder = "${Calls.DATE} DESC LIMIT $queryLimit"
            context.contentResolver.query(contentUri, projection, selection, selectionParams, sortOrder)
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

//                if (name == number && !isUnknownNumber) {
//                    if (contactsNumbersMap.containsKey(number)) {
//                        name = contactsNumbersMap[number]!!
//                    } else {
//                        val normalizedNumber = number.normalizePhoneNumber()
//                        if (normalizedNumber!!.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
//                            name = contacts.filter { it.phoneNumbers.isNotEmpty() }.firstOrNull { contact ->
//                                val curNumber = contact.phoneNumbers.first().normalizedNumber
//                                if (curNumber != "") {
//                                    if (curNumber.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
//                                        if (curNumber.substring(curNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH) == normalizedNumber.substring(
//                                                normalizedNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH
//                                            )
//                                        ) {
//                                            contactsNumbersMap[number] = contact.getNameToDisplay()
//                                            return@firstOrNull true
//                                        }
//                                    }
//                                }
//                                false
//                            }?.name ?: number
//                        }
//                    }
//                }

                var contact: Contact? = null
                if (number != null) {
                    if (contactsMap.containsKey(number)) {
                        contact = contactsMap[number]!!
                    } else {
                        contact = contacts.firstOrNull { it.doesContainPhoneNumber(number) }
                        // If the number in the contacts is written without + or 8 instead of +7
                        // https://en.wikipedia.org/wiki/National_conventions_for_writing_telephone_numbers
                        if (contact == null) contact = contacts.firstOrNull {
                            it.doesContainPhoneNumber(number.replace("+", "")) || //All
                                it.doesContainPhoneNumber(number.replace("+7", "8")) || //Russia
                                it.doesContainPhoneNumber(number.replace("+31", "0")) || //Netherlands
                                it.doesContainPhoneNumber(number.replace("+33", "0")) || //France
                                it.doesContainPhoneNumber(number.replace("+34", "")) || //Spain
                                it.doesContainPhoneNumber(number.replace("+39", "0")) || //Italy
                                it.doesContainPhoneNumber(number.replace("+44", "0")) || //United Kingdom
                                it.doesContainPhoneNumber(number.replace("+49", "0")) || //Germany
                                it.doesContainPhoneNumber(number.replace("+91", "0")) || //India
                                it.doesContainPhoneNumber(number.replace("+351", "")) || //Portugal
                                it.doesContainPhoneNumber(number.replace("+374", "0")) || //Armenia
                                it.doesContainPhoneNumber(number.replace("+375", "0")) || //Belarus
                                it.doesContainPhoneNumber(number.replace("+380", "0")) //Ukraine
                        }
                        if (contact != null) contactsMap[number] = contact
                    }
                }

                //Without this, the call history does not reflect the contact name for the contact's second and subsequent numbers
                //TODO try again to find the contact name
                if (!isUnknownNumber) {
                    if (contact != null) name = contact.getNameToDisplay()
                }

                if (name!!.isEmpty() || name == "-1") {
                    name = context.getString(R.string.unknown)
                }

                var isVoiceMail = false
                if (name == number) {
                    @SuppressLint("MissingPermission")
                    if (context.hasPermission(PERMISSION_READ_PHONE_STATE)) {
                        try {
                            val telecomManager = context.telecomManager
                            telecomManager.callCapablePhoneAccounts.forEachIndexed { _, account ->
                                val phoneAccount = telecomManager.getPhoneAccount(account)
                                val voiceMailNumber = telecomManager.getVoiceMailNumber(phoneAccount.accountHandle)
                                if (voiceMailNumber == number) {
//                                    name = context.getString(R.string.voicemail)
                                    isVoiceMail = true
                                }
                            }
                        } catch (ignored: Exception) {
                        }
                    }
                }

                var photoUri = cursor.getStringValue(Calls.CACHED_PHOTO_URI).orEmpty()
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

                val startTS = cursor.getLongValue(Calls.DATE)
                if (previousStartTS == startTS) {
                    continue
                } else {
                    previousStartTS = startTS
                }

                val duration = cursor.getIntValue(Calls.DURATION)
                val type = cursor.getIntValue(Calls.TYPE)
                val features = cursor.getIntValue(Calls.FEATURES)
                val accountId = cursor.getStringValue(Calls.PHONE_ACCOUNT_ID)
                //var simID = accountIdToSimIDMap[accountId] ?: -1
                // Some users do not correctly detect the second ESIM card
                // https://stackoverflow.com/questions/63834168/identifying-a-sim-card-slot-with-phone-account-id-in-android-calllogcalls
                // On some devices PHONE_ACCOUNT_ID just returns the SIM card slot index
                var simID = -1
                if (isHuawei) {
                    if (accountId != null) {
                        if (accountId.length == 1) {
                            accountId.toIntOrNull()?.let {
                                simID = if (it >= 0) it + 1 else -1 //Huawei's sim card index returns (0,1...)
                            }
                        }
                    }
                    if (simID == -1) simID = accountIdToSimIDMap[accountId] ?: -1
                } else {
                    simID = accountIdToSimIDMap[accountId] ?: -1
                    if (simID == -1 && accountId != null) {
                        if (accountId.length == 1) {
                            accountId.toIntOrNull()?.let {
                                simID = if (it >= 0) it else -1
                            }
                        }
                    }
                }

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
                var contactID: Int? = null
                if (contact != null) {
                    val phoneNumbers = contact.phoneNumbers.firstOrNull { it.normalizedNumber == number }
                    if (phoneNumbers != null) {
                        specificNumber = contact.phoneNumbers.first { it.normalizedNumber == number }.value
                        specificType = context.getPhoneNumberTypeText(phoneNumbers.type, phoneNumbers.label)
                    }

                    nickname = contact.nickname
                    company = contact.organization.company
                    jobPosition = contact.organization.jobPosition
                    contactID = contact.id
                }

                recentCalls.add(
                    RecentCall(
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
                        simID = simID,
                        specificNumber = specificNumber,
                        specificType = specificType,
                        isUnknownNumber = isUnknownNumber,
                        contactID = contactID,
                        features = features,
                        isVoiceMail = isVoiceMail
                    )
                )

                if (updateCallsCache) {
                    updateRecentCallsContactInfo(
                        number.orEmpty(),
                        name,
                        cursor.getStringValueOrNull(Calls.CACHED_NAME),
                        photoUri,
                        cursor.getStringValueOrNull(Calls.CACHED_PHOTO_URI)
                    )
                }
            } while (cursor.moveToNext() && recentCalls.size < queryLimit)
        }

        val blockedNumbers = context.getBlockedNumbers()

        return if (context.config.showBlockedNumbers) recentCalls else
            recentCalls.filter { !context.isNumberBlocked(it.phoneNumber, blockedNumbers) }
    }

    fun removeRecentCalls(ids: List<Int>, callback: () -> Unit) {
        context.config.needUpdateRecents = true
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
                context.config.needUpdateRecents = true
                ensureBackgroundThread {
                    context.contentResolver.delete(contentUri, null, null)
                    callback()
                }
            }
        }
    }

    fun restoreRecentCalls(activity: SimpleActivity, objects: List<RecentCall>, callback: () -> Unit) {
        activity.handlePermission(PERMISSION_WRITE_CALL_LOG) { granted ->
            if (granted) {
                ensureBackgroundThread {
                    val values = objects
                        .sortedBy { it.startTS }
                        .map {
                            ContentValues().apply {
                                put(Calls.NUMBER, it.phoneNumber)
                                put(Calls.TYPE, it.type)
                                put(Calls.DATE, it.startTS)
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

    //https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#189
    fun markMissedCallsAsRead() {
        val values = ContentValues().apply {
            put(Calls.NEW, 0)
            put(Calls.IS_READ, 1)
        }
        val where = StringBuilder().apply {
            append(Calls.NEW)
            append(" = 1 AND ")
            append(Calls.TYPE)
            append(" = ?")
        }

        try {
            context.contentResolver.update(
                contentUri, values,
                where.toString(), arrayOf(Calls.MISSED_TYPE.toString())
            )
        } catch (ignored: IllegalArgumentException) {
        }
    }

    private fun updateRecentCallsContactInfo(
        number: String, updatedName: String, callLogName: String?, updatedPhotoUri: String?, callLogPhotoUri: String?
    ) {
        val values = ContentValues()
        var needsUpdate = false

        if (callLogName != null) {
            if (!TextUtils.equals(updatedName, callLogName)) {
                values.put(Calls.CACHED_NAME, updatedName)
                needsUpdate = true
            }
        } else {
            values.put(Calls.CACHED_NAME, updatedName)
            needsUpdate = true
        }

        if (callLogPhotoUri != null) {
            val updatedPhotoUriContactsOnly = UriUtils.nullForNonContactsUri(UriUtils.parseUriOrNull(updatedPhotoUri))
            if (!UriUtils.areEqual(updatedPhotoUriContactsOnly, UriUtils.parseUriOrNull(callLogPhotoUri))) {
                values.put(Calls.CACHED_PHOTO_URI, UriUtils.uriToString(updatedPhotoUriContactsOnly))
                needsUpdate = true
            }
        } else {
            values.put(Calls.CACHED_PHOTO_URI, updatedPhotoUri)
            needsUpdate = true
        }

        if (!needsUpdate) {
            return
        }

        try {
            context
                .contentResolver
                .update(
                    Calls.CONTENT_URI,
                    values,
                    Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " IS NULL",
                    arrayOf(number)
                )
        } catch (_: SQLiteFullException) {
        }
    }
}
