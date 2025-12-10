package com.goodwy.dialer.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.provider.CallLog.Calls
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.SimpleItemAnimator
import com.behaviorule.arturdumchev.library.pixels
import com.bumptech.glide.Glide
import com.goodwy.commons.adapters.MyRecyclerViewListAdapter
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.extensions.addBlockedNumber
import com.goodwy.commons.extensions.adjustAlpha
import com.goodwy.commons.extensions.applyColorFilter
import com.goodwy.commons.extensions.beVisibleIf
import com.goodwy.commons.extensions.copyToClipboard
import com.goodwy.commons.extensions.deleteBlockedNumber
import com.goodwy.commons.extensions.formatDateOrTime
import com.goodwy.commons.extensions.formatPhoneNumber
import com.goodwy.commons.extensions.formatSecondsToShortTimeString
import com.goodwy.commons.extensions.formatterUnicodeWrap
import com.goodwy.commons.extensions.getBlockedNumbers
import com.goodwy.commons.extensions.getColoredDrawableWithColor
import com.goodwy.commons.extensions.getContrastColor
import com.goodwy.commons.extensions.getLetterBackgroundColors
import com.goodwy.commons.extensions.getPopupMenuTheme
import com.goodwy.commons.extensions.getTextSize
import com.goodwy.commons.extensions.highlightTextPart
import com.goodwy.commons.extensions.isDefaultDialer
import com.goodwy.commons.extensions.isDynamicTheme
import com.goodwy.commons.extensions.isNumberBlocked
import com.goodwy.commons.extensions.isRTLLayout
import com.goodwy.commons.extensions.isSystemInDarkMode
import com.goodwy.commons.extensions.launchActivityIntent
import com.goodwy.commons.extensions.launchCallIntent
import com.goodwy.commons.extensions.launchInternetSearch
import com.goodwy.commons.extensions.setHeightAndWidth
import com.goodwy.commons.extensions.setupViewBackground
import com.goodwy.commons.extensions.slideLeft
import com.goodwy.commons.extensions.slideLeftReturn
import com.goodwy.commons.extensions.slideRight
import com.goodwy.commons.extensions.slideRightReturn
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.extensions.updateMarginWithBase
import com.goodwy.commons.helpers.CONTACT_ID
import com.goodwy.commons.helpers.IS_PRIVATE
import com.goodwy.commons.helpers.IS_RIGHT_APP
import com.goodwy.commons.helpers.PERMISSION_WRITE_CALL_LOG
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.CallHistoryActivity
import com.goodwy.dialer.activities.DialpadActivity
import com.goodwy.dialer.activities.MainActivity
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.databinding.ItemRecentCallBinding
import com.goodwy.dialer.databinding.ItemRecentCallSwipeBinding
import com.goodwy.dialer.databinding.ItemRecentsDateBinding
import com.goodwy.dialer.extensions.areMultipleSIMsAvailable
import com.goodwy.dialer.extensions.callContactWithSim
import com.goodwy.dialer.extensions.callContactWithSimWithConfirmationCheck
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getCountryByNumber
import com.goodwy.dialer.extensions.getDayCode
import com.goodwy.dialer.extensions.launchSendSMSIntentRecommendation
import com.goodwy.dialer.extensions.setWidth
import com.goodwy.dialer.extensions.startAddContactIntent
import com.goodwy.dialer.extensions.startCallWithConfirmationCheck
import com.goodwy.dialer.extensions.startContactDetailsIntentRecommendation
import com.goodwy.dialer.helpers.CURRENT_RECENT_CALL
import com.goodwy.dialer.helpers.CURRENT_RECENT_CALL_LIST
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.helpers.SWIPE_ACTION_BLOCK
import com.goodwy.dialer.helpers.SWIPE_ACTION_DELETE
import com.goodwy.dialer.helpers.SWIPE_ACTION_MESSAGE
import com.goodwy.dialer.helpers.SWIPE_ACTION_NONE
import com.goodwy.dialer.interfaces.RefreshItemsListener
import com.goodwy.dialer.models.CallLogItem
import com.goodwy.dialer.models.RecentCall
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeDirection
import me.thanel.swipeactionview.SwipeGestureListener
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import org.joda.time.DateTime
import kotlin.math.abs

class RecentCallsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    private val refreshItemsListener: RefreshItemsListener?,
    private val showOverflowMenu: Boolean = false,
    private val showCallIcon: Boolean = false,
    private val hideTimeAtOtherDays: Boolean = false,
    private val isDialpad: Boolean = false,
    private val itemDelete: (List<RecentCall>) -> Unit = {},
    itemClick: (Any) -> Unit,
    val profileInfoClick: ((RecentCall) -> Unit)? = null,
    val profileIconClick: ((Any) -> Unit)? = null
) : MyRecyclerViewListAdapter<CallLogItem>(activity, recyclerView, RecentCallsDiffCallback(), itemClick) {

    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    private lateinit var blockedCallIcon: Drawable
    var fontSize: Float = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val missedCallColor = resources.getColor(R.color.red_missed, activity.theme)
    private var secondaryTextColor = textColor.adjustAlpha(0.6f)
    private var textToHighlight = ""
    private var getBlockedNumbers = activity.getBlockedNumbers()
    private val cachedSimColors = HashMap<Int, Int>()
    private val marginNormal = resources.getDimension(com.goodwy.commons.R.dimen.normal_margin).toInt()
    private val marginTen = resources.getDimension(com.goodwy.commons.R.dimen.ten_dpi).toInt()

    private val voiceMail = resources.getString(R.string.voicemail)
    private val colorCache = mutableMapOf<String, Int>()

    companion object {
        private const val VIEW_TYPE_DATE = 0
        private const val VIEW_TYPE_CALL = 1
        private const val VIEW_TYPE_CALL_SWIPE = 2
    }

    init {
        initDrawables()
        setupDragListener(true)
        setHasStableIds(true)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val selectedItems = getSelectedItems()
        val isOneItemSelected = selectedItems.size == 1
        val selectedNumber = "tel:${getSelectedPhoneNumber()}".replace("+","%2B")
        getBlockedNumbers = activity.getBlockedNumbers()
        val isAllBlockedNumbers = isAllBlockedNumbers()
        val isAllUnblockedNumbers = isAllUnblockedNumbers()

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""

            findItem(R.id.cab_block_number).title = if (isOneItemSelected) resources.getString(R.string.block_number) else resources.getString(R.string.block_numbers)
            findItem(R.id.cab_block_number).isVisible = isAllUnblockedNumbers && !isAllBlockedNumbers
            findItem(R.id.cab_unblock_number).title = if (isOneItemSelected) resources.getString(R.string.unblock_number) else resources.getString(R.string.unblock_numbers)
            findItem(R.id.cab_unblock_number).isVisible = isAllBlockedNumbers && !isAllUnblockedNumbers
            findItem(R.id.cab_add_number).isVisible = isOneItemSelected
            findItem(R.id.cab_show_call_details).isVisible = isOneItemSelected
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected
            findItem(R.id.web_search).isVisible = isOneItemSelected
            findItem(R.id.cab_view_details)?.isVisible = isOneItemSelected && findContactByCall(selectedItems.first()) != null
        }
    }

    private fun isAllBlockedNumbers(): Boolean {
        getSelectedItems().map { it.phoneNumber }.forEach { number ->
            if (activity.isNumberBlocked(number, getBlockedNumbers)) return true
        }
        return false
    }

    private fun isAllUnblockedNumbers(): Boolean {
        getSelectedItems().map { it.phoneNumber }.forEach { number ->
            if (!activity.isNumberBlocked(number, getBlockedNumbers)) return true
        }
        return false
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_block_number -> askConfirmBlock()
            R.id.cab_unblock_number -> askConfirmUnblock()
            R.id.cab_add_number -> addNumberToContact()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_show_call_details -> showCallDetails()
            R.id.cab_copy_number -> copyNumber()
            R.id.web_search -> webSearch()
            R.id.cab_remove -> askConfirmRemove()
            R.id.cab_select_all -> selectAll()
            R.id.cab_view_details -> {
                val selectItems = getSelectedItems().firstOrNull() ?: return
                launchContactDetailsIntent(findContactByCall(selectItems))
            }
        }
    }

    override fun getItemId(position: Int) = currentList[position].getItemId().toLong()

    override fun getSelectableItemCount() = currentList.filterIsInstance<RecentCall>().size

    override fun getIsItemSelectable(position: Int) = currentList.getOrNull(position) is RecentCall

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.getItemId()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.getItemId() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun getItemViewType(position: Int): Int {
        return when (currentList[position]) {
            is CallLogItem.Date -> VIEW_TYPE_DATE
            is RecentCall -> if (activity.config.useSwipeToAction) VIEW_TYPE_CALL_SWIPE else VIEW_TYPE_CALL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = when (viewType) {
            VIEW_TYPE_DATE -> RecentCallDateViewHolder(
                ItemRecentsDateBinding.inflate(layoutInflater, parent, false)
            )

            VIEW_TYPE_CALL -> RecentCallViewHolder(
                ItemRecentCallBinding.inflate(layoutInflater, parent, false)
            )

            VIEW_TYPE_CALL_SWIPE -> RecentCallSwipeViewHolder(
                ItemRecentCallSwipeBinding.inflate(layoutInflater, parent, false)
            )

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }

        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val callRecord = currentList[position]
        when (holder) {
            is RecentCallDateViewHolder -> holder.bind(callRecord as CallLogItem.Date)
            is RecentCallViewHolder -> holder.bind(callRecord as RecentCall)
            is RecentCallSwipeViewHolder -> holder.bind(callRecord as RecentCall)
        }

        bindViewHolder(holder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            if (activity.config.useSwipeToAction) {
                if (holder is RecentCallSwipeViewHolder) {
                    Glide.with(activity).clear(holder.binding.itemRecentsImage)
                }
            } else {
                if (holder is RecentCallViewHolder) {
                    Glide.with(activity).clear(holder.binding.itemRecentsImage)
                }
            }
        }
    }

    override fun submitList(list: List<CallLogItem>?) {
        val layoutManager = recyclerView.layoutManager!!
        val recyclerViewState = layoutManager.onSaveInstanceState()
        super.submitList(list) {
            layoutManager.onRestoreInstanceState(recyclerViewState)
        }
    }

    fun initDrawables(newColor: Int = textColor) {
        secondaryTextColor = textColor.adjustAlpha(0.6f)
        outgoingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_call_made_vector, newColor)
        incomingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_call_received_vector, newColor)
        incomingMissedCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_call_missed_vector, newColor)
        blockedCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_block_vector, newColor)
    }

    private fun callContact(useSimOne: Boolean) {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        val name = getSelectedName() ?: return

        activity.callContactWithSimWithConfirmationCheck(phoneNumber, name, useSimOne)
    }

    private fun callContact(prefix: String = "") {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        val name = getSelectedName() ?: return

        (activity as SimpleActivity).startCallWithConfirmationCheck("$prefix$phoneNumber", name)
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber()?.replace("+","%2B") ?: return
        activity.config.removeCustomSIM(phoneNumber)
        finishActMode()
    }

    private fun askConfirmBlock() {
        if (activity.isDefaultDialer()) {
            val numbers = TextUtils.join(", ", getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
            val baseString = R.string.block_confirmation
            val question = String.format(resources.getString(baseString), numbers)

            ConfirmationAdvancedDialog(activity, question, cancelOnTouchOutside = false) {
                if (it) blockNumbers()
                else selectedKeys.clear()
            }
        } else {
            selectedKeys.clear()
            activity.toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()

        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            val recentCalls = currentList.toMutableList().also { if (!activity.config.showBlockedNumbers) it.removeAll(callsToBlock) }
            activity.runOnUiThread {
                if (!activity.config.showBlockedNumbers) submitList(recentCalls)
                finishActMode()
                selectedKeys.clear()
                getBlockedNumbers = activity.getBlockedNumbers()
            }
        }
    }

    private fun askConfirmUnblock() {
        if (activity.isDefaultDialer()) {
            val numbers = TextUtils.join(", ", getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
            val baseString = R.string.unblock_confirmation
            val question = String.format(resources.getString(baseString), numbers)

            ConfirmationAdvancedDialog(activity, question, cancelOnTouchOutside = false) {
                if (it) unblockNumbers()
                else selectedKeys.clear()
            }
        } else {
            selectedKeys.clear()
            activity.toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
        }
    }

    private fun unblockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()
        //val positions = getSelectedItemPositions()
        //recentCalls.removeAll(callsToBlock.toSet())

        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.deleteBlockedNumber(number)
            }

            activity.runOnUiThread {
                //removeSelectedItems(positions)
                finishActMode()
                selectedKeys.clear()
                getBlockedNumbers = activity.getBlockedNumbers()
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        val formatPhoneNumber = if (activity.config.formatPhoneNumbers) phoneNumber.formatPhoneNumber() else phoneNumber
        activity.startAddContactIntent(formatPhoneNumber)
    }

    private fun sendSMS() {
        val numbers = getSelectedItems().map { it.phoneNumber }
        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntentRecommendation(recipient)
    }

    private fun showCallDetails() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
//        val recentCalls = recentCall.groupedCalls ?: listOf(recentCall)
//        ShowGroupedCallsDialog(activity, recentCalls)
        showCallHistory(recentCall)
    }

    private fun copyNumber() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(recentCall.phoneNumber)
        finishActMode()
    }

    private fun editNumberBeforeCall() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        Intent(Intent.ACTION_DIAL).apply {
            data = Uri.fromParts("tel", recentCall.phoneNumber, null)
            putExtra(IS_RIGHT_APP, BuildConfig.RIGHT_APP_KEY)
            activity.launchActivityIntent(this)
        }
        finishActMode()
    }

    private fun webSearch() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        activity.launchInternetSearch(recentCall.phoneNumber)
        finishActMode()
    }

    private fun askConfirmRemove() {
        ConfirmationAdvancedDialog(activity, resources.getString(R.string.remove_confirmation), cancelOnTouchOutside = false) { result ->
            if (result) {
                activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                    if (it) removeRecents()
                }
            } else selectedKeys.clear()
        }
    }

    private fun removeRecents() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.groupedCalls?.mapTo(idsToRemove) { call -> call.id }
        }

        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            itemDelete(callsToRemove)
            val recentCalls = currentList.filterIsInstance<RecentCall>().toMutableList().also { it.removeAll(callsToRemove) }
            activity.runOnUiThread {
                refreshItemsListener?.refreshItems(needUpdate = true)
                submitList(recentCalls)
                finishActMode()
            }
        }
    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return if (isDialpad) (activity as DialpadActivity).allContacts
            .find { /*it.name == recentCall.name &&*/ it.doesHavePhoneNumber(recentCall.phoneNumber) }
        else (activity as MainActivity).cachedContacts
            .find { /*it.name == recentCall.name &&*/ it.doesHavePhoneNumber(recentCall.phoneNumber) }
    }

    private fun launchContactDetailsIntent(contact: Contact?) {
        if (contact != null) {
            activity.startContactDetailsIntentRecommendation(contact)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<CallLogItem>, highlightText: String = "") {
        if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            submitList(newItems)
            notifyDataSetChanged()
            finishActMode()
        } else {
            submitList(newItems)
        }
    }

    private fun getSelectedItems() = currentList.filterIsInstance<RecentCall>()
        .filter { selectedKeys.contains(it.getItemId()) }

    private fun getLastItem() = currentList.last()

    private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumber

    private fun getSelectedName() = getSelectedItems().firstOrNull()?.name

    private fun showPopupMenu(view: View, call: RecentCall) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)
        val contact = findContactByCall(call)
        val selectedNumber = "tel:${call.phoneNumber}".replace("+","%2B")
        getBlockedNumbers = activity.getBlockedNumbers()

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(R.menu.menu_recent_item_options)
            menu.apply {
                val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
                findItem(R.id.cab_call).isVisible = !areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_call_sim_1).isVisible = areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_call_sim_2).isVisible = areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_send_sms).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_view_details).isVisible = contact != null && !call.isUnknownNumber
                findItem(R.id.cab_add_number).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_copy_number).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_edit_number_before_call).isVisible = !call.isUnknownNumber
                findItem(R.id.web_search).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_show_call_details).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_block_number).isVisible = !call.isUnknownNumber && !activity.isNumberBlocked(call.phoneNumber, getBlockedNumbers)
                findItem(R.id.cab_unblock_number).isVisible = !call.isUnknownNumber && activity.isNumberBlocked(call.phoneNumber, getBlockedNumbers)
                findItem(R.id.cab_remove_default_sim).isVisible = (activity.config.getCustomSIM(selectedNumber) ?: "") != "" && !call.isUnknownNumber
            }
            setOnMenuItemClickListener { item ->
                val callId = call.id
                when (item.itemId) {
                    R.id.cab_call -> {
                        executeItemMenuOperation(callId) {
                            callContact()
                        }
                    }

                    R.id.cab_call_anonymously -> {
                        if (activity.config.showWarningAnonymousCall) {
                            var phoneNumber = ""
                            executeItemMenuOperation(callId) {
                                phoneNumber = getSelectedPhoneNumber() ?: "+1 234 567 8910"
                            }
                            val text = String.format(resources.getString(R.string.call_anonymously_warning), phoneNumber)
                            ConfirmationAdvancedDialog(
                                activity,
                                text,
                                R.string.call_anonymously_warning,
                                com.goodwy.commons.R.string.ok,
                                com.goodwy.commons.R.string.do_not_show_again,
                                fromHtml = true
                            ) {
                                if (it) {
                                    executeItemMenuOperation(callId) {
                                        callContact("#31#")
                                    }
                                } else {
                                    activity.config.showWarningAnonymousCall = false
                                    executeItemMenuOperation(callId) {
                                        callContact("#31#")
                                    }
                                }
                            }
                        } else {
                            executeItemMenuOperation(callId) {
                                callContact("#31#")
                            }
                        }
                    }

                    R.id.cab_call_sim_1 -> {
                        executeItemMenuOperation(callId) {
                            callContact(true)
                        }
                    }

                    R.id.cab_call_sim_2 -> {
                        executeItemMenuOperation(callId) {
                            callContact(false)
                        }
                    }

                    R.id.cab_send_sms -> {
                        executeItemMenuOperation(callId) {
                            sendSMS()
                        }
                    }

                    R.id.cab_view_details -> {
                        executeItemMenuOperation(callId) {
                            launchContactDetailsIntent(contact)
                        }
                    }

                    R.id.cab_add_number -> {
                        executeItemMenuOperation(callId) {
                            addNumberToContact()
                        }
                    }

                    R.id.cab_show_call_details -> {
                        executeItemMenuOperation(callId) {
                            showCallDetails()
                        }
                    }

                    R.id.cab_block_number -> {
                        selectedKeys.add(callId)
                        askConfirmBlock()
                    }

                    R.id.cab_unblock_number -> {
                        selectedKeys.add(callId)
                        askConfirmUnblock()
                    }

                    R.id.cab_remove -> {
                        selectedKeys.add(callId)
                        askConfirmRemove()
                    }

                    R.id.cab_copy_number -> {
                        executeItemMenuOperation(callId) {
                            copyNumber()
                        }
                    }

                    R.id.cab_edit_number_before_call -> {
                        executeItemMenuOperation(callId) {
                            editNumberBeforeCall()
                        }
                    }

                    R.id.web_search -> {
                        executeItemMenuOperation(callId) {
                            webSearch()
                        }
                    }

                    R.id.cab_remove_default_sim -> {
                        executeItemMenuOperation(callId) {
                            removeDefaultSIM()
                        }
                    }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(callId: Int, callback: () -> Unit) {
        selectedKeys.add(callId)
        callback()
        selectedKeys.remove(callId)
    }

    private inner class RecentCallViewHolder(val binding: ItemRecentCallBinding) : ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(call: RecentCall) = bindView(
            item = call,
            allowSingleClick = (refreshItemsListener != null || isDialpad) && !call.isUnknownNumber,
            allowLongClick = (refreshItemsListener != null || isDialpad) && !call.isUnknownNumber
        ) { _, _ ->
            binding.apply {
                val currentFontSize = fontSize
                itemRecentsHolder.isSelected = selectedKeys.contains(call.id)
                itemRecentsHolder.setupViewBackground(activity)

                divider.setBackgroundColor(textColor)
                if (getLastItem() == call || !activity.config.useDividers) divider.visibility = View.INVISIBLE else divider.visibility = View.VISIBLE

//                val matchingContact = findContactByCall(call)
                val name = call.name //matchingContact?.getNameToDisplay() ?: call.name
                val formatPhoneNumbers = activity.config.formatPhoneNumbers
                var nameToShow = if (name == call.phoneNumber && formatPhoneNumbers) {
                    SpannableString(name.formatPhoneNumber())
                } else {
                    SpannableString(formatterUnicodeWrap(name))
                }

                if (call.groupedCalls != null) {
                    nameToShow = SpannableString("$nameToShow (${call.groupedCalls.size})")
                }

                if (textToHighlight.isNotEmpty() && nameToShow.contains(textToHighlight, true)) {
                    nameToShow = SpannableString(nameToShow.toString().highlightTextPart(textToHighlight, properPrimaryColor))
                }

                itemRecentsName.apply {
                    text = nameToShow
                    setTextColor(if (call.type == Calls.MISSED_TYPE) missedCallColor else textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize)
                }

                var numberToShow =
                    if (formatPhoneNumbers) SpannableString(call.phoneNumber.formatPhoneNumber()) else SpannableString(call.phoneNumber)
                if (textToHighlight.isNotEmpty() && numberToShow.contains(textToHighlight, true)) {
                    numberToShow = SpannableString(numberToShow.toString().highlightTextPart(textToHighlight, properPrimaryColor))
                }

                itemRecentsNumber.apply {
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                    val recentsNumber = if (call.phoneNumber == call.name) {
                        if (call.isVoiceMail) voiceMail else call.phoneNumber.getCountryByNumber()
                    } else {
                        if (call.specificType.isNotEmpty() && call.specificNumber.isNotEmpty()) call.specificType
                        else {
                            if (formatPhoneNumbers) call.phoneNumber.formatPhoneNumber() else call.phoneNumber
                        }
                    }
                    text = if (name != call.phoneNumber && textToHighlight.isNotEmpty()) numberToShow else formatterUnicodeWrap(recentsNumber)
                }

                itemRecentsDateTime.apply {
                    text = if (activity.config.useRelativeDate) {
                        DateUtils.getRelativeDateTimeString(
                            context,
                            call.startTS,
                            1.minutes.inWholeMilliseconds,
                            2.days.inWholeMilliseconds,
                            0,
                        )
                    } else {
                        call.startTS.formatDateOrTime(context, hideTimeOnOtherDays = hideTimeAtOtherDays, false)
                    }
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                    if (showCallIcon) updateMarginWithBase(marginNormal,0,marginNormal,0)
                }

                itemRecentsDuration.apply {
                    text = context.formatSecondsToShortTimeString(call.duration)
                    setTextColor(textColor)
                    beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                }

                itemRecentsSimImage.beVisibleIf(areMultipleSIMsAvailable)
                itemRecentsSimId.beVisibleIf(areMultipleSIMsAvailable)
                if (areMultipleSIMsAvailable) {
                    val colorSimIcons = activity.config.colorSimIcons
                    val simColor = if (!colorSimIcons) textColor
                                else getAdjustedSimColor(call.simColor)
                    itemRecentsSimImage.applyColorFilter(simColor)
                    itemRecentsSimImage.alpha = if (!colorSimIcons) 0.6f else 1f
                    itemRecentsSimId.setTextColor(simColor.getContrastColor())
                    itemRecentsSimId.text = if (call.simID == -1) "?" else call.simID.toString()
                }

                val showContactThumbnails = activity.config.showContactThumbnails
                itemRecentsImage.beVisibleIf(showContactThumbnails)
                if (showContactThumbnails) {
                    val size = (root.context.pixels(R.dimen.normal_icon_size) * contactThumbnailsSize).toInt()
                    itemRecentsImage.setHeightAndWidth(size)
                    if (call.phoneNumber == call.name || ((call.isABusinessCall() || call.isVoiceMail) && call.photoUri == "")) {
                        val placeholderRes = when {
                            call.isABusinessCall() -> R.drawable.placeholder_company
                            call.isVoiceMail -> R.drawable.placeholder_voicemail
                            else -> R.drawable.placeholder_contact
                        }

                        val drawable = AppCompatResources.getDrawable(activity, placeholderRes)?.mutate()
                        if (baseConfig.useColoredContacts && drawable is LayerDrawable) {
                            val color = getCachedColorForName(call.name)
                            val backgroundId = when {
                                call.isABusinessCall() -> R.id.placeholder_company_background
                                call.isVoiceMail -> R.id.placeholder_contact_background
                                else -> R.id.placeholder_contact_background
                            }

                            // Use mutate() to create a separate copy, otherwise the colors might get mixed up
                            val backgroundDrawable = drawable.findDrawableByLayerId(backgroundId)?.mutate()
                            backgroundDrawable?.applyColorFilter(color)
                        }
                        itemRecentsImage.setImageDrawable(drawable)
                    } else {
                        SimpleContactsHelper(root.context.applicationContext).loadContactImage(call.photoUri, itemRecentsImage, call.name)
                    }

                    itemRecentsImage.apply {
                        if (profileIconClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    profileIconClick.invoke(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                            setOnLongClickListener {
                                viewLongClicked()
                                true
                            }
                        }
                    }
                }

                val drawable = when {
                    call.blockReason != 0 -> blockedCallIcon
                    call.type == Calls.OUTGOING_TYPE -> outgoingCallIcon
                    call.type == Calls.MISSED_TYPE -> incomingMissedCallIcon
                    else -> incomingCallIcon
                }
                itemRecentsType.setImageDrawable(drawable)

                itemRecentsInfo.apply {
                    beVisibleIf(showOverflowMenu)
                    if (showCallIcon) {
                        setImageResource(R.drawable.ic_phone_vector)
                        setBackgroundResource(R.drawable.circle_background)
                        if (activity.isDynamicTheme() && !activity.isSystemInDarkMode()) {
                            background.setTint(backgroundColor)
                        } else background.setTint(surfaceColor)
                        updateMarginWithBase(marginTen,0,marginTen,0)

                        if (profileInfoClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    swipedCall(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                        }
                        contentDescription = resources.getString(R.string.call)
                    } else {
                        if (profileInfoClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    profileInfoClick.invoke(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                        }
                    }
                    applyColorFilter(accentColor)
                    setOnLongClickListener {
                        showPopupMenu(overflowMenuAnchor, call)
                        true
                    }
                }
                //In order not to miss the icon item_recents_info
                itemRecentsInfoHolder.apply {
                    if (showCallIcon) {
                        if (profileInfoClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    swipedCall(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                        }
                    } else {
                        if (profileInfoClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    profileInfoClick.invoke(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                        }
                    }
                    setOnLongClickListener {
                        showPopupMenu(overflowMenuAnchor, call)
                        true
                    }
                }
            }
        }
    }

    private inner class RecentCallSwipeViewHolder(val binding: ItemRecentCallSwipeBinding) : ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(call: RecentCall) = bindView(
            item = call,
            allowSingleClick = (refreshItemsListener != null || isDialpad) && !call.isUnknownNumber,
            allowLongClick = (refreshItemsListener != null || isDialpad) && !call.isUnknownNumber
        ) { _, _ ->
            binding.apply {
                itemRecentsFrameSelect.setupViewBackground(activity)

                if (activity.isDynamicTheme() && !activity.isSystemInDarkMode()) {
                    itemRecentsFrame.setBackgroundColor(surfaceColor)
                } else itemRecentsFrame.setBackgroundColor(backgroundColor)

                val currentFontSize = fontSize
                itemRecentsHolder.isSelected = selectedKeys.contains(call.id)

                divider.setBackgroundColor(textColor)
                if (getLastItem() == call || !activity.config.useDividers) divider.visibility = View.INVISIBLE else divider.visibility = View.VISIBLE

//                val matchingContact = findContactByCall(call)
                val name = call.name //matchingContact?.getNameToDisplay() ?: call.name
                val formatPhoneNumbers = activity.config.formatPhoneNumbers
                var nameToShow = if (name == call.phoneNumber && formatPhoneNumbers) {
                    SpannableString(name.formatPhoneNumber())
                } else {
                    SpannableString(formatterUnicodeWrap(name))
                }

                if (call.groupedCalls != null) {
                    nameToShow = SpannableString("$nameToShow (${call.groupedCalls.size})")
                }

                if (textToHighlight.isNotEmpty() && nameToShow.contains(textToHighlight, true)) {
                    nameToShow = SpannableString(nameToShow.toString().highlightTextPart(textToHighlight, properPrimaryColor))
                }

                itemRecentsName.apply {
                    text = nameToShow
                    setTextColor(if (call.type == Calls.MISSED_TYPE) missedCallColor else textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize)
                }

                var numberToShow =
                    if (formatPhoneNumbers) SpannableString(call.phoneNumber.formatPhoneNumber()) else SpannableString(call.phoneNumber)
                if (textToHighlight.isNotEmpty() && numberToShow.contains(textToHighlight, true)) {
                    numberToShow = SpannableString(numberToShow.toString().highlightTextPart(textToHighlight, properPrimaryColor))
                }

                itemRecentsNumber.apply {
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                    val recentsNumber = if (call.phoneNumber == call.name) {
                        if (call.isVoiceMail) voiceMail else call.phoneNumber.getCountryByNumber()
                    } else {
                        if (call.specificType.isNotEmpty() && call.specificNumber.isNotEmpty()) call.specificType
                        else {
                            if (formatPhoneNumbers) call.phoneNumber.formatPhoneNumber() else call.phoneNumber
                        }
                    }
                    text = if (name != call.phoneNumber && textToHighlight.isNotEmpty()) numberToShow else formatterUnicodeWrap(recentsNumber)
                }

                itemRecentsDateTime.apply {
                    text = if (activity.config.useRelativeDate) {
                        DateUtils.getRelativeDateTimeString(
                            context,
                            call.startTS,
                            1.minutes.inWholeMilliseconds,
                            2.days.inWholeMilliseconds,
                            0,
                        )
                    } else {
                        call.startTS.formatDateOrTime(context, hideTimeOnOtherDays = hideTimeAtOtherDays, false)
                    }
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                    if (showCallIcon) updateMarginWithBase(marginNormal,0,marginNormal,0)
                }

                itemRecentsDuration.apply {
                    text = context.formatSecondsToShortTimeString(call.duration)
                    setTextColor(textColor)
                    beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                }

                itemRecentsSimImage.beVisibleIf(areMultipleSIMsAvailable)
                itemRecentsSimId.beVisibleIf(areMultipleSIMsAvailable)
                if (areMultipleSIMsAvailable) {
                    val colorSimIcons = activity.config.colorSimIcons
                    val simColor = if (!colorSimIcons) textColor
                                else getAdjustedSimColor(call.simColor)
                    itemRecentsSimImage.applyColorFilter(simColor)
                    itemRecentsSimImage.alpha = if (!colorSimIcons) 0.6f else 1f
                    itemRecentsSimId.setTextColor(simColor.getContrastColor())
                    itemRecentsSimId.text = if (call.simID == -1) "?" else call.simID.toString()
                }

                val showContactThumbnails = activity.config.showContactThumbnails
                itemRecentsImage.beVisibleIf(showContactThumbnails)
                if (showContactThumbnails) {
                    val size = (root.context.pixels(R.dimen.normal_icon_size) * contactThumbnailsSize).toInt()
                    itemRecentsImage.setHeightAndWidth(size)
                    if (call.phoneNumber == call.name || ((call.isABusinessCall() || call.isVoiceMail) && call.photoUri == "")) {
                        val placeholderRes = when {
                            call.isABusinessCall() -> R.drawable.placeholder_company
                            call.isVoiceMail -> R.drawable.placeholder_voicemail
                            else -> R.drawable.placeholder_contact
                        }

                        val drawable = AppCompatResources.getDrawable(activity, placeholderRes)?.mutate()
                        if (baseConfig.useColoredContacts && drawable is LayerDrawable) {
                            val color = getCachedColorForName(call.name)
                            val backgroundId = when {
                                call.isABusinessCall() -> R.id.placeholder_company_background
                                call.isVoiceMail -> R.id.placeholder_contact_background
                                else -> R.id.placeholder_contact_background
                            }

                            // Use mutate() to create a separate copy, otherwise the colors might get mixed up
                            val backgroundDrawable = drawable.findDrawableByLayerId(backgroundId)?.mutate()
                            backgroundDrawable?.applyColorFilter(color)
                        }
                        itemRecentsImage.setImageDrawable(drawable)
                    } else {
                        SimpleContactsHelper(root.context.applicationContext).loadContactImage(call.photoUri, itemRecentsImage, call.name)
                    }

                    itemRecentsImage.apply {
                        if (profileIconClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    profileIconClick.invoke(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                            setOnLongClickListener {
                                viewLongClicked()
                                true
                            }
                        }
                    }
                }

                val drawable = when {
                    call.blockReason != 0 -> blockedCallIcon
                    call.type == Calls.OUTGOING_TYPE -> outgoingCallIcon
                    call.type == Calls.MISSED_TYPE -> incomingMissedCallIcon
                    else -> incomingCallIcon
                }
                itemRecentsType.setImageDrawable(drawable)

                itemRecentsInfo.apply {
                    beVisibleIf(showOverflowMenu)
                    if (showCallIcon) {
                        setImageResource(R.drawable.ic_phone_vector)
                        setBackgroundResource(R.drawable.circle_background)
                        if (activity.isDynamicTheme() && !activity.isSystemInDarkMode()) {
                            background.setTint(backgroundColor)
                        } else background.setTint(surfaceColor)
                        updateMarginWithBase(marginTen,0,marginTen,0)

                        if (profileInfoClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    swipedCall(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                        }
                        contentDescription = resources.getString(R.string.call)
                    } else {
                        if (profileInfoClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    profileInfoClick.invoke(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                        }
                    }
                    applyColorFilter(accentColor)
                    setOnLongClickListener {
                        showPopupMenu(overflowMenuAnchor, call)
                        true
                    }
                }
                //In order not to miss the icon item_recents_info
                itemRecentsInfoHolder.apply {
                    if (showCallIcon) {
                        if (profileInfoClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    swipedCall(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                        }
                    } else {
                        if (profileInfoClick != null) {
                            setOnClickListener {
                                if (!actModeCallback.isSelectable) {
                                    profileInfoClick.invoke(call)
                                } else {
                                    viewClicked(call)
                                }
                            }
                        }
                    }
                    setOnLongClickListener {
                        showPopupMenu(overflowMenuAnchor, call)
                        true
                    }
                }

                //swipe
                val isRTL = activity.isRTLLayout
                val swipeLeftAction = if (isRTL) activity.config.swipeRightAction else activity.config.swipeLeftAction
                swipeLeftIcon.setImageResource(swipeActionImageResource(swipeLeftAction))
                swipeLeftIcon.setColorFilter(properPrimaryColor.getContrastColor())
                swipeLeftIconHolder.setBackgroundColor(swipeActionColor(call, swipeLeftAction))

                val swipeRightAction = if (isRTL) activity.config.swipeLeftAction else activity.config.swipeRightAction
                swipeRightIcon.setImageResource(swipeActionImageResource(swipeRightAction))
                swipeRightIcon.setColorFilter(properPrimaryColor.getContrastColor())
                swipeRightIconHolder.setBackgroundColor(swipeActionColor(call, swipeRightAction))

                itemRecentsHolder.setDirectionEnabled(SwipeDirection.Left, swipeLeftAction != SWIPE_ACTION_NONE)
                itemRecentsHolder.setDirectionEnabled(SwipeDirection.Right, swipeRightAction != SWIPE_ACTION_NONE)

                val halfScreenWidth = activity.resources.displayMetrics.widthPixels / 2
                val swipeWidth = activity.resources.getDimension(com.goodwy.commons.R.dimen.swipe_width)
                if (swipeWidth > halfScreenWidth) {
                    swipeRightIconHolder.setWidth(halfScreenWidth)
                    swipeLeftIconHolder.setWidth(halfScreenWidth)
                }

                if (activity.config.swipeRipple) {
                    itemRecentsHolder.setRippleColor(SwipeDirection.Left, swipeActionColor(call, swipeLeftAction))
                    itemRecentsHolder.setRippleColor(SwipeDirection.Right, swipeActionColor(call, swipeRightAction))
                }

                itemRecentsHolder.useHapticFeedback = activity.config.swipeVibration
                itemRecentsHolder.swipeGestureListener = object : SwipeGestureListener {
                    override fun onSwipedLeft(swipeActionView: SwipeActionView): Boolean {
                        finishActMode()
                        val swipeLeftOrRightAction =
                            if (activity.isRTLLayout) activity.config.swipeRightAction else activity.config.swipeLeftAction
                        swipeAction(swipeLeftOrRightAction, call)
                        swipeLeftIcon.slideLeftReturn(swipeLeftIconHolder)
                        return true
                    }

                    override fun onSwipedRight(swipeActionView: SwipeActionView): Boolean {
                        finishActMode()
                        val swipeRightOrLeftAction =
                            if (activity.isRTLLayout) activity.config.swipeLeftAction else activity.config.swipeRightAction
                        swipeAction(swipeRightOrLeftAction, call)
                        swipeRightIcon.slideRightReturn(swipeRightIconHolder)
                        return true
                    }

                    override fun onSwipedActivated(swipedRight: Boolean) {
                        if (swipedRight) swipeRightIcon.slideRight(swipeRightIconHolder)
                        else swipeLeftIcon.slideLeft()
                    }

                    override fun onSwipedDeactivated(swipedRight: Boolean) {
                        if (swipedRight) swipeRightIcon.slideRightReturn(swipeRightIconHolder)
                        else swipeLeftIcon.slideLeftReturn(swipeLeftIconHolder)
                    }
                }
            }
        }
    }

    private fun getAdjustedSimColor(simColor: Int): Int {
        return cachedSimColors.getOrPut(simColor) {
            simColor
        }
    }

    private inner class RecentCallDateViewHolder(val binding: ItemRecentsDateBinding) : ViewHolder(binding.root) {
        fun bind(date: CallLogItem.Date) {
            binding.dateTextView.apply {
                setTextColor(secondaryTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.76f)

                val now = DateTime.now()
                text = when (date.dayCode) {
                    now.millis.getDayCode() -> resources.getString(R.string.today)
                    now.minusDays(1).millis.getDayCode() -> resources.getString(R.string.yesterday)
                    else -> date.timestamp.formatDateOrTime(
                        context = activity,
                        hideTimeOnOtherDays = true,
                        showCurrentYear = false
                    )
                }
            }
        }
    }

    private fun showCallHistory(call: RecentCall) {
//        val callIdList : ArrayList<Int> = arrayListOf()
//        for (i in getCallList(call)){ callIdList.add(i.id) } // add all the individual records
//        for (n in getCallList(call)){ callIdList.addAll(n.neighbourIDs) } // add all grouped records
        val recentCalls = call.groupedCalls as ArrayList<RecentCall>? ?: arrayListOf(call)
        val matchingContact = findContactByCall(call)
        Intent(activity, CallHistoryActivity::class.java).apply {
            putExtra(CURRENT_RECENT_CALL, call)
            putExtra(CURRENT_RECENT_CALL_LIST, recentCalls)
            putExtra(CONTACT_ID, call.contactID)
            if (matchingContact != null) {
                putExtra(IS_PRIVATE, matchingContact.isPrivate())
            }
            activity.launchActivityIntent(this)
        }
    }

    private fun swipeActionImageResource(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> com.goodwy.commons.R.drawable.ic_delete_outline
            SWIPE_ACTION_MESSAGE -> R.drawable.ic_messages
            SWIPE_ACTION_BLOCK -> R.drawable.ic_block_vector
            else -> R.drawable.ic_phone_vector
        }
    }

    private fun swipeActionColor(call: RecentCall, swipeAction: Int): Int {
        val defaultSim = if (activity.config.currentSIMCardIndex == 0) 1 else 2
        // if we're calling with the same SIM that was used to call us then highlight using that SIMs color
        // but only if we have the sim ID
        val simIndex: Int = if (call.simID > 0 && call.simID < 5 && activity.config.callUsingSameSim) call.simID else defaultSim
        val simColor = activity.config.simIconsColors[simIndex]
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> resources.getColor(R.color.red_call, activity.theme)
            SWIPE_ACTION_MESSAGE -> resources.getColor(R.color.ic_messages, activity.theme)
            SWIPE_ACTION_BLOCK -> resources.getColor(R.color.swipe_purple, activity.theme)
            else -> simColor
        }
    }

    private fun swipeAction(swipeAction: Int, call: RecentCall) {
        when (swipeAction) {
            SWIPE_ACTION_DELETE -> swipedDelete(call)
            SWIPE_ACTION_MESSAGE -> swipedSMS(call)
            SWIPE_ACTION_BLOCK -> swipedBlock(call)
            else -> swipedCall(call)
        }
    }

    private fun swipedDelete(call: RecentCall) {
        selectedKeys.add(call.id)
        if (activity.config.skipDeleteConfirmation) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                if (it) removeRecents()
            }
        } else askConfirmRemove()
    }

    private fun swipedSMS(call: RecentCall) {
        activity.launchSendSMSIntentRecommendation(call.phoneNumber)
    }

    private fun swipedBlock(call: RecentCall) {
        if (call.isUnknownNumber) return
        selectedKeys.add(call.id)
        if (!activity.isNumberBlocked(call.phoneNumber, getBlockedNumbers)) askConfirmBlock() else askConfirmUnblock()
    }

    private fun swipedCall(call: RecentCall) {
        if (activity.config.showCallConfirmation) {
            CallConfirmationDialog(activity as SimpleActivity, call.name) {
                callRecentNumber(call)
            }
        } else {
            callRecentNumber(call)
        }
    }

    private fun callRecentNumber(recentCall: RecentCall) {
        if (activity.config.callUsingSameSim && recentCall.simID > 0) {
            val sim = recentCall.simID == 1
            activity.callContactWithSim(recentCall.phoneNumber, sim)
        }
        else {
            activity.launchCallIntent(recentCall.phoneNumber, key = BuildConfig.RIGHT_APP_KEY)
        }
    }

    private fun getCachedColorForName(name: String): Int {
        return colorCache.getOrPut(name) {
            val letterBackgroundColors = activity.getLetterBackgroundColors()
            letterBackgroundColors[abs(name.hashCode()) % letterBackgroundColors.size].toInt()
        }
    }

//    fun clearColorCache() {
//        colorCache.clear()
//    }
}

class RecentCallsDiffCallback : DiffUtil.ItemCallback<CallLogItem>() {

    override fun areItemsTheSame(oldItem: CallLogItem, newItem: CallLogItem) = oldItem.getItemId() == newItem.getItemId()

    override fun areContentsTheSame(oldItem: CallLogItem, newItem: CallLogItem): Boolean {
        return when {
            oldItem is CallLogItem.Date && newItem is CallLogItem.Date -> oldItem.timestamp == newItem.timestamp && oldItem.dayCode == newItem.dayCode
            oldItem is RecentCall && newItem is RecentCall -> {
                oldItem.phoneNumber == newItem.phoneNumber &&
                    oldItem.name == newItem.name &&
                    oldItem.photoUri == newItem.photoUri &&
                    oldItem.startTS == newItem.startTS &&
                    oldItem.duration == newItem.duration &&
                    oldItem.type == newItem.type &&
                    oldItem.simID == newItem.simID &&
                    oldItem.specificNumber == newItem.specificNumber &&
                    oldItem.specificType == newItem.specificType &&
                    oldItem.isUnknownNumber == newItem.isUnknownNumber &&
                    oldItem.groupedCalls?.size == newItem.groupedCalls?.size
            }

            else -> false
        }
    }
}
