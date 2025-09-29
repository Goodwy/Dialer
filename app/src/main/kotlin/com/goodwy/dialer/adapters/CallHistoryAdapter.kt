package com.goodwy.dialer.adapters

import android.annotation.SuppressLint
import android.os.Build
import android.provider.CallLog.Calls
import android.util.TypedValue
import android.view.*
import android.widget.PopupMenu
import androidx.annotation.RequiresApi
import com.goodwy.commons.adapters.MyRecyclerViewListAdapter
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.databinding.ItemCallHistoryBinding
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.interfaces.RefreshItemsListener
import com.goodwy.dialer.models.CallLogItem
import com.goodwy.dialer.models.RecentCall

class CallHistoryAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    private val refreshItemsListener: RefreshItemsListener?,
    private val hideTimeAtOtherDays: Boolean = false,
    val itemDelete: (List<RecentCall>) -> Unit = {},
    itemClick: (Any) -> Unit,
) : MyRecyclerViewListAdapter<CallLogItem>(activity, recyclerView, RecentCallsDiffCallback(), itemClick) {

    private lateinit var outgoingCallText: String
    private lateinit var incomingCallText: String
    private lateinit var incomingMissedCallText: String
    private var fontSize: Float = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val missedCallColor = resources.getColor(R.color.red_missed)

    private val colorSimIcons = activity.config.colorSimIcons
    private val simIconsColors = activity.config.simIconsColors

    init {
        initString()
        setupDragListener(true)
        setHasStableIds(true)
        recyclerView.itemAnimator?.changeDuration = 0
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }
    }

    override fun getItemId(position: Int) = currentList[position].getItemId().toLong()

    override fun getSelectableItemCount() = currentList.filterIsInstance<RecentCall>().size

    override fun getIsItemSelectable(position: Int) = currentList[position] is RecentCall

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.getItemId()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.getItemId() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun getItemViewType(position: Int): Int {
        return VIEW_TYPE_CALL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = when (viewType) {
            VIEW_TYPE_CALL -> RecentCallViewHolder(
                ItemCallHistoryBinding.inflate(layoutInflater, parent, false)
            )

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }

        return viewHolder
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val callRecord = currentList[position]
        if (holder is RecentCallViewHolder)  holder.bind(callRecord as RecentCall)

        bindViewHolder(holder)
    }

//    override fun onViewRecycled(holder: ViewHolder) {
//        super.onViewRecycled(holder)
//    }

    private fun initString() {
        outgoingCallText = resources.getString(R.string.outgoing_call)
        incomingCallText = resources.getString(R.string.incoming_call)
        incomingMissedCallText = resources.getString(R.string.missed_call)
    }

    private fun askConfirmRemove() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                if (it) removeRecents()
            }
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
            val recentCalls = currentList.toMutableList().also { it.removeAll(callsToRemove) }
            activity.runOnUiThread {
                refreshItemsListener?.refreshItems()
                submitList(recentCalls)
                finishActMode()
//                (activity as? CallHistoryActivity)?.refreshItems()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<RecentCall>) {
        submitList(newItems)
    }

    fun getSelectedItems() = currentList.filterIsInstance<RecentCall>()
        .filter { selectedKeys.contains(it.getItemId()) }

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
                            activity.copyToClipboard(call.startTS.formatDateOrTime(activity, hideTimeOnOtherDays = hideTimeAtOtherDays, false))
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

    private inner class RecentCallViewHolder(val binding: ItemCallHistoryBinding) : ViewHolder(binding.root) {
        fun bind(call: RecentCall) = bindView(
            item = call,
            allowSingleClick = refreshItemsListener != null && !call.isUnknownNumber,
            allowLongClick = refreshItemsListener != null && !call.isUnknownNumber
        ) { _, _ ->
            binding.apply {
                val currentFontSize = fontSize

                itemRecentsDateTime.apply {
                    val date = call.startTS.formatDateOrTime(context, hideTimeOnOtherDays = hideTimeAtOtherDays, false)
                    text = date
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.7f)
                }

                itemRecentsDuration.apply {
                    text = call.duration.getFormattedDuration()
                    setTextColor(textColor)
                    beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.7f)
                }

                itemRecentsSimImage.beVisibleIf(areMultipleSIMsAvailable)
                itemRecentsSimId.beVisibleIf(areMultipleSIMsAvailable)
                if (areMultipleSIMsAvailable) {
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
                    itemRecentsSimId.text = if (call.simID == -1) "?" else call.simID.toString()
                }

                val type = when (call.type) {
                    Calls.OUTGOING_TYPE -> outgoingCallText
                    Calls.MISSED_TYPE -> incomingMissedCallText
                    else -> incomingCallText
                }
                val features = when (call.features) {
                    Calls.FEATURES_HD_CALL -> " (HD)"
                    Calls.FEATURES_PULLED_EXTERNALLY -> " (Externally)"
                    Calls.FEATURES_RTT -> " (RTT)"
                    Calls.FEATURES_VIDEO -> " (Video)"
                    Calls.FEATURES_VOLTE, 256 -> " (VoLTE)"
                    Calls.FEATURES_WIFI -> " (Wi-Fi)"
                    else -> ""
                }
                itemRecentsTypeName.apply {
                    text = type + features
                    setTextColor(if (call.type == Calls.MISSED_TYPE) missedCallColor else textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                }

                itemRecentsFrame.setOnClickListener {
                    showPopupMenu(overflowMenuAnchor, call)
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_CALL = 1
    }
}
