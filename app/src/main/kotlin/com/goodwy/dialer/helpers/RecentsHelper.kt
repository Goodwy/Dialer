package com.goodwy.dialer.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteFullException
import android.os.Build
import android.provider.CallLog.Calls
import android.provider.CallLog.Calls.PRESENTATION_UNAVAILABLE
import android.provider.CallLog.Calls.PRESENTATION_UNKNOWN
import android.telephony.PhoneNumberUtils
import android.text.TextUtils
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getAvailableSIMCardLabels
import com.goodwy.dialer.models.RecentCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            callback(ArrayList())
            return
        }

        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
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
                    val previousRecentCalls = previousRecentsOrEmpty
                        .flatMap { it.groupedCalls ?: listOf(it) }
                        .map { it.copy(groupedCalls = null) }

                    // We take into account the size of previous entries in the total limit
                    val remainingLimit = queryLimit - previousRecentCalls.size

                    if (remainingLimit <= 0) {
                        // If there are already enough previous entries, we return them within the limit
                        previousRecentCalls.take(queryLimit)
                    } else {

                        val newerLimit = (queryLimit * 0.3).toInt() // 30% limit on new entries
                        val newerRecents = getRecents(
                            contacts = contacts,
                            selection = "${Calls.DATE} > ?",
                            selectionParams = arrayOf("${previousRecentCalls.first().startTS}"),
                            updateCallsCache = updateCallsCache,
                            limit = newerLimit
                        )

                        val olderLimit = queryLimit - newerRecents.size // Remaining limit on old entries
                        val olderRecents = getRecents(
                            contacts = contacts,
                            selection = "${Calls.DATE} < ?",
                            selectionParams = arrayOf("${previousRecentCalls.last().startTS}"),
                            updateCallsCache = updateCallsCache,
                            limit = olderLimit
                        )

                        newerRecents + previousRecentCalls + olderRecents
                    }
                } else {
                    getRecents(contacts, updateCallsCache = updateCallsCache, limit = queryLimit)
                }

                callback(
                    recentCalls
                        .sortedByDescending { it.startTS }
                        .distinctBy { it.id }
//                        .take(queryLimit)
                )
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
        val differentSim = callA.simID != callB.simID
        val differentDay = callA.dayCode != callB.dayCode
        val namesAreBothRealAndDifferent =
            callA.name != callB.name &&
                callA.name != callA.phoneNumber &&
                callB.name != callB.phoneNumber

        if (differentSim || differentDay || namesAreBothRealAndDifferent) return false

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
        limit: Int = queryLimit
    ): List<RecentCall> {
        val recentCalls = mutableListOf<RecentCall>()
        var previousStartTS = 0L
//        val contactsNumbersMap = HashMap<String, String>()
        val contactPhotosMap = HashMap<String, String>()
        val contactsMap = HashMap<String, Contact>()

        val accountIdToSimIDMap = HashMap<String, Int>()
        context.getAvailableSIMCardLabels().forEach {
            accountIdToSimIDMap[it.handle.id] = it.id
        }
        val accountIdToSimColorMap = HashMap<String, Int>()
        context.getAvailableSIMCardLabels().forEach {
            accountIdToSimColorMap[it.handle.id] = it.color
        }

        val isHuawei = context.config.isEmui
        val voiceMailNumbers = loadVoiceMailNumbers()

        val projection = if (isQPlus()) {
            arrayOf(
                Calls._ID,
                Calls.NUMBER,
                Calls.CACHED_NAME,
                Calls.CACHED_PHOTO_URI,
                Calls.DATE,
                Calls.DURATION,
                Calls.TYPE,
                Calls.PHONE_ACCOUNT_ID,
                Calls.NUMBER_PRESENTATION,
                Calls.FEATURES,
                Calls.COUNTRY_ISO,
                Calls.BLOCK_REASON
            )
        } else {
            arrayOf(
                Calls._ID,
                Calls.NUMBER,
                Calls.CACHED_NAME,
                Calls.CACHED_PHOTO_URI,
                Calls.DATE,
                Calls.DURATION,
                Calls.TYPE,
                Calls.PHONE_ACCOUNT_ID,
                Calls.NUMBER_PRESENTATION,
                Calls.FEATURES,
                Calls.COUNTRY_ISO
            )
        }

        // https://issuetracker.google.com/issues/175198972?pli=1#comment6
        val limitedUri = contentUri.buildUpon()
            .appendQueryParameter(Calls.LIMIT_PARAM_KEY, limit.toString())
            .build()
        val sortOrder = "${Calls.DATE} DESC" //TODO Sort call
        val cursor = context.contentResolver.query(limitedUri, projection, selection, selectionParams, sortOrder)

        cursor?.use {
            if (!cursor.moveToFirst()) {
                return@use
            }

            do {
                val id = cursor.getIntValue(Calls._ID)
                var isUnknownNumber = false
                val number = cursor.getStringValueOrNull(Calls.NUMBER)
                val presentation = cursor.getIntValueOrNull(Calls.NUMBER_PRESENTATION) ?: Calls.PRESENTATION_ALLOWED
                val presentationBlocked = presentation == PRESENTATION_UNKNOWN
                    || presentation == PRESENTATION_UNAVAILABLE
                    || presentation == Calls.PRESENTATION_RESTRICTED
                if (presentationBlocked || number.isNullOrBlank() || number == "-1") {
                    isUnknownNumber = true
                }

                var name = cursor.getStringValueOrNull(Calls.CACHED_NAME)
                if (name.isNullOrEmpty() || name == "-1") {
                    name = number.orEmpty()
                }

                var contact: Contact? = null
                if (number != null) {
                    if (contactsMap.containsKey(number)) {
                        contact = contactsMap[number]!!
                    } else {
                        contact = contacts.firstOrNull { it.doesContainPhoneNumber(number) } //contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                        if (contact != null) contactsMap[number] = contact
                    }
                }

                //Without this, the call history does not reflect the contact name for the contact's second and subsequent numbers
                //TODO try again to find the contact name
                if (!isUnknownNumber) {
                    if (contact != null) name = contact.getNameToDisplay()
                }

                if (name.isEmpty() || name == "-1") {
                    name = context.getString(R.string.unknown)
                }

                val isVoiceMail = if (name == number)  voiceMailNumbers.contains(number) else false

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
                val simColor = accountIdToSimColorMap[accountId] ?: context.config.simIconsColors[0]

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

                val blockReason = if (isQPlus()) cursor.getIntValueOrNull(Calls.BLOCK_REASON) ?: 0 else 0

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
                        simColor = simColor,
                        specificNumber = specificNumber,
                        specificType = specificType,
                        isUnknownNumber = isUnknownNumber,
                        contactID = contactID,
                        features = features,
                        isVoiceMail = isVoiceMail,
                        blockReason = blockReason
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

        return if (context.config.showBlockedNumbers) recentCalls
        else {
            val blockedNumbers = context.getBlockedNumbers()
            recentCalls.filter { !context.isNumberBlocked(it.phoneNumber, blockedNumbers) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadVoiceMailNumbers(): List<String> {
        return if (context.hasPermission(PERMISSION_READ_PHONE_STATE)) {
            try {
                context.telecomManager.callCapablePhoneAccounts.mapNotNull { account ->
                    context.telecomManager.getVoiceMailNumber(
                        context.telecomManager.getPhoneAccount(account).accountHandle
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
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
        CoroutineScope(Dispatchers.IO).launch {
            val values = ContentValues().apply {
                put(Calls.NEW, 0)
                put(Calls.IS_READ, 1)
            }
//        val where = StringBuilder().apply {
//            append(Calls.NEW)
//            append(" = 1 AND ")
//            append(Calls.TYPE)
//            append(" = ?")
//        }
            val where = "${Calls.NEW} = 1 AND ${Calls.TYPE} = ?"

            try {
                context.contentResolver.update(
                    contentUri, values,
                    where, arrayOf(Calls.MISSED_TYPE.toString())
                )
            } catch (_: IllegalArgumentException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateRecentCallsContactInfo(
        number: String, updatedName: String, callLogName: String?, updatedPhotoUri: String?, callLogPhotoUri: String?
    ) {
        if (!context.hasPermission(PERMISSION_WRITE_CALL_LOG)) {
            return
        }

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

        val updatedPhotoUriContactsOnly = UriUtils.nullForNonContactsUri(UriUtils.parseUriOrNull(updatedPhotoUri))
        val currentPhotoUri = UriUtils.parseUriOrNull(callLogPhotoUri)
        if (!UriUtils.areEqual(updatedPhotoUriContactsOnly, currentPhotoUri)) {
            values.put(Calls.CACHED_PHOTO_URI, UriUtils.uriToString(updatedPhotoUriContactsOnly))
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
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (_: SQLiteFullException) {
        }
    }
}
