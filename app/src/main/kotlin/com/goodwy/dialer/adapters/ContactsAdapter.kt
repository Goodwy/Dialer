package com.goodwy.dialer.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.behaviorule.arturdumchev.library.pixels
import com.bumptech.glide.Glide
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.databinding.ItemContactWithNumberGridBinding
import com.goodwy.commons.databinding.ItemContactWithNumberInfoBinding
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.interfaces.ItemMoveCallback
import com.goodwy.commons.interfaces.ItemTouchHelperContract
import com.goodwy.commons.interfaces.StartReorderDragListener
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.databinding.ItemContactWithNumberGridSwipeBinding
import com.goodwy.dialer.databinding.ItemContactWithNumberInfoSwipeBinding
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.interfaces.RefreshItemsListener
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeDirection
import me.thanel.swipeactionview.SwipeGestureListener
import java.util.Collections
import java.util.Locale
import kotlin.math.abs

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
    itemClick: (Any) -> Unit,
    val profileIconClick: ((Any) -> Unit)? = null
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
            findItem(R.id.cab_call).isVisible = !hasMultipleSIMs && isOneItemSelected
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
            R.id.cab_call -> callContact()
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_view_details -> viewContactDetails()
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.rawId

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.rawId == key }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = Binding.getByItemViewType(viewType, activity.config.useSwipeToAction).inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun getItemViewType(position: Int): Int {
        return viewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, true, allowLongClick) { itemView, _ ->
            val viewType = getItemViewType(position)
            setupView(Binding.getByItemViewType(viewType, activity.config.useSwipeToAction).bind(itemView), contact, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    private fun getItemWithKey(key: Int): Contact? = contacts.firstOrNull { it.id == key }

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

    private fun tryBlockingUnblocking(swipe: Boolean = false) {
        val contact = getSelectedItems().firstOrNull() ?: return
        activity.isContactBlocked(contact) { blocked ->
            if (swipe) selectedKeys.clear()
            if (blocked) {
                tryUnblocking(contact)
            } else {
                tryBlocking(contact)
            }
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

    @SuppressLint("NotifyDataSetChanged")
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

    private fun callContact() {
        val contact = getItemWithKey(selectedKeys.first()) ?: return
        if (activity.config.showCallConfirmation) {
            CallConfirmationDialog(activity as SimpleActivity, contact.getNameToDisplay()) {
                activity.apply {
                    initiateCall(contact) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
                }
            }
        } else {
            activity.apply {
                initiateCall(contact) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
            }
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

    private fun sendSMS(isSwipe: Boolean = false) {
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
        if (isSwipe) selectedKeys.clear()
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

        ConfirmationAdvancedDialog(activity, question, cancelOnTouchOutside = false) {
            if (it) {
                activity.handlePermission(PERMISSION_WRITE_CONTACTS) {
                    deleteContacts()
                }
            } else selectedKeys.clear()
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
                            putExtra(IS_RIGHT_APP, BuildConfig.RIGHT_APP_KEY)
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
            Binding.getByItemViewType(holder.itemViewType, activity.config.useSwipeToAction).bind(holder.itemView).apply {
                Glide.with(activity).clear(itemContactImage)
            }
        }
    }

    private fun String.highlightTextFromNumbers(textToHighlight: String, primaryColor: Int, language: String): SpannableString {
        val spannableString = SpannableString(this)
        val digits = DialpadT9.convertLettersToNumbers(this.uppercase(), language)
        if (digits.contains(textToHighlight)) {
            //offsetting the characters to be extracted, by the number of first non-letter or non-numeric characters.
            val lettersAndNumbers = Regex("[^A-Za-z0-9 ]")
            val firstSymbol = lettersAndNumbers.replace(this, "").firstOrNull()
            val offsetIndex = if (firstSymbol != null) this.indexOf(firstSymbol, 0, true) else 0

            val startIndex = digits.indexOf(textToHighlight, 0, true) + offsetIndex
            val endIndex = (startIndex + textToHighlight.length).coerceAtMost(length)
            try {
                spannableString.setSpan(ForegroundColorSpan(primaryColor), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
            } catch (ignored: IndexOutOfBoundsException) {
            }
        }

        return spannableString
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(binding: ItemViewBinding, contact: Contact, holder: ViewHolder) {
        binding.apply {
            root.setupViewBackground(activity)
            itemContactFrame.isSelected = selectedKeys.contains(contact.rawId)

            itemContactImage.apply {
                if (profileIconClick != null) {
                    setOnClickListener {
                        if (!actModeCallback.isSelectable) {
                            profileIconClick.invoke(contact)
                        } else {
                            holder.viewClicked(contact)
                        }
                    }
                    setOnLongClickListener {
                        holder.viewLongClicked()
                        true
                    }
                }
            }

            itemContactName.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                val name = contact.getNameToDisplay()
                text = if (textToHighlight.isEmpty()) {
                    name
                } else {
                    if (name.contains(textToHighlight, true)) {
                        name.highlightTextPart(textToHighlight, properPrimaryColor)
                    } else {
                        var spacedTextToHighlight = textToHighlight
                        val strippedName = PhoneNumberUtils.convertKeypadLettersToDigits(name.filterNot { it.isWhitespace() })
                        val startIndex = strippedName.indexOf(textToHighlight)

                        if (strippedName.contains(textToHighlight) && strippedName != name) {
                            for (i in 0..spacedTextToHighlight.length) {
                                if (name.toCharArray().size > startIndex + i) {
                                    if (name[startIndex + i].isWhitespace()) {
                                        spacedTextToHighlight = spacedTextToHighlight.replaceRange(i, i, " ")
                                    }
                                }
                            }
                        }

                        val langPref = activity.config.dialpadSecondaryLanguage ?: ""
                        val langLocale = Locale.getDefault().language
                        val isAutoLang = DialpadT9.getSupportedSecondaryLanguages().contains(langLocale) && langPref == LANGUAGE_SYSTEM
                        val lang = if (isAutoLang) langLocale else langPref
                        name.highlightTextFromNumbers(spacedTextToHighlight, properPrimaryColor, lang)
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
                text = if (textToHighlight.isEmpty()) numberText else numberText.highlightTextPart(
                    textToHighlight, properPrimaryColor,
                    highlightAll = false,
                    ignoreCharsBetweenDigits = true
                )
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
                val showContactThumbnails = activity.config.showContactThumbnails
                itemContactImage.beVisibleIf(showContactThumbnails)
                if (showContactThumbnails) {
                    if (viewType != VIEW_TYPE_GRID) {
                        val size = (root.context.pixels(R.dimen.normal_icon_size) * contactThumbnailsSize).toInt()
                        itemContactImage.setHeightAndWidth(size)
                    }
                    if (contact.isABusinessContact()) {
                        val drawable = ResourcesCompat.getDrawable(resources, R.drawable.placeholder_company, activity.theme)
                        if (baseConfig.useColoredContacts) {
                            val letterBackgroundColors = activity.getLetterBackgroundColors()
                            val color = letterBackgroundColors[abs(contact.getNameToDisplay().hashCode()) % letterBackgroundColors.size].toInt()
                            (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                        }
                        itemContactImage.setImageDrawable(drawable)
                    } else {
                        SimpleContactsHelper(root.context).loadContactImage(contact.photoUri, itemContactImage, contact.getNameToDisplay())
                    }
                }
            }

            divider?.apply {
                setBackgroundColor(textColor)
                beInvisibleIf(getLastItem() == contact || !activity.config.useDividers)
            }

            //swipe
            if (activity.config.useSwipeToAction && itemContactSwipe != null) {
                itemContactFrameSelect?.setupViewBackground(activity)
                itemContactFrame.setBackgroundColor(backgroundColor)

                val isRTL = activity.isRTLLayout
                val swipeLeftAction = if (isRTL) activity.config.swipeRightAction else activity.config.swipeLeftAction
                swipeLeftIcon!!.setImageResource(swipeActionImageResource(swipeLeftAction))
                swipeLeftIcon!!.setColorFilter(properPrimaryColor.getContrastColor())
                swipeLeftIconHolder!!.setBackgroundColor(swipeActionColor(swipeLeftAction))

                val swipeRightAction = if (isRTL) activity.config.swipeLeftAction else activity.config.swipeRightAction
                swipeRightIcon!!.setImageResource(swipeActionImageResource(swipeRightAction))
                swipeRightIcon!!.setColorFilter(properPrimaryColor.getContrastColor())
                swipeRightIconHolder!!.setBackgroundColor(swipeActionColor(swipeRightAction))

                if (activity.config.swipeRipple) {
                    itemContactSwipe!!.setRippleColor(SwipeDirection.Left, swipeActionColor(swipeLeftAction))
                    itemContactSwipe!!.setRippleColor(SwipeDirection.Right, swipeActionColor(swipeRightAction))
                }

                val contactsGridColumnCount = activity.config.contactsGridColumnCount
                if (viewType == VIEW_TYPE_GRID && contactsGridColumnCount > 1) {
                    val width =
                        (Resources.getSystem().displayMetrics.widthPixels / contactsGridColumnCount / 2.5).toInt()
                    swipeLeftIconHolder!!.setWidth(width)
                    swipeRightIconHolder!!.setWidth(width)
                } else {
                    val halfScreenWidth = activity.resources.displayMetrics.widthPixels / 2
                    val swipeWidth = activity.resources.getDimension(com.goodwy.commons.R.dimen.swipe_width)
                    if (swipeWidth > halfScreenWidth) {
                        swipeRightIconHolder!!.setWidth(halfScreenWidth)
                        swipeLeftIconHolder!!.setWidth(halfScreenWidth)
                    }
                }

                itemContactSwipe!!.useHapticFeedback = activity.config.swipeVibration
                itemContactSwipe!!.swipeGestureListener = object : SwipeGestureListener {
                    override fun onSwipedLeft(swipeActionView: SwipeActionView): Boolean {
                        finishActMode()
                        val swipeLeftOrRightAction =
                            if (activity.isRTLLayout) activity.config.swipeRightAction else activity.config.swipeLeftAction
                        swipeAction(swipeLeftOrRightAction, contact)
                        slideLeftReturn(swipeLeftIcon!!, swipeLeftIconHolder!!)
                        return true
                    }

                    override fun onSwipedRight(swipeActionView: SwipeActionView): Boolean {
                        finishActMode()
                        val swipeRightOrLeftAction =
                            if (activity.isRTLLayout) activity.config.swipeLeftAction else activity.config.swipeRightAction
                        swipeAction(swipeRightOrLeftAction, contact)
                        slideRightReturn(swipeRightIcon!!, swipeRightIconHolder!!)
                        return true
                    }

                    override fun onSwipedActivated(swipedRight: Boolean) {
                        if (viewType != VIEW_TYPE_GRID) {
                            if (swipedRight) slideRight(swipeRightIcon!!, swipeRightIconHolder!!)
                            else slideLeft(swipeLeftIcon!!)
                        }
                    }

                    override fun onSwipedDeactivated(swipedRight: Boolean) {
                        if (viewType != VIEW_TYPE_GRID) {
                            if (swipedRight) slideRightReturn(swipeRightIcon!!, swipeRightIconHolder!!)
                            else slideLeftReturn(swipeLeftIcon!!, swipeLeftIconHolder!!)
                        }
                    }
                }
            }
        }
    }

    private fun slideRight(view: View, parent: View) {
        view.animate()
            .x(parent.right - activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin) - view.width)
    }

    private fun slideLeft(view: View) {
        view.animate()
            .x(activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin))
    }

    private fun slideRightReturn(view: View, parent: View) {
        view.animate()
            .x(parent.left + activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin))
    }

    private fun slideLeftReturn(view: View, parent: View) {
        view.animate()
            .x(parent.width - activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin) - view.width)
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
            fun getByItemViewType(viewType: Int, useSwipeToAction: Boolean): Binding {
                return when (viewType) {
                    VIEW_TYPE_GRID -> if (useSwipeToAction) ItemContactGridSwipe else ItemContactGrid
                    else -> if (useSwipeToAction) ItemContactSwipe else ItemContact
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

        data object ItemContactGridSwipe : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactGridSwipeBindingAdapter(ItemContactWithNumberGridSwipeBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactGridSwipeBindingAdapter(ItemContactWithNumberGridSwipeBinding.bind(view))
            }
        }

        data object ItemContactSwipe : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemContactSwipeBindingAdapter(ItemContactWithNumberInfoSwipeBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemContactSwipeBindingAdapter(ItemContactWithNumberInfoSwipeBinding.bind(view))
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
        val itemContactSwipe: SwipeActionView?
        val itemContactFrameSelect: ConstraintLayout?
        val swipeLeftIconHolder: RelativeLayout?
        val swipeRightIconHolder: RelativeLayout?
        val swipeLeftIcon: ImageView?
        val swipeRightIcon: ImageView?
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
        override val itemContactSwipe = null
        override val itemContactFrameSelect = null
        override val swipeLeftIconHolder = null
        override val swipeRightIconHolder = null
        override val swipeLeftIcon = null
        override val swipeRightIcon = null

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
        override val itemContactSwipe = null
        override val itemContactFrameSelect = null
        override val swipeLeftIconHolder = null
        override val swipeRightIconHolder = null
        override val swipeLeftIcon = null
        override val swipeRightIcon = null

        override fun getRoot(): View = binding.root
    }

    private class ItemContactGridSwipeBindingAdapter(val binding: ItemContactWithNumberGridSwipeBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactNumber = binding.itemContactNumber
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override val itemContactInfo = null
        override val itemContactInfoHolder = null
        override val divider = null
        override val itemContactSwipe = binding.itemContactSwipe
        override val itemContactFrameSelect = binding.itemContactFrameSelect
        override val swipeLeftIconHolder = binding.swipeLeftIconHolder
        override val swipeRightIconHolder = binding.swipeRightIconHolder
        override val swipeLeftIcon = binding.swipeLeftIcon
        override val swipeRightIcon = binding.swipeRightIcon

        override fun getRoot(): View = binding.root
    }

    private class ItemContactSwipeBindingAdapter(val binding: ItemContactWithNumberInfoSwipeBinding) : ItemViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactNumber = binding.itemContactNumber
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon
        override val itemContactInfo = binding.itemContactInfo
        override val itemContactInfoHolder = binding.itemContactInfoHolder
        override val divider = binding.divider
        override val itemContactSwipe = binding.itemContactSwipe
        override val itemContactFrameSelect = binding.itemContactFrameSelect
        override val swipeLeftIconHolder = binding.swipeLeftIconHolder
        override val swipeRightIconHolder = binding.swipeRightIconHolder
        override val swipeLeftIcon = binding.swipeLeftIcon
        override val swipeRightIcon = binding.swipeRightIcon

        override fun getRoot(): View = binding.root
    }
    private fun viewContactInfo(contact: Contact) {
        activity.startContactDetailsIntentRecommendation(contact)
    }

    private fun swipeActionImageResource(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> com.goodwy.commons.R.drawable.ic_delete_outline
            SWIPE_ACTION_MESSAGE -> R.drawable.ic_messages
            SWIPE_ACTION_BLOCK -> R.drawable.ic_block_vector
            else -> R.drawable.ic_phone_vector
        }
    }

    private fun swipeActionColor(swipeAction: Int): Int {
        val oneSim = activity.config.currentSIMCardIndex == 0
        val simColor = if (oneSim) activity.config.simIconsColors[1] else activity.config.simIconsColors[2]
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> resources.getColor(R.color.red_call, activity.theme)
            SWIPE_ACTION_MESSAGE -> resources.getColor(R.color.ic_messages, activity.theme)
            SWIPE_ACTION_BLOCK -> resources.getColor(R.color.swipe_purple, activity.theme)
            else -> simColor
        }
    }

    private fun swipeAction(swipeAction: Int, contact: Contact) {
        when (swipeAction) {
            SWIPE_ACTION_DELETE -> swipedDelete(contact)
            SWIPE_ACTION_MESSAGE -> swipedSMS(contact)
            SWIPE_ACTION_BLOCK -> swipedBlock(contact)
            else -> swipedCall(contact)
        }
    }

    private fun swipedDelete(contact: Contact) {
        selectedKeys.add(contact.rawId)
        if (activity.config.skipDeleteConfirmation) {
            activity.handlePermission(PERMISSION_WRITE_CONTACTS) {
                deleteContacts()
            }
        } else askConfirmDelete()
    }

    private fun swipedSMS(contact: Contact) {
        selectedKeys.add(contact.rawId)
        sendSMS(true)
    }

    private fun swipedBlock(contact: Contact) {
        if (!isNougatPlus()) return
        selectedKeys.add(contact.rawId)
        tryBlockingUnblocking(true)
    }

    private fun swipedCall(contact: Contact) {
        if (activity.config.showCallConfirmation) {
            CallConfirmationDialog(activity as SimpleActivity, contact.getNameToDisplay()) {
                activity.apply {
                    initiateCall(contact) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
                }
            }
        } else {
            activity.apply {
                initiateCall(contact) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
            }
        }
    }
}
