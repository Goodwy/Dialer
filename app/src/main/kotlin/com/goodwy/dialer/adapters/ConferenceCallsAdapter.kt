package com.goodwy.dialer.adapters

import android.graphics.drawable.LayerDrawable
import android.telecom.Call
import android.view.Menu
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import com.bumptech.glide.Glide
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.LOWER_ALPHA
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.databinding.ItemConferenceCallBinding
import com.goodwy.dialer.extensions.hasCapability
import com.goodwy.dialer.helpers.getCallContact
import kotlin.math.abs

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemConferenceCallBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = data[position]
        holder.bindView(call, allowSingleClick = false, allowLongClick = false) { itemView, _ ->
            ItemConferenceCallBinding.bind(itemView).apply {
                getCallContact(itemView.context, call) { callContact ->
                    root.post {
                        itemConferenceCallName.text = callContact.name.ifEmpty { itemView.context.getString(R.string.unknown_caller) }
                        if (callContact.number == callContact.name || callContact.isABusinessCall || callContact.isVoiceMail) {
                            val drawable =
                                if (callContact.isABusinessCall) AppCompatResources.getDrawable(activity, R.drawable.placeholder_company)
                                else if (callContact.isVoiceMail) AppCompatResources.getDrawable(activity, R.drawable.placeholder_voicemail)
                                else AppCompatResources.getDrawable(activity, R.drawable.placeholder_contact)
                            if (baseConfig.useColoredContacts) {
                                val letterBackgroundColors = activity.getLetterBackgroundColors()
                                val color = letterBackgroundColors[abs(callContact.name.hashCode()) % letterBackgroundColors.size].toInt()
                                (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                            }
                            itemConferenceCallImage.setImageDrawable(drawable)
                        } else {
                            SimpleContactsHelper(activity).loadContactImage(
                                callContact.photoUri,
                                itemConferenceCallImage,
                                callContact.name
                            )
                        }
                    }
                }

                val canSeparate = call.hasCapability(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE)
                val canDisconnect = call.hasCapability(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)
                itemConferenceCallName.setTextColor(textColor)
                itemConferenceCallSplit.applyColorFilter(textColor)
                itemConferenceCallSplit.isEnabled = canSeparate
                itemConferenceCallSplit.alpha = if (canSeparate) 1.0f else LOWER_ALPHA
                itemConferenceCallSplit.setOnClickListener {
                    call.splitFromConference()
                    data.removeAt(position)
                    notifyItemRemoved(position)
                    if (data.size == 1) {
                        //activity.finish()
                        activity.onBackPressed()
                    }
                }

                itemConferenceCallSplit.setOnLongClickListener {
                    if (!it.contentDescription.isNullOrEmpty()) {
                        root.context.toast(it.contentDescription.toString())
                    }
                    true
                }
                itemConferenceCallEnd.isEnabled = canDisconnect
                itemConferenceCallEnd.alpha = if (canDisconnect) 1.0f else LOWER_ALPHA
                itemConferenceCallEnd.setOnClickListener {
                    call.disconnect()
                    data.removeAt(position)
                    notifyItemRemoved(position)
                    if (data.size == 1) {
                        //activity.finish()
                        activity.onBackPressed()
                    }
                }

                itemConferenceCallEnd.setOnLongClickListener {
                    if (!it.contentDescription.isNullOrEmpty()) {
                        root.context.toast(it.contentDescription.toString())
                    }
                    true
                }
            }
        }
        bindViewHolder(holder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            ItemConferenceCallBinding.bind(holder.itemView).apply {
                Glide.with(activity).clear(itemConferenceCallImage)
            }
        }
    }
}
