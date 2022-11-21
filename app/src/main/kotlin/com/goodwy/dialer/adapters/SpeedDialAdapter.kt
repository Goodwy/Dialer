package com.goodwy.dialer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.interfaces.RemoveSpeedDialListener
import com.goodwy.dialer.models.SpeedDial
import kotlinx.android.synthetic.main.item_speed_dial.view.*
import java.util.*

class SpeedDialAdapter(
    activity: SimpleActivity, var speedDialValues: ArrayList<SpeedDial>, private val removeListener: RemoveSpeedDialListener,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_delete_only

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> deleteSpeedDial()
        }
    }

    override fun getSelectableItemCount() = speedDialValues.size

    override fun getIsItemSelectable(position: Int) = speedDialValues[position].isValid()

    override fun getItemSelectionKey(position: Int) = speedDialValues.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = speedDialValues.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_speed_dial, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val speedDial = speedDialValues[position]
        holder.bindView(speedDial, true, true) { itemView, layoutPosition ->
            setupView(itemView, speedDial)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = speedDialValues.size

    private fun getSelectedItems() = speedDialValues.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<SpeedDial>

    private fun deleteSpeedDial() {
        val ids = getSelectedItems().map { it.id }.toMutableList() as ArrayList<Int>
        removeListener.removeSpeedDial(ids)
        finishActMode()
    }

    private fun setupView(view: View, speedDial: SpeedDial) {
        view.apply {
            var displayName = "${speedDial.id}. "
            displayName += if (speedDial.isValid()) speedDial.displayName else ""

            speed_dial_label.apply {
                text = displayName
                isSelected = selectedKeys.contains(speedDial.hashCode())
                setTextColor(textColor)
            }
        }
    }
}
