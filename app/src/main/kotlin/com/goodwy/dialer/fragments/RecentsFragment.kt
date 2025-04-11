package com.goodwy.dialer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.CallHistoryActivity
import com.goodwy.dialer.activities.MainActivity
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.adapters.RecentCallsAdapter
import com.goodwy.dialer.databinding.FragmentRecentsBinding
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.CURRENT_RECENT_CALL
import com.goodwy.dialer.helpers.CURRENT_RECENT_CALL_LIST
import com.goodwy.dialer.helpers.RecentsHelper
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
        binding.recentsFragment.setBackgroundColor(context.getProperBackgroundColor())
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

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.recentsPlaceholder.setTextColor(textColor)
        binding.recentsPlaceholder2.setTextColor(properPrimaryColor)

        recentsAdapter?.apply {
            updatePrimaryColor()
            updateBackgroundColor(context.getProperBackgroundColor())
            updateTextColor(textColor)
            initDrawables(textColor)
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        if (invalidate) {
            allRecentCalls = emptyList()
        }

        gotRecents()
        refreshCallLog(loadAll = true) {
//            refreshCallLog(loadAll = true)
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
                .filter {
                    it is RecentCall && (it.name.contains(fixedText, true) ||
                        it.doesContainPhoneNumber(fixedText) ||
                        it.nickname.contains(fixedText, true) ||
                        it.company.contains(fixedText, true) ||
                        it.jobPosition.contains(fixedText, true))
                }.sortedByDescending {
                    it is RecentCall && it.name.startsWith(fixedText, true)
                } as List<RecentCall>

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

    private fun gotRecents(recents: List<CallLogItem> = activity!!.config.parseRecentCallsCache()) {
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

                recentsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        activity?.hideKeyboard()
                    }
                })
            }

            if (binding.recentsList.adapter == null) {
                recentsAdapter = RecentCallsAdapter(
                    activity = activity as SimpleActivity,
                    recyclerView = binding.recentsList,
                    refreshItemsListener = this,
                    showOverflowMenu = true,
                    hideTimeAtOtherDays = true,
                    itemDelete = { deleted ->
                        allRecentCalls = allRecentCalls.filter { it !in deleted }
                    },
                    itemClick = {
                        val recentCall = it as RecentCall
                        if (context.config.showCallConfirmation) {
                            CallConfirmationDialog(activity as SimpleActivity, recentCall.name) {
                                callRecentNumber(recentCall)
                            }
                        } else {
                            callRecentNumber(recentCall)
                        }
                    },
                    profileInfoClick = {
                        val recentCall = it as RecentCall
//                        val recentCalls = recents
//                            .filterIsInstance<RecentCall>()
//                            .filter { recent -> recent.phoneNumber == recentCall.phoneNumber} as ArrayList<RecentCall>
                        val recentCalls = recentCall.groupedCalls as ArrayList<RecentCall>? ?: arrayListOf(recentCall)
                        Intent(activity, CallHistoryActivity::class.java).apply {
                            putExtra(CURRENT_RECENT_CALL, recentCall)
                            putExtra(CURRENT_RECENT_CALL_LIST, recentCalls)
                            putExtra(CONTACT_ID, recentCall.contactID)
                            activity?.launchActivityIntent(this)
                        }
                    },
                    profileIconClick = {
                        val contact = findContactByCall(it as RecentCall)
                        if (contact != null) {
                            activity?.startContactDetailsIntent(contact)
                        } else {
                            addContact(it)
                        }
                    }
                )

                binding.recentsList.adapter = recentsAdapter
                recentsAdapter?.updateItems(recents)

                if (context.areSystemAnimationsEnabled) {
                    binding.recentsList.scheduleLayoutAnimation()
                }

                binding.recentsList.endlessScrollListener = object : MyRecyclerView.EndlessScrollListener {
                        override fun updateTop() = Unit
                        override fun updateBottom() = refreshCallLog()
                    }
            } else {
                recentsAdapter?.updateItems(recents)
            }
        }
    }

    private fun callRecentNumber(recentCall: RecentCall) {
        if (context.config.callUsingSameSim && recentCall.simID > 0) {
            val sim = recentCall.simID == 1;
            activity?.callContactWithSim(recentCall.phoneNumber, sim);
        }
        else {
            activity?.launchCallIntent(recentCall.phoneNumber, key = BuildConfig.RIGHT_APP_KEY)
        }
    }

    private fun refreshCallLog(loadAll: Boolean = false, callback: (() -> Unit)? = null) {
        getRecentCalls(loadAll) {
            allRecentCalls = it
            val recentCalls = it.filterIsInstance<RecentCall>()
            context.config.recentCallsCache = Gson().toJson(recentCalls.take(300))
            if (searchQuery.isNullOrEmpty()) {
                activity?.runOnUiThread { gotRecents(it) }
            } else {
                updateSearchResult()
            }

            //Deleting notes if a call has already been deleted
            context.callerNotesHelper.removeCallerNotes(
                recentCalls.map { recentCall -> recentCall.phoneNumber.numberForNotes()}
            )

            callback?.invoke()
        }
    }

    private fun getRecentCalls(loadAll: Boolean, callback: (List<CallLogItem>) -> Unit) {
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

    private fun prepareCallLog(calls: List<RecentCall>, callback: (List<CallLogItem>) -> Unit) {
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

    private fun groupCallsByDate(recentCalls: List<RecentCall>): MutableList<CallLogItem> {
        val callLog = mutableListOf<CallLogItem>()
        var lastDayCode = ""
        for (call in recentCalls) {
            val currentDayCode = call.getDayCode()
            if (currentDayCode != lastDayCode) {
                callLog += CallLogItem.Date(timestamp = call.startTS, dayCode = currentDayCode)
                lastDayCode = currentDayCode
            }

            callLog += call
        }

        return callLog
    }


    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as MainActivity).cachedContacts.find { it.name == recentCall.name && it.doesHavePhoneNumber(recentCall.phoneNumber) }
    }

    private fun addContact(recentCall: RecentCall) {
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, recentCall.phoneNumber)
            context.launchActivityIntent(this)
        }
    }

    override fun myRecyclerView() = binding.recentsList
}
