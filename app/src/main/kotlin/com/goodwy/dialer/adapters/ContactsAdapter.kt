package com.goodwy.dialer.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.databinding.ItemContactWithNumberGridBinding
import com.goodwy.commons.databinding.ItemContactWithNumberInfoBinding
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.FeatureLockedDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.interfaces.ItemMoveCallback
import com.goodwy.commons.interfaces.ItemTouchHelperContract
import com.goodwy.commons.interfaces.StartReorderDragListener
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.interfaces.RefreshItemsListener
import java.util.*

class ContactsAdapter(
    activity: SimpleActivity,
    var contacts: MutableList<Contact>,
    recyclerView: MyRecyclerView,
    highlightText: String = "",
    private val refreshItemsListener: RefreshItemsListener? = null,
    var viewType: Int = VIEW_TYPE_LIST,
    private val showDeleteButton: Boolean = true,
    private val enableDrag: Boolean = false,
    private val allowLongClick: Boolean = true,
    private val showIcon: Boolean = true,
    private val showNumber: Boolean = false,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick),
    ItemTouchHelperContract, MyRecyclerView.MyZoomListener {

    private var textToHighlight = highlightText
    var fontSize: Float = activity.getTextSize()
    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener? = null
    var onDragEndListener: (() -> Unit)? = null
    var onSpanCountListener: (Int) -> Unit = {}

    init {
        setupDragListener(true)

        if (recyclerView.layoutManager is GridLayoutManager) {
            setupZoomListener(this)
        }

        if (enableDrag) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this, true/*viewType == VIEW_TYPE_GRID*/))
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
        val selectedNumber = "tel:${getSelectedPhoneNumber()}".replace("+","%2B")

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""

            findItem(R.id.cab_delete).isVisible = showDeleteButton
            findItem(R.id.cab_create_shortcut).title = activity.getString(R.string.create_shortcut)//activity.addLockedLabelIfNeeded(R.string.create_shortcut)
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected && isOreoPlus()
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected
            findItem(R.id.cab_block_unblock_contact).isVisible = isOneItemSelected && isNougatPlus()
            getCabBlockContactTitle { title ->
                findItem(R.id.cab_block_unblock_contact).title = title
            }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_block_unblock_contact -> tryBlockingUnblocking()
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = Binding.getByItemViewType(viewType).inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val layout = when (viewType) {
//            VIEW_TYPE_GRID -> R.layout.item_contact_with_number_grid
//            else -> R.layout.item_contact_with_number_info
//        }
//        return createViewHolder(layout, parent)
//    }

    override fun getItemViewType(position: Int): Int {
        return viewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, true, allowLongClick) { itemView, layoutPosition ->
            val viewType = getItemViewType(position)
            setupView(Binding.getByItemViewType(viewType).bind(itemView), contact, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    private fun getCabBlockContactTitle(callback: (String) -> Unit) {
        val contact = getSelectedItems().firstOrNull() ?: return callback("")

        activity.isContactBlocked(contact) { blocked ->
            val cabItemTitleRes = if (blocked) {
                R.string.unblock_contact
            } else {
                R.string.block_contact
            }

            callback(activity.getString(cabItemTitleRes)) //callback(activity.addLockedLabelIfNeeded(cabItemTitleRes))
        }
    }

    private fun tryBlockingUnblocking() {
        val contact = getSelectedItems().firstOrNull() ?: return

        if (activity.isOrWasThankYouInstalled()) {
            activity.isContactBlocked(contact) { blocked ->
                if (blocked) {
                    tryUnblocking(contact)
                } else {
                    tryBlocking(contact)
                }
            }
        } else {
            FeatureLockedDialog(activity) { }
        }
    }

    private fun tryBlocking(contact: Contact) {
        askConfirmBlock(contact) { contactBlocked ->
            val resultMsg = if (contactBlocked) {
                R.string.block_contact_success
            } else {
                R.string.block_contact_fail
            }

            activity.toast(resultMsg)
            finishActMode()
        }
    }

    private fun tryUnblocking(contact: Contact) {
        val contactUnblocked = activity.unblockContact(contact)
        val resultMsg = if (contactUnblocked) {
            R.string.unblock_contact_success
        } else {
            R.string.unblock_contact_fail
        }

        activity.toast(resultMsg)
        finishActMode()
    }

    private fun askConfirmBlock(contact: Contact, callback: (Boolean) -> Unit) {
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), contact.name)

        ConfirmationDialog(activity, question) {
            val contactBlocked = activity.blockContact(contact)
            callback(contactBlocked)
        }
    }

    fun updateItems(newItems: List<Contact>, highlightText: String = "") {
        if (newItems.hashCode() != contacts.hashCode()) {
            contacts = ArrayList(newItems)
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
        val phoneNumber = getSelectedPhoneNumber()?.replace("+","%2B") ?: return
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
        activity.launchSendSMSIntentRecommendation(recipient)
    }

    private fun viewContactDetails() {
        val contact = getSelectedItems().firstOrNull() ?: return
        activity.startContactDetailsIntentRecommendation(contact)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().firstOrNull() ?: return
        val items = if (itemsCnt == 1) {
            "\"${firstItem.getNameToDisplay()}\""
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

    private fun getSelectedItems() = contacts.filter { selectedKeys.contains(it.rawId) } as ArrayList<Contact>

    private fun getLastItem() = contacts.last()

    private fun getSelectedPhoneNumber(): String? {
//        val numbers = getSelectedItems().firstOrNull()?.phoneNumbers
//        val primaryNumber = numbers?.firstOrNull { it.isPrimary }
//        return primaryNumber?.normalizedNumber ?: numbers?.firstOrNull()?.normalizedNumber
        return getSelectedItems().firstOrNull()?.getPrimaryNumber()
    }

    private fun tryCreateShortcut() {
        /*if (activity.isOrWasThankYouInstalled()) {
            createShortcut()
        } else {
            FeatureLockedDialog(activity) { }
        }*/
        createShortcut()
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val contact = contacts.firstOrNull { selectedKeys.contains(it.rawId) } ?: return
        val manager = activity.shortcutManager
        if (manager.isRequestPinShortcutSupported) {
            SimpleContactsHelper(activity).getShortcutImage(contact.photoUri, contact.getNameToDisplay()) { image ->
                activity.runOnUiThread {
                    activity.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        val intent = Intent(action).apply {
                            data = Uri.fromParts("tel", getSelectedPhoneNumber(), null)
                        }

                        val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                            .setShortLabel(contact.getNameToDisplay())
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
            Binding.getByItemViewType(holder.itemViewType).bind(holder.itemView).apply {
                Glide.with(activity).clear(itemContactImage)
            }
        }
    }

    private fun setupView(binding: ItemViewBinding, contact: Contact, holder: ViewHolder) {
        binding.apply {
            root.setupViewBackground(activity)
            itemContactFrame.isSelected = selectedKeys.contains(contact.rawId)
            itemContactName.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                val name = contact.getNameToDisplay()
                text = if (textToHighlight.isEmpty()) name else {
                    if (name.contains(textToHighlight, true)) {
                        name.highlightTextPart(textToHighlight, properPrimaryColor)
                    } else {
                        name.highlightTextFromNumbers(textToHighlight, properPrimaryColor)
                    }
                }
            }
            itemContactNumber.apply {
                beGoneIf(!showNumber)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)

                val phoneNumberToUse = if (textToHighlight.isEmpty()) {
                    contact.phoneNumbers.firstOrNull { it.isPrimary } ?: contact.phoneNumbers.firstOrNull()
                } else {
                    contact.phoneNumbers.firstOrNull { it.value.contains(textToHighlight) } ?: contact.phoneNumbers.firstOrNull()
                }
                val numberText = phoneNumberToUse?.value ?: ""
                text = if (textToHighlight.isEmpty()) numberText else numberText.highlightTextPart(textToHighlight, properPrimaryColor, false, true)
            }
            itemContactInfo?.applyColorFilter(accentColor)
            itemContactInfoHolder?.apply {
                beVisibleIf(showIcon && selectedKeys.isEmpty())
                setOnClickListener {
                    viewContactInfo(contact)
                }
            }

            if (enableDrag && textToHighlight.isEmpty()) {
                dragHandleIcon.apply {
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
                dragHandleIcon.apply {
                    beGone()
                    setOnTouchListener(null)
                }
            }

            if (!activity.isDestroyed) {
                itemContactImage.beVisibleIf(activity.config.showContactThumbnails)
                if (activity.config.showContactThumbnails) {
                    SimpleContactsHelper(root.context).loadContactImage(contact.photoUri, itemContactImage, contact.getNameToDisplay())
                }
            }

            divider?.apply {
                setBackgroundColor(textColor)
                beInvisibleIf(getLastItem() == contact || !activity.config.useDividers)
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

    override fun zoomIn() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpanCount = layoutManager.spanCount
            val newSpanCount = (currentSpanCount - 1).coerceIn(1, CONTACTS_GRID_MAX_COLUMNS_COUNT)
            layoutManager.spanCount = newSpanCount
            recyclerView.requestLayout()
            onSpanCountListener(newSpanCount)
        }
    }

    override fun zoomOut() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpanCount = layoutManager.spanCount
            val newSpanCount = (currentSpanCount + 1).coerceIn(1, CONTACTS_GRID_MAX_COLUMNS_COUNT)
            layoutManager.spanCount = newSpanCount
            recyclerView.requestLayout()
            onSpanCountListener(newSpanCount)
        }
    }

    private sealed interface Binding {
        companion object {
            fun getByItemViewType(viewType: Int): Binding {
                return when (viewType) {
                    VIEW_TYPE_GRID -> ItemContactGrid
                    else -> ItemContact
                }
            }
        }

        fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding

        fun bind(view: View): ItemViewBinding

        data object ItemContactGrid : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactGridBindingAdapter(ItemContactWithNumberGridBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactGridBindingAdapter(ItemContactWithNumberGridBinding.bind(view))
            }
        }

        data object ItemContact : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactBindingAdapter(ItemContactWithNumberInfoBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactBindingAdapter(ItemContactWithNumberInfoBinding.bind(view))
            }
        }
    }

    private interface ItemViewBinding : ViewBinding {
        val itemContactName: TextView
        val itemContactNumber: TextView
        val itemContactImage: ImageView
        val itemContactFrame: FrameLayout
        val dragHandleIcon: ImageView
        val itemContactInfo: ImageView?
        val itemContactInfoHolder: FrameLayout?
        val divider: ImageView?
    }

    private class ItemContactGridBindingAdapter(val binding: ItemContactWithNumberGridBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactNumber = binding.itemContactNumber
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override val itemContactInfo = null
        override val itemContactInfoHolder = null
        override val divider = null

        override fun getRoot(): View = binding.root
    }

    private class ItemContactBindingAdapter(val binding: ItemContactWithNumberInfoBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactNumber = binding.itemContactNumber
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override val itemContactInfo = binding.itemContactInfo
        override val itemContactInfoHolder = binding.itemContactInfoHolder
        override val divider = binding.divider

        override fun getRoot(): View = binding.root
    }
    private fun viewContactInfo(contact: Contact) {
        activity.startContactDetailsIntentRecommendation(contact)
    }
}
