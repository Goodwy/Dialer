package com.goodwy.dialer.adapters

import android.annotation.SuppressLint
import android.os.Build
import android.provider.CallLog.Calls
import android.text.SpannableString
import android.util.TypedValue
import android.view.*
import android.widget.PopupMenu
import androidx.annotation.RequiresApi
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.CallHistoryActivity
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.databinding.ItemCallHistoryBinding
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.interfaces.RefreshItemsListener
import com.goodwy.dialer.models.RecentCall

class CallHistoryAdapter(
    activity: SimpleActivity,
    private var recentCalls: MutableList<RecentCall>,
    recyclerView: MyRecyclerView,
    private val refreshItemsListener: RefreshItemsListener?,
    private val hideTimeAtOtherDays: Boolean = false,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private lateinit var outgoingCallText: String
    private lateinit var incomingCallText: String
    private lateinit var incomingMissedCallText: String
    private var fontSize: Float = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val redColor = resources.getColor(R.color.red_missed)
    private var textToHighlight = ""

    private val colorSimIcons = activity.config.colorSimIcons
    private val simIconsColors = activity.config.simIconsColors

    init {
        initString()
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }
    }

    override fun getSelectableItemCount() = recentCalls.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recentCalls.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recentCalls.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemCallHistoryBinding.inflate(layoutInflater, parent, false).root)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recentCall = recentCalls[position]
        holder.bindView(
            any = recentCall,
            allowSingleClick = refreshItemsListener != null && !recentCall.isUnknownNumber,
            allowLongClick = refreshItemsListener != null && !recentCall.isUnknownNumber
        ) { itemView, _ ->
            val binding = ItemCallHistoryBinding.bind(itemView)
            setupView(binding, recentCall)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recentCalls.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
    }

    private fun initString() {
        outgoingCallText = resources.getString(R.string.outgoing_call)
        incomingCallText = resources.getString(R.string.incoming_call)
        incomingMissedCallText = resources.getString(R.string.missed_call)
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
                    (activity as? CallHistoryActivity)?.refreshItems()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as CallHistoryActivity).allContacts.find { it.name == recentCall.name && it.doesHavePhoneNumber(recentCall.phoneNumber) }
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

    fun getSelectedItems() = recentCalls.filter { selectedKeys.contains(it.id) } as ArrayList<RecentCall>

    private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumber

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupView(binding: ItemCallHistoryBinding, call: RecentCall) {
        binding.apply {
            val currentFontSize = fontSize
            //itemRecentsHolder.isSelected = selectedKeys.contains(call.id)

            //val name = findContactByCall(call)?.getNameToDisplay() ?: call.name
            var nameToShow = SpannableString(call.name)

            if (nameToShow.startsWith("+")) nameToShow = SpannableString(getPhoneNumberFormat(activity, number = nameToShow.toString()))
            if (call.neighbourIDs.isNotEmpty()) {
                nameToShow = SpannableString("$nameToShow (${call.neighbourIDs.size + 1})")
            }

            if (textToHighlight.isNotEmpty() && nameToShow.contains(textToHighlight, true)) {
                nameToShow = SpannableString(nameToShow.toString().highlightTextPart(textToHighlight, properPrimaryColor))
            }

            itemRecentsDateTime.apply {
//                val relativeDate = DateUtils.getRelativeDateTimeString(
//                    context,
//                    call.startTS * 1000L,
//                    1.minutes.inWholeMilliseconds,
//                    2.days.inWholeMilliseconds,
//                    0,
//                )
                val date = call.startTS.formatDateOrTime(context, hideTimeAtOtherDays = hideTimeAtOtherDays, false)
//                text = if (activity.config.useRelativeDate) relativeDate else date
                text = date
                //setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
            }

            itemRecentsDuration.apply {
                text = call.duration.getFormattedDuration()
                setTextColor(textColor)
                beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
            }

            itemRecentsSimImage.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
            itemRecentsSimId.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
            if (areMultipleSIMsAvailable && call.simID != -1) {
                val simColor = if (!colorSimIcons) textColor
                else {
                    when (call.simID) {
                        1 -> simIconsColors[1]
                        2 -> simIconsColors[2]
                        3 -> simIconsColors[3]
                        4 -> simIconsColors[4]
                        else -> simIconsColors[0]
                    }
                }
                itemRecentsSimImage.applyColorFilter(simColor)
                itemRecentsSimImage.alpha = if (!colorSimIcons) 0.6f else 1f
                itemRecentsSimId.setTextColor(simColor.getContrastColor())
                itemRecentsSimId.text = call.simID.toString()
            }

            val type = when (call.type) {
                Calls.OUTGOING_TYPE -> outgoingCallText
                Calls.MISSED_TYPE -> incomingMissedCallText
                else -> incomingCallText
            }
            itemRecentsTypeName.apply {
                text = type
                setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
            }

            itemRecentsFrame.setOnClickListener {
                showPopupMenu(overflowMenuAnchor, call)
            }
        }
    }

    private fun showPopupMenu(view: View, call: RecentCall) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(R.menu.menu_call_history_item_options)
            setOnMenuItemClickListener { item ->
                val callId = call.id
                when (item.itemId) {
                    R.id.cab_remove -> {
                        selectedKeys.add(callId)
                        askConfirmRemove()
                    }

                    R.id.cab_copy_date -> {
                        executeItemMenuOperation(callId) {
                            activity.copyToClipboard(call.startTS.formatDateOrTime(activity, hideTimeAtOtherDays = hideTimeAtOtherDays, false))
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
}
