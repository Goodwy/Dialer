package com.goodwy.dialer.adapters

import android.telecom.Call
import android.view.Menu
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.extensions.applyColorFilter
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.helpers.LOWER_ALPHA
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.extensions.hasCapability
import com.goodwy.dialer.helpers.getCallContact
import kotlinx.android.synthetic.main.item_conference_call.view.*

class ConferenceCallsAdapter(
    activity: SimpleActivity, recyclerView: MyRecyclerView, val data: ArrayList<Call>, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    override fun actionItemPressed(id: Int) {}

    override fun getActionMenuId(): Int = 0

    override fun getIsItemSelectable(position: Int): Boolean = false

    override fun getItemCount(): Int = data.size

    override fun getItemKeyPosition(key: Int): Int = -1

    override fun getItemSelectionKey(position: Int): Int? = null

    override fun getSelectableItemCount(): Int = data.size

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun prepareActionMode(menu: Menu) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_conference_call, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = data[position]
        holder.bindView(call, allowSingleClick = false, allowLongClick = false) { itemView, _ ->
            getCallContact(itemView.context, call) { callContact ->
                itemView.post {
                    itemView.item_conference_call_name.setTextColor(textColor)
                    itemView.item_conference_call_name.text = callContact.name.ifEmpty { itemView.context.getString(R.string.unknown_caller) }
                    SimpleContactsHelper(activity.applicationContext).loadContactImage(
                        callContact.photoUri,
                        itemView.item_conference_call_image,
                        callContact.name
                    )
                }
            }
            val canSeparate = call.hasCapability(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE)
            val canDisconnect = call.hasCapability(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)
            itemView.item_conference_call_split.applyColorFilter(textColor)
            itemView.item_conference_call_split.isEnabled = canSeparate
            itemView.item_conference_call_split.alpha = if (canSeparate) 1.0f else LOWER_ALPHA
            itemView.item_conference_call_split.setOnClickListener {
                call.splitFromConference()
                data.removeAt(position)
                notifyItemRemoved(position)
                if (data.size == 1) {
                    activity.finish()
                }
            }
            //itemView.item_conference_call_end.applyColorFilter(textColor)
            itemView.item_conference_call_end.isEnabled = canDisconnect
            itemView.item_conference_call_end.alpha = if (canDisconnect) 1.0f else LOWER_ALPHA
            itemView.item_conference_call_end.setOnClickListener {
                call.disconnect()
                data.removeAt(position)
                notifyItemRemoved(position)
                if (data.size == 1) {
                    activity.finish()
                }
            }
        }
        bindViewHolder(holder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.item_conference_call_image)
        }
    }
}
