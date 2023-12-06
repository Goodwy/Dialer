package com.goodwy.dialer.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.CallLog.Calls
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.bumptech.glide.Glide
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.CallHistoryActivity
import com.goodwy.dialer.activities.MainActivity
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.databinding.ItemRecentCallBinding
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.CURRENT_RECENT_CALL
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.interfaces.RefreshItemsListener
import com.goodwy.dialer.models.RecentCall
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class RecentCallsAdapter(
    activity: SimpleActivity,
    private var recentCalls: MutableList<RecentCall>,
    recyclerView: MyRecyclerView,
    private val refreshItemsListener: RefreshItemsListener?,
    private val showOverflowMenu: Boolean = false,
    private val hideTimeAtOtherDays: Boolean = false,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    //private lateinit var infoIcon: Drawable
    var fontSize: Float = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val redColor = resources.getColor(R.color.red_missed) //md_red_700
    private var textToHighlight = ""
    //private var durationPadding = resources.getDimension(R.dimen.normal_margin).toInt()

    init {
        initDrawables()
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val selectedItems = getSelectedItems()
        val isOneItemSelected = selectedItems.size == 1
        val selectedNumber = "tel:${getSelectedPhoneNumber()}".replace("+","%2B")

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""

            findItem(R.id.cab_block_number).title = if (isOneItemSelected) activity.getString(R.string.block_number) else activity.getString(R.string.block_numbers)
            findItem(R.id.cab_block_number).isVisible = isNougatPlus() && (isAllUnblockedNumbers() && !isAllBlockedNumbers())
            findItem(R.id.cab_unblock_number).title = if (isOneItemSelected) activity.getString(R.string.unblock_number) else activity.getString(R.string.unblock_numbers)
            findItem(R.id.cab_unblock_number).isVisible = isNougatPlus() && (isAllBlockedNumbers() && !isAllUnblockedNumbers())
            findItem(R.id.cab_add_number).isVisible = isOneItemSelected
            findItem(R.id.cab_show_call_details).isVisible = isOneItemSelected
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected
            findItem(R.id.cab_view_details)?.isVisible = isOneItemSelected && findContactByCall(selectedItems.first()) != null
        }
    }

    private fun isAllBlockedNumbers(): Boolean {
        getSelectedItems().map { it.phoneNumber }.forEach { number ->
            if (activity.isNumberBlocked(number, activity.getBlockedNumbers())) return true
        }
        return false
    }

    private fun isAllUnblockedNumbers(): Boolean {
        getSelectedItems().map { it.phoneNumber }.forEach { number ->
            if (!activity.isNumberBlocked(number, activity.getBlockedNumbers())) return true
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
            R.id.cab_remove -> askConfirmRemove()
            R.id.cab_select_all -> selectAll()
            R.id.cab_view_details -> launchContactDetailsIntent(findContactByCall(getSelectedItems().first()))
        }
    }

    override fun getSelectableItemCount() = recentCalls.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recentCalls.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recentCalls.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemRecentCallBinding.inflate(layoutInflater, parent, false).root)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recentCall = recentCalls[position]
        holder.bindView(
            any = recentCall,
            allowSingleClick = refreshItemsListener != null && !recentCall.isUnknownNumber,
            allowLongClick = refreshItemsListener != null && !recentCall.isUnknownNumber
        ) { itemView, _ ->
            val binding = ItemRecentCallBinding.bind(itemView)
            setupView(binding, recentCall)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recentCalls.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            ItemRecentCallBinding.bind(holder.itemView).apply {
                Glide.with(activity).clear(itemRecentsImage)
            }
        }
    }

    fun initDrawables(newColor: Int = textColor) {
        outgoingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_outgoing_call_vector, newColor)
        incomingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_incoming_call_vector, newColor)
        incomingMissedCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_missed_call_vector, newColor)
        //infoIcon = resources.getColoredDrawableWithColor(R.drawable.ic_info, properPrimaryColor)
    }

    private fun callContact(useSimOne: Boolean) {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.callContactWithSim(phoneNumber, useSimOne)
    }

    private fun callContact(prefix: String = "") {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        (activity as SimpleActivity).startCallIntent("$prefix$phoneNumber")
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber()?.replace("+","%2B") ?: return
        activity.config.removeCustomSIM("tel:$phoneNumber")
        finishActMode()
    }

