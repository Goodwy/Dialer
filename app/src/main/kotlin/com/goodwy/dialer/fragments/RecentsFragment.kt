package com.goodwy.dialer.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.provider.CallLog.Calls
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.extensions.beGone
import com.goodwy.commons.extensions.beGoneIf
import com.goodwy.commons.extensions.beVisible
import com.goodwy.commons.extensions.getMyContactsCursor
import com.goodwy.commons.extensions.getProperBackgroundColor
import com.goodwy.commons.extensions.getSurfaceColor
import com.goodwy.commons.extensions.hasPermission
import com.goodwy.commons.extensions.hideKeyboard
import com.goodwy.commons.extensions.isDynamicTheme
import com.goodwy.commons.extensions.isSystemInDarkMode
import com.goodwy.commons.extensions.launchActivityIntent
import com.goodwy.commons.extensions.launchCallIntent
import com.goodwy.commons.extensions.underlineText
import com.goodwy.commons.helpers.CONTACT_ID
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.IS_PRIVATE
import com.goodwy.commons.helpers.MyContactsContentProvider
import com.goodwy.commons.helpers.PERMISSION_READ_CALL_LOG
import com.goodwy.commons.helpers.SMT_PRIVATE
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.CallHistoryActivity
import com.goodwy.dialer.activities.MainActivity
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.adapters.RecentCallsAdapter
import com.goodwy.dialer.databinding.FragmentRecentsBinding
import com.goodwy.dialer.extensions.callContactWithSim
import com.goodwy.dialer.extensions.callerNotesHelper
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.launchSendSMSIntentRecommendation
import com.goodwy.dialer.extensions.numberForNotes
import com.goodwy.dialer.extensions.runAfterAnimations
import com.goodwy.dialer.extensions.startAddContactIntent
import com.goodwy.dialer.extensions.startContactDetailsIntent
import com.goodwy.dialer.helpers.CURRENT_RECENT_CALL
import com.goodwy.dialer.helpers.CURRENT_RECENT_CALL_LIST
import com.goodwy.dialer.helpers.RECENT_CALL_CACHE_SIZE
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.helpers.SWIPE_ACTION_CALL
import com.goodwy.dialer.helpers.SWIPE_ACTION_MESSAGE
import com.goodwy.dialer.helpers.SWIPE_ACTION_OPEN
import com.goodwy.dialer.interfaces.RefreshItemsListener
import com.goodwy.dialer.models.CallLogItem
import com.goodwy.dialer.models.RecentCall
import com.google.gson.Gson

