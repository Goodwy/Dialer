package com.goodwy.dialer.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.FeatureLockedDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.PERMISSION_CALL_PHONE
import com.goodwy.commons.helpers.PERMISSION_WRITE_CONTACTS
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.isOreoPlus
import com.goodwy.commons.interfaces.ItemMoveCallback
import com.goodwy.commons.interfaces.ItemTouchHelperContract
import com.goodwy.commons.interfaces.StartReorderDragListener
import com.goodwy.commons.models.SimpleContact
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.extensions.areMultipleSIMsAvailable
import com.goodwy.dialer.extensions.callContactWithSim
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.startContactDetailsIntent
import com.goodwy.dialer.interfaces.RefreshItemsListener
import java.util.*

class ContactsAdapter(
    activity: SimpleActivity,
    var contacts: ArrayList<SimpleContact>,
    recyclerView: MyRecyclerView,
    val refreshItemsListener: RefreshItemsListener? = null,
    highlightText: String = "",
    val showDeleteButton: Boolean = true,
    private val enableDrag: Boolean = false,
    val showIcon: Boolean = true,
    val showNumber: Boolean = false,
    val allowLongClick: Boolean = true,
    itemClick: (Any) -> Unit
    ) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), ItemTouchHelperContract {

    private var textToHighlight = highlightText
    private var fontSize = activity.getTextSize()
    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener? = null
    var onDragEndListener: (() -> Unit)? = null

    init {
        setupDragListener(true)

        if (enableDrag) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this))
            touchHelper!!.attachToRecyclerView(recyclerView)

            startReorderDragListener = object : StartReorderDragListener {
                override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                    touchHelper?.startDrag(viewHolder)
                }
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_contacts

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val isOneItemSelected = isOneItemSelected()
        val selectedNumber = "tel:${getSelectedPhoneNumber()}"

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && activity.config.getCustomSIM(selectedNumber) != ""

            findItem(R.id.cab_delete).isVisible = showDeleteButton
            findItem(R.id.cab_create_shortcut).title = activity.addLockedLabelIfNeeded(R.string.create_shortcut)
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected && isOreoPlus()
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_view_details -> viewContactDetails()
            R.id.cab_create_shortcut -> tryCreateShortcut()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.rawId

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.rawId == key }

    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_contact_with_number_info, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, true, allowLongClick) { itemView, layoutPosition ->
            setupView(itemView, contact, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    fun updateItems(newItems: ArrayList<SimpleContact>, highlightText: String = "") {
        if (newItems.hashCode() != contacts.hashCode()) {
            contacts = newItems.clone() as ArrayList<SimpleContact>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    @SuppressLint("MissingPermission")
    private fun callContact(useSimOne: Boolean) {
        val number = getSelectedPhoneNumber() ?: return
        activity.callContactWithSim(number, useSimOne)
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.config.removeCustomSIM("tel:$phoneNumber")
        finishActMode()
    }

    private fun sendSMS() {
        val numbers = ArrayList<String>()
        getSelectedItems().map { simpleContact ->
            val contactNumbers = simpleContact.phoneNumbers
            val primaryNumber = contactNumbers.firstOrNull { it.isPrimary }
            val normalizedNumber = primaryNumber?.normalizedNumber ?: contactNumbers.firstOrNull()?.normalizedNumber

            if (normalizedNumber != null) {
                numbers.add(normalizedNumber)
            }
        }

        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntent(recipient)
    }

    private fun viewContactDetails() {
        val contact = getSelectedItems().firstOrNull() ?: return
        activity.startContactDetailsIntent(contact)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().firstOrNull() ?: return
        val items = if (itemsCnt == 1) {
            "\"${firstItem.name}\""
        } else {
            resources.getQuantityString(R.plurals.delete_contacts, itemsCnt, itemsCnt)
        }

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            activity.handlePermission(PERMISSION_WRITE_CONTACTS) {
                deleteContacts()
            }
        }
    }

    private fun deleteContacts() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        contacts.removeAll(contactsToRemove)
        val idsToRemove = contactsToRemove.map { it.rawId }.toMutableList() as ArrayList<Int>

        SimpleContactsHelper(activity).deleteContactRawIDs(idsToRemove) {
            activity.runOnUiThread {
                if (contacts.isEmpty()) {
                    refreshItemsListener?.refreshItems()
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    private fun getSelectedItems() = contacts.filter { selectedKeys.contains(it.rawId) } as ArrayList<SimpleContact>

    private fun getLastItem() = contacts.last()

    //private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber
    private fun getSelectedPhoneNumber(): String? {
        val numbers = getSelectedItems().firstOrNull()?.phoneNumbers
        val primaryNumber = numbers?.firstOrNull { it.isPrimary }
        return primaryNumber?.normalizedNumber ?: numbers?.firstOrNull()?.normalizedNumber
    }

    private fun tryCreateShortcut() {
        if (activity.isOrWasThankYouInstalled()) {
            createShortcut()
        } else {
            FeatureLockedDialog(activity) { }
        }
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val contact = contacts.firstOrNull { selectedKeys.contains(it.rawId) } ?: return
        val manager = activity.shortcutManager
        if (manager.isRequestPinShortcutSupported) {
            SimpleContactsHelper(activity).getShortcutImage(contact.photoUri, contact.name) { image ->
                activity.runOnUiThread {
                    activity.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        val intent = Intent(action).apply {
                            data = Uri.fromParts("tel", contact.phoneNumbers.first().normalizedNumber, null)
                        }

                        val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                            .setShortLabel(contact.name)
                            .setIcon(Icon.createWithBitmap(image))
                            .setIntent(intent)
                            .build()

                        manager.requestPinShortcut(shortcut, null)
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.findViewById<ImageView>(R.id.item_contact_image))
        }
    }

    private fun setupView(view: View, contact: SimpleContact, holder: ViewHolder) {
        view.apply {
            findViewById<ImageView>(R.id.divider)?.setBackgroundColor(textColor)
            if (getLastItem() == contact || !context.config.useDividers) findViewById<ImageView>(R.id.divider)?.beInvisible() else findViewById<ImageView>(R.id.divider)?.beVisible()

            findViewById<FrameLayout>(R.id.item_contact_frame).isSelected = selectedKeys.contains(contact.rawId)
            findViewById<TextView>(R.id.item_contact_name).apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                text = if (textToHighlight.isEmpty()) contact.name else {
                    if (contact.name.contains(textToHighlight, true)) {
                        contact.name.highlightTextPart(textToHighlight, context.getProperPrimaryColor())
                    } else {
                        contact.name.highlightTextFromNumbers(textToHighlight, context.getProperPrimaryColor())
                    }
                }

            }
            findViewById<TextView>(R.id.item_contact_number).apply {
                beVisibleIf(showNumber)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)

                val numbers = contact.phoneNumbers.firstOrNull {
                    it.normalizedNumber.contains(textToHighlight)
                }
                text = if (textToHighlight.isEmpty()) "" else {
                    numbers?.value?.highlightTextFromNumbers(textToHighlight, context.getProperPrimaryColor()) ?: ""
                }

            }
            findViewById<ImageView>(R.id.item_contact_info).applyColorFilter(context.getProperPrimaryColor())
            findViewById<FrameLayout>(R.id.item_contact_info_holder).apply {
                beVisibleIf(showIcon && selectedKeys.isEmpty())
                setOnClickListener {
                    viewContactInfo(contact)
                }
            }

            val dragIcon = findViewById<ImageView>(R.id.drag_handle_icon)
            if (enableDrag && textToHighlight.isEmpty()) {
                dragIcon.apply {
                    beVisibleIf(selectedKeys.isNotEmpty())
                    applyColorFilter(textColor)
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            startReorderDragListener?.requestDrag(holder)
                        }
                        false
                    }
                }
            } else {
                dragIcon.apply {
                    beGone()
                    setOnTouchListener(null)
                }
            }

            if (!activity.isDestroyed) {
                findViewById<ImageView>(R.id.item_contact_image).beVisibleIf(activity.config.showContactThumbnails)
                if (activity.config.showContactThumbnails) {
                    SimpleContactsHelper(context.applicationContext).loadContactImage(contact.photoUri, findViewById(R.id.item_contact_image), contact.name)
                }
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        activity.config.isCustomOrderSelected = true

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(contacts, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(contacts, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        onDragEndListener?.invoke()
    }

    private fun viewContactInfo(contact: SimpleContact) {
        activity.startContactDetailsIntent(contact)
    }
}