//    private fun tryBlocking() {
//        /*if (activity.isOrWasThankYouInstalled()) {
//            askConfirmBlock()
//        } else {
//            FeatureLockedDialog(activity) { }
//        }*/
//        askConfirmBlock()
//    }

    private fun askConfirmBlock() {
        if (activity.isDefaultDialer()) {
            val numbers = TextUtils.join(", ", getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
            val baseString = R.string.block_confirmation
            val question = String.format(resources.getString(baseString), numbers)

            ConfirmationDialog(activity, question) {
                blockNumbers()
            }
        } else activity.toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()
        val positions = getSelectedItemPositions()
        recentCalls.removeAll(callsToBlock.toSet())

        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                if (!activity.config.showBlockedNumbers) removeSelectedItems(positions)
                finishActMode()
            }
        }
    }

    private fun askConfirmUnblock() {
        if (activity.isDefaultDialer()) {
            val numbers = TextUtils.join(", ", getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
            val baseString = R.string.unblock_confirmation
            val question = String.format(resources.getString(baseString), numbers)

            ConfirmationDialog(activity, question) {
                unblockNumbers()
            }
        } else activity.toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
    }

    private fun unblockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()
        //val positions = getSelectedItemPositions()
        recentCalls.removeAll(callsToBlock.toSet())

        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.deleteBlockedNumber(number)
            }

            activity.runOnUiThread {
                //removeSelectedItems(positions)
                finishActMode()
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun sendSMS() {
        val numbers = getSelectedItems().map { it.phoneNumber }
        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntentRecommendation(recipient)
    }

    private fun showCallDetails() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
//        val callIds = recentCall.neighbourIDs.map { it }.toMutableList() as ArrayList<Int>
//        callIds.add(recentCall.id)
//        ShowGroupedCallsDialog(activity, callIds)
        showCallHistory(recentCall)
    }

    private fun copyNumber() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(recentCall.phoneNumber)
        finishActMode()
    }

    private fun askConfirmRemove() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.neighbourIDs.mapTo(idsToRemove, { it })
        }

        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            recentCalls.removeAll(callsToRemove.toSet())
            activity.runOnUiThread {
                refreshItemsListener?.refreshItems()
                if (recentCalls.isEmpty()) {
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as MainActivity).cachedContacts.find { it.name == recentCall.name && it.doesHavePhoneNumber(recentCall.phoneNumber) }
    }

    private fun launchContactDetailsIntent(contact: Contact?) {
        if (contact != null) {
            activity.startContactDetailsIntentRecommendation(contact)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<RecentCall>, highlightText: String = "") {
        if (newItems.hashCode() != recentCalls.hashCode()) {
            recentCalls = newItems.toMutableList()
            textToHighlight = highlightText
            recyclerView.resetItemCount()
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun getSelectedItems() = recentCalls.filter { selectedKeys.contains(it.id) } as ArrayList<RecentCall>

    private fun getLastItem() = recentCalls.last()

    private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumber

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupView(binding: ItemRecentCallBinding, call: RecentCall) {
        binding.apply {
            val currentFontSize = fontSize
            itemRecentsHolder.isSelected = selectedKeys.contains(call.id)

            divider.setBackgroundColor(textColor)
            if (getLastItem() == call || !activity.config.useDividers) divider.visibility = View.INVISIBLE else divider.visibility = View.VISIBLE

            //val name = findContactByCall(call)?.getNameToDisplay() ?: call.name
            var nameToShow = SpannableString(call.name)

            if (nameToShow.startsWith("+")) nameToShow = SpannableString(getPhoneNumberFormat(activity, number = nameToShow.toString()))
            if (call.neighbourIDs.isNotEmpty()) {
                nameToShow = SpannableString("$nameToShow (${call.neighbourIDs.size + 1})")
            }

            if (textToHighlight.isNotEmpty() && nameToShow.contains(textToHighlight, true)) {
                nameToShow = SpannableString(nameToShow.toString().highlightTextPart(textToHighlight, properPrimaryColor))
            }

            itemRecentsName.apply {
                val name = nameToShow.toString()
                text = formatterUnicodeWrap(name)
                setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor) //(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize)
            }

            itemRecentsNumber.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                val recentsNumber = if (call.phoneNumber == call.name) {
                    val country = if (call.phoneNumber.startsWith("+")) getCountryByNumber(activity, call.phoneNumber) else ""
                    country
                } else {
                    val phoneNumber = if (call.phoneNumber.startsWith("+")) getPhoneNumberFormat(activity, number = call.phoneNumber) else call.phoneNumber
                    if (call.specificType.isNotEmpty() && call.specificNumber.isNotEmpty()) call.specificType else phoneNumber
                    //setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                }
                text = formatterUnicodeWrap(recentsNumber)
            }

            itemRecentsDateTime.apply {
                val relativeDate = DateUtils.getRelativeDateTimeString(
                    context,
                    call.startTS * 1000L,
                    1.minutes.inWholeMilliseconds,
                    2.days.inWholeMilliseconds,
                    0,
                )
                val date = call.startTS.formatDateOrTime(context, hideTimeAtOtherDays = hideTimeAtOtherDays, false)
                text = if (activity.config.useRelativeDate) relativeDate else date
                //setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
            }

            itemRecentsDuration.apply {
                text = call.duration.getFormattedDuration()
                setTextColor(textColor)
                beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
//                if (!showOverflowMenu) {
//                    itemRecentsDuration.setPadding(0, 0, durationPadding, 0)
//                }
            }

            itemRecentsSimImage.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
            itemRecentsSimId.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
            if (areMultipleSIMsAvailable && call.simID != -1) {
                val simColor = if (!activity.config.colorSimIcons) textColor
                else {
                    when (call.simID) {
                        1 -> activity.config.simIconsColors[1]
                        2 -> activity.config.simIconsColors[2]
                        3 -> activity.config.simIconsColors[3]
                        4 -> activity.config.simIconsColors[4]
                        else -> activity.config.simIconsColors[0]
                    }
                }
                itemRecentsSimImage.applyColorFilter(simColor)
                itemRecentsSimImage.alpha = if (!activity.config.colorSimIcons) 0.6f else 1f
                itemRecentsSimId.setTextColor(simColor.getContrastColor())
                itemRecentsSimId.text = call.simID.toString()
            }

            val showContactThumbnails = activity.config.showContactThumbnails
            itemRecentsImage.beVisibleIf(showContactThumbnails)
            itemRecentsImageIcon.beVisibleIf(showContactThumbnails)
            if (showContactThumbnails) {
                if (call.phoneNumber == call.name) {
                    SimpleContactsHelper(root.context.applicationContext).loadContactImage(call.photoUri, itemRecentsImage, call.name, letter = false)
                    itemRecentsImageIcon.beVisibleIf(call.photoUri == "")
                } else {
                    SimpleContactsHelper(root.context.applicationContext).loadContactImage(call.photoUri, itemRecentsImage, call.name)
                    itemRecentsImageIcon.beGone()
                }
            }

            val drawable = when (call.type) {
                Calls.OUTGOING_TYPE -> outgoingCallIcon
                Calls.MISSED_TYPE -> incomingMissedCallIcon
                else -> incomingCallIcon
            }
            itemRecentsType.setImageDrawable(drawable)

            itemRecentsInfo.apply {
                beVisibleIf(showOverflowMenu)
                applyColorFilter(accentColor)
                setOnClickListener {
                    showCallHistory(call)
                }
                setOnLongClickListener {
                    showPopupMenu(overflowMenuAnchor, call)
                    true
                }
            }
            //In order not to miss the icon item_recents_info
            itemRecentsInfoHolder.apply {
                setOnClickListener {
                    showCallHistory(call)
                }
                setOnLongClickListener {
                    showPopupMenu(overflowMenuAnchor, call)
                    true
                }
            }

//            overflowMenuIcon.beVisibleIf(showOverflowMenu)
//            overflowMenuIcon.drawable.apply {
//                mutate()
//                setTint(activity.getProperTextColor())
//            }
//            overflowMenuIcon.setOnClickListener {
//                showPopupMenu(overflowMenuAnchor, call)
//            }
        }
    }

    private fun showPopupMenu(view: View, call: RecentCall) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)
        val contact = findContactByCall(call)
        val selectedNumber = "tel:${call.phoneNumber}".replace("+","%2B")

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
                findItem(R.id.cab_show_call_details).isVisible = !call.isUnknownNumber
                //findItem(R.id.cab_block_number).title = activity.getString(R.string.block_number)
                findItem(R.id.cab_block_number).isVisible = isNougatPlus() && !call.isUnknownNumber && !activity.isNumberBlocked(call.phoneNumber)
                //findItem(R.id.cab_unblock_number).title = activity.getString(R.string.unblock_number)
                findItem(R.id.cab_unblock_number).isVisible = isNougatPlus() && !call.isUnknownNumber && activity.isNumberBlocked(call.phoneNumber)
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
                            val text = String.format(activity.getString(R.string.call_anonymously_warning), phoneNumber)
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

//    private fun getCallList(call: RecentCall) = recentCalls.filter { it.phoneNumber == call.phoneNumber}.toMutableList() as ArrayList<RecentCall>

    private fun showCallHistory(call: RecentCall) {
//        val callIdList : ArrayList<Int> = arrayListOf()
//        for (i in getCallList(call)){ callIdList.add(i.id) } // add all the individual records
//        for (n in getCallList(call)){ callIdList.addAll(n.neighbourIDs) } // add all grouped records
        Intent(activity, CallHistoryActivity::class.java).apply {
            putExtra(CURRENT_PHONE_NUMBER, call.phoneNumber)
            putExtra(CURRENT_RECENT_CALL, call.id)
            putExtra(CONTACT_ID, call.contactID)
            activity.launchActivityIntent(this)
        }
    }

//    private fun confirmRemove(call: RecentCall) {
//        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
//            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
//                removeRecent(call)
//            }
//        }
//    }
//
//    private fun removeRecent(call: RecentCall) {
//       /*if (selectedKeys.isEmpty()) {
//            return
//        }*/
//
//        val callsToRemove = ArrayList<RecentCall>()
//        callsToRemove.add(call)
//        val positions = ArrayList<Int>(0)
//        val idsToRemove = ArrayList<Int>()
//        idsToRemove.add(call.id)
//        /*callsToRemove.forEach {
//            idsToRemove.add(it.id)
//            it.neighbourIDs.mapTo(idsToRemove, { it })
//        }*/
//
//        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
//            recentCalls.removeAll(callsToRemove)
//            (activity as CallHistoryActivity).refreshItems()
//            /*activity.runOnUiThread {
//                removeSelectedItems(idsToRemove)
//                refreshItemsListener?.refreshItems()
//                (activity as CallHistoryActivity).refreshItems()
//                finishActMode()
//            }*/
//        }
//    }


    /*private fun viewContactInfo(contact: SimpleContact) {
        activity.startContactDetailsIntent(contact)
    }*/
}