class RecentsFragment(
    context: Context, attributeSet: AttributeSet,
) : MyViewPagerFragment<MyViewPagerFragment.RecentsInnerBinding>(context, attributeSet), RefreshItemsListener {

    private lateinit var binding: FragmentRecentsBinding
    private var allRecentCalls = listOf<CallLogItem>()
    private var recentsAdapter: RecentCallsAdapter? = null

    private var searchQuery: String? = null
    private var recentsHelper = RecentsHelper(context)

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentRecentsBinding.bind(this)
        innerBinding = RecentsInnerBinding(binding)
    }

    override fun setupFragment() {
        val useSurfaceColor = context.isDynamicTheme() && !context.isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) context.getSurfaceColor() else context.getProperBackgroundColor()
        binding.recentsFragment.setBackgroundColor(backgroundColor)

        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        binding.recentsPlaceholder.text = context.getString(placeholderResId)
        binding.recentsPlaceholder2.apply {
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, accentColor: Int) {
        binding.recentsPlaceholder.setTextColor(textColor)
        binding.recentsPlaceholder2.setTextColor(primaryColor)

        recentsAdapter?.apply {
            updatePrimaryColor()
            updateBackgroundColor(context.getProperBackgroundColor())
            updateTextColor(textColor)
            initDrawables(textColor)
        }
    }

    override fun refreshItems(invalidate: Boolean, needUpdate: Boolean, callback: (() -> Unit)?) {
        if (invalidate) {
            allRecentCalls = emptyList()
            activity!!.config.recentCallsCache = ""
        }

        if (needUpdate || !searchQuery.isNullOrEmpty() || activity!!.config.needUpdateRecents) {
            refreshCallLog(loadAll = false) {
                binding.recentsList.runAfterAnimations {
                    refreshCallLog(loadAll = true)
                }
            }
        } else {
            var recents = emptyList<RecentCall>()
            if (!invalidate) {
                try {
                    recents = activity!!.config.parseRecentCallsCache()
                } catch (_: Exception) {
                    activity!!.config.recentCallsCache = ""
                }
            }

            if (recents.isNotEmpty()) {
                refreshCallLogFromCache(recents) {
                    binding.recentsList.runAfterAnimations {
                        refreshCallLog(loadAll = true)
                    }
                }
            } else {
                refreshCallLog(loadAll = false) {
                    binding.recentsList.runAfterAnimations {
                        refreshCallLog(loadAll = true)
                    }
                }
            }
        }
    }

    override fun onSearchClosed() {
        searchQuery = null
        showOrHidePlaceholder(allRecentCalls.isEmpty())
        recentsAdapter?.updateItems(allRecentCalls)
    }

    override fun onSearchQueryChanged(text: String) {
        searchQuery = text
        updateSearchResult()
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateSearchResult() {
        ensureBackgroundThread {
            val fixedText = searchQuery!!.trim().replace("\\s+".toRegex(), " ")
            val recentCalls = allRecentCalls
                .filterIsInstance<RecentCall>()
                .filter {
                    it.name.contains(fixedText, true) ||
                        it.doesContainPhoneNumber(fixedText) ||
                        it.nickname.contains(fixedText, true) ||
                        it.company.contains(fixedText, true) ||
                        it.jobPosition.contains(fixedText, true)
                }
                .sortedWith(
                    compareByDescending<RecentCall> { it.dayCode }
                        .thenByDescending { it.name.startsWith(fixedText, true) }
                        .thenByDescending { it.startTS }
                )

            prepareCallLog(recentCalls) {
                activity?.runOnUiThread {
                    showOrHidePlaceholder(recentCalls.isEmpty())
                    recentsAdapter?.updateItems(it, fixedText)
                }
            }
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                binding.recentsPlaceholder.text = context.getString(R.string.no_previous_calls)
                binding.recentsPlaceholder2.beGone()
                refreshCallLog()
            }
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        if (show /*&& !binding.progressIndicator.isVisible()*/) {
            binding.recentsPlaceholder.beVisible()
        } else {
            binding.recentsPlaceholder.beGone()
        }
    }

    private fun gotRecents(recents: List<CallLogItem>) {
//        binding.progressIndicator.hide()
        if (recents.isEmpty()) {
            binding.apply {
                showOrHidePlaceholder(true)
                recentsPlaceholder2.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))
                recentsList.beGone()
            }
        } else {
            binding.apply {
                showOrHidePlaceholder(false)
                recentsPlaceholder2.beGone()
                recentsList.beVisible()

//                recentsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
//                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
//                        super.onScrollStateChanged(recyclerView, newState)
//                        activity?.hideKeyboard()
//                    }
//                })
                recentsList.setOnTouchListener { _, _ ->
                    activity?.hideKeyboard()
                    false
                }
            }

            if (binding.recentsList.adapter == null) {
                recentsAdapter = RecentCallsAdapter(
                    activity = activity as SimpleActivity,
                    recyclerView = binding.recentsList,
                    refreshItemsListener = this,
                    showOverflowMenu = true,
                    showCallIcon = context.config.onRecentClick == SWIPE_ACTION_OPEN,
                    hideTimeAtOtherDays = true,
                    itemDelete = { deleted ->
                        allRecentCalls = allRecentCalls.filter { it !in deleted }
                    },
                    itemClick = {
                        itemClickAction(context.config.onRecentClick, it as RecentCall)
                    },
                    profileInfoClick = { recentCall ->
                        actionOpen(recentCall)
                    },
                    profileIconClick = {
                        val recentCall = it as RecentCall
                        val contact = findContactByCall(recentCall)
                        if (contact != null) {
                            activity?.startContactDetailsIntent(contact)
                        } else {
                            activity?.startAddContactIntent(recentCall.phoneNumber)
                        }
                    }
                )

                recentsAdapter?.addBottomPadding(64)

                binding.recentsList.adapter = recentsAdapter
                recentsAdapter?.updateItems(recents)
            } else {
                recentsAdapter?.updateItems(recents)
            }
        }
    }

    private fun callRecentNumber(recentCall: RecentCall) {
        if (context.config.callUsingSameSim && recentCall.simID > 0) {
            val sim = recentCall.simID == 1
            activity?.callContactWithSim(recentCall.phoneNumber, sim)
        }
        else {
            activity?.launchCallIntent(recentCall.phoneNumber, key = BuildConfig.RIGHT_APP_KEY)
        }
    }

    private fun refreshCallLog(loadAll: Boolean = false, callback: (() -> Unit)? = null) {
        getRecentCalls(loadAll) {
            allRecentCalls = it
            if (searchQuery.isNullOrEmpty()) {
                activity?.runOnUiThread { gotRecents(it) }
                callback?.invoke()

                context.config.recentCallsCache = Gson().toJson(it.take(RECENT_CALL_CACHE_SIZE))
            } else {
                updateSearchResult()
                callback?.invoke()
            }

            //Deleting notes if a call has already been deleted
            context.callerNotesHelper.removeCallerNotes(
                it.map { recentCall -> recentCall.phoneNumber.numberForNotes()}
            )
        }

        if (loadAll) {
            with(recentsHelper) {
                val queryCount = context.config.queryLimitRecent
                getRecentCalls(queryLimit = queryCount, updateCallsCache = false) { it ->
                    ensureBackgroundThread {
                        val recentOutgoingNumbers = it
                            .filter { it.type == Calls.OUTGOING_TYPE }
                            .map { recentCall -> recentCall.phoneNumber }

                        context.config.recentOutgoingNumbers = recentOutgoingNumbers.toMutableSet()
                    }
                }
            }
        }
    }

    private fun refreshCallLogFromCache(cache: List<RecentCall>, callback: (() -> Unit)? = null) {
        gotRecents(cache)
        callback?.invoke()
    }

    private fun getRecentCalls(loadAll: Boolean, callback: (List<RecentCall>) -> Unit) {
        val queryCount = if (loadAll) context.config.queryLimitRecent else RecentsHelper.QUERY_LIMIT
        val existingRecentCalls = allRecentCalls.filterIsInstance<RecentCall>()

        with(recentsHelper) {
            if (context.config.groupSubsequentCalls) {
                getGroupedRecentCalls(existingRecentCalls, queryCount) {
                    prepareCallLog(it, callback)
                }
            } else {
                getRecentCalls(existingRecentCalls, queryCount, updateCallsCache = true) { it ->
                    val calls = if (context.config.groupAllCalls) it.distinctBy { it.phoneNumber } else it
                    prepareCallLog(calls, callback)
                }
            }
        }
    }

    private fun prepareCallLog(calls: List<RecentCall>, callback: (List<RecentCall>) -> Unit) {
        if (calls.isEmpty()) {
            callback(emptyList())
            return
        }

        ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            ensureBackgroundThread {
                val privateContacts = getPrivateContacts()
                val updatedCalls = updateNamesIfEmpty(
                    calls = maybeFilterPrivateCalls(calls, privateContacts),
                    contacts = contacts,
                    privateContacts = privateContacts
                )

                callback(
                    updatedCalls
                )
            }
        }
    }

    private fun getPrivateContacts(): ArrayList<Contact> {
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        return MyContactsContentProvider.getContacts(context, privateCursor)
    }

    private fun maybeFilterPrivateCalls(calls: List<RecentCall>, privateContacts: List<Contact>): List<RecentCall> {
        val ignoredSources = context.baseConfig.ignoredContactSources
        return if (SMT_PRIVATE in ignoredSources) {
            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
            calls.filterNot { it.phoneNumber in privateNumbers }
        } else {
            calls
        }
    }

    private fun updateNamesIfEmpty(calls: List<RecentCall>, contacts: List<Contact>, privateContacts: List<Contact>): List<RecentCall> {
        if (calls.isEmpty()) return mutableListOf()

        val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
        return calls.map { call ->
            if (call.phoneNumber == call.name) {
                val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(call.phoneNumber) }
                val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == call.phoneNumber }

                when {
                    privateContact != null -> withUpdatedName(call = call, name = privateContact.getNameToDisplay())
                    contact != null -> withUpdatedName(call = call, name = contact.getNameToDisplay())
                    else -> call
                }
            } else {
                call
            }
        }
    }

    private fun withUpdatedName(call: RecentCall, name: String): RecentCall {
        return call.copy(
            name = name,
            groupedCalls = call.groupedCalls
                ?.map { it.copy(name = name) }
                ?.toMutableList()
                ?.ifEmpty { null }
        )
    }

//    private fun groupCallsByDate(recentCalls: List<RecentCall>): MutableList<CallLogItem> {
//        val callLog = mutableListOf<CallLogItem>()
//        var lastDayCode = ""
//        for (call in recentCalls) {
//            val currentDayCode = call.dayCode
//            if (currentDayCode != lastDayCode) {
//                callLog += CallLogItem.Date(timestamp = call.startTS, dayCode = currentDayCode)
//                lastDayCode = currentDayCode
//            }
//
//            callLog += call
//        }
//
//        return callLog
//    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as MainActivity).cachedContacts
            .find { /*it.name == recentCall.name &&*/ it.doesHavePhoneNumber(recentCall.phoneNumber) }
    }

    override fun myRecyclerView() = binding.recentsList

    private fun itemClickAction(action: Int, call: RecentCall) {
        when (action) {
            SWIPE_ACTION_MESSAGE -> actionSMS(call)
            SWIPE_ACTION_CALL -> actionCall(call)
            SWIPE_ACTION_OPEN -> actionOpen(call)
            else -> {}
        }
    }

    private fun actionCall(call: RecentCall) {
        val recentCall = call
        if (context.config.showCallConfirmation) {
            CallConfirmationDialog(activity as SimpleActivity, recentCall.name) {
                callRecentNumber(recentCall)
            }
        } else {
            callRecentNumber(recentCall)
        }
    }

    private fun actionSMS(call: RecentCall) {
        activity?.launchSendSMSIntentRecommendation(call.phoneNumber)
    }

    private fun actionOpen(call: RecentCall) {
        val recentCalls = call.groupedCalls as ArrayList<RecentCall>? ?: arrayListOf(call)
        val contact = findContactByCall(call)
        Intent(activity, CallHistoryActivity::class.java).apply {
            putExtra(CURRENT_RECENT_CALL, call)
            putExtra(CURRENT_RECENT_CALL_LIST, recentCalls)
            putExtra(CONTACT_ID, call.contactID)
            if (contact != null) {
                putExtra(IS_PRIVATE, contact.isPrivate())
            }
            activity?.launchActivityIntent(this)
        }
    }
}

class BottomSpaceDecoration(private val spaceHeight: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == parent.adapter?.itemCount?.minus(1) ?: 0) {
            outRect.bottom = spaceHeight
        }
    }
}
