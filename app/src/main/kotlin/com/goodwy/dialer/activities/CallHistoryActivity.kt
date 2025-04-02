package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.text.SpannableString
import android.view.View
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.ContactSource
import com.goodwy.commons.models.contacts.Event
import com.goodwy.commons.models.contacts.SocialAction
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.CallHistoryAdapter
import com.goodwy.dialer.databinding.ActivityCallHistoryBinding
import com.goodwy.dialer.databinding.ItemViewEmailBinding
import com.goodwy.dialer.databinding.ItemViewEventBinding
import com.goodwy.dialer.databinding.ItemViewMessengersActionsBinding
import com.goodwy.dialer.dialogs.ChangeTextDialog
import com.goodwy.dialer.dialogs.ChooseSocialDialog
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.RecentCall
import kotlin.collections.ArrayList
import kotlin.math.abs
import androidx.core.graphics.drawable.toDrawable

class CallHistoryActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCallHistoryBinding::inflate)

    private var allRecentCall = listOf<RecentCall>()
    private var contact: Contact? = null
    private var duplicateContacts = ArrayList<Contact>()
    private var contactSources = ArrayList<ContactSource>()
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFFEBEBEB.toInt()
    private val black = 0xFF000000.toInt()
    private var buttonBg = white
    private var recentsAdapter: CallHistoryAdapter? = null
    private var currentRecentCall: RecentCall? = null
    private var recentsHelper = RecentsHelper(this)
    private var permissionContact = false

    private var getCurrentPhoneNumber = ""
    private var getCurrentRecentId = 0
    //private fun getCurrentContactId() = intent.getIntExtra(CONTACT_ID, 0)

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.callHistoryWrapper, binding.callHistoryHolder, useTransparentNavigation = true, useTopSearchMenu = false)

        getCurrentPhoneNumber = intent.getStringExtra(CURRENT_PHONE_NUMBER) ?: ""
        getCurrentRecentId = intent.getIntExtra(CURRENT_RECENT_CALL, CURRENT_RECENT_CALL_ID)
        handlePermission(PERMISSION_READ_CONTACTS) {
            permissionContact = it
        }
        initButton()
        gotRecents()
    }

    private fun initButton() {
        val properPrimaryColor = getProperPrimaryColor()

        var drawableSMS = AppCompatResources.getDrawable(this, R.drawable.ic_messages)
        drawableSMS = DrawableCompat.wrap(drawableSMS!!)
        DrawableCompat.setTint(drawableSMS, properPrimaryColor)
        DrawableCompat.setTintMode(drawableSMS, PorterDuff.Mode.SRC_IN)
        binding.oneButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableSMS, null, null)

        var drawableCall = AppCompatResources.getDrawable(this, R.drawable.ic_phone_vector)
        drawableCall = DrawableCompat.wrap(drawableCall!!)
        DrawableCompat.setTint(drawableCall, properPrimaryColor)
        DrawableCompat.setTintMode(drawableCall, PorterDuff.Mode.SRC_IN)
        binding.twoButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableCall, null, null)

        var drawableInfo = AppCompatResources.getDrawable(this, R.drawable.ic_videocam_vector)
        drawableInfo = DrawableCompat.wrap(drawableInfo!!)
        DrawableCompat.setTint(drawableInfo, properPrimaryColor)
        DrawableCompat.setTintMode(drawableInfo, PorterDuff.Mode.SRC_IN)
        binding.threeButton.apply {
            setCompoundDrawablesWithIntrinsicBounds(null, drawableInfo, null, null)
            alpha = 0.5f
            isEnabled = false
        }

        var drawableShare = AppCompatResources.getDrawable(this, R.drawable.ic_mail_vector)
        drawableShare = DrawableCompat.wrap(drawableShare!!)
        DrawableCompat.setTint(drawableShare, properPrimaryColor)
        DrawableCompat.setTintMode(drawableShare, PorterDuff.Mode.SRC_IN)
        binding.fourButton.apply {
            setCompoundDrawablesWithIntrinsicBounds(null, drawableShare, null, null)
            alpha = 0.5f
            isEnabled = false
        }

        binding.oneButton.setTextColor(properPrimaryColor)
        binding.twoButton.setTextColor(properPrimaryColor)
        binding.threeButton.setTextColor(properPrimaryColor)
        binding.fourButton.setTextColor(properPrimaryColor)
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        binding.callHistoryPlaceholderContainer.beGone()
        updateTextColors(binding.callHistoryHolder)
        buttonBg = if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) white else getBottomNavigationBackgroundColor()
        updateColors()
        setupCallerNotes()
        refreshItems {}
        setupMenu()
    }

    private fun updateColors() {
        val red = resources.getColor(R.color.red_missed)
        val properPrimaryColor = getProperPrimaryColor()
        val properBackgroundColor = getProperBackgroundColor()

        val phoneNumber = if (config.formatPhoneNumbers) getCurrentPhoneNumber.formatPhoneNumber() else getCurrentPhoneNumber

        binding.apply {
            callHistoryNumberType.beGone()
            callHistoryNumberType.setTextColor(getProperTextColor())
            callHistoryNumberContainer.setOnClickListener {
                val call = currentRecentCall
                if (call != null) {
                    makeCall(call)
                }
            }
            callHistoryNumberContainer.setOnLongClickListener {
                copyToClipboard(callHistoryNumber.text.toString())
                true
            }
            callHistoryNumber.text = formatterUnicodeWrap(phoneNumber)
            callHistoryNumber.setTextColor(properPrimaryColor)

            if (baseConfig.backgroundColor == white) {
                val colorToWhite = 0xFFf2f2f6.toInt()
                supportActionBar?.setBackgroundDrawable(colorToWhite.toDrawable())
                window.decorView.setBackgroundColor(colorToWhite)
                window.statusBarColor = colorToWhite
                //window.navigationBarColor = colorToWhite
                callHistoryAppbar.setBackgroundColor(colorToWhite)
            } else {
                window.decorView.setBackgroundColor(properBackgroundColor)
                callHistoryAppbar.setBackgroundColor(properBackgroundColor)
            }

            arrayOf(
                oneButton, twoButton, threeButton, fourButton,
                callHistoryPlaceholderContainer, callHistoryList,
                callHistoryNumberContainer,
                contactMessengersActionsHolder,
                contactEmailsHolder,
                contactEventsHolder,
                callerNotesHolder,
                defaultSimButtonContainer,
                blockButton
            ).forEach {
                it.background.setTint(buttonBg)
            }

            if (isNumberBlocked(getCurrentPhoneNumber, getBlockedNumbers())) {
                blockButton.text = getString(R.string.unblock_number)
                blockButton.setTextColor(properPrimaryColor)
            } else {
                blockButton.text = getString(R.string.block_number)
                blockButton.setTextColor(red)
            }
        }
    }

    private fun setupCallerNotes() {
        val callerNote = callerNotesHelper.getCallerNotes(getCurrentPhoneNumber)
        val note = callerNote?.note
        binding.apply {
            callerNotesIcon.setColorFilter(getProperTextColor())
            callerNotesText(note)

            callerNotesHolder.setOnClickListener {
                changeNoteDialog(getCurrentPhoneNumber)
            }
            callerNotesHolder.setOnLongClickListener {
                val text = callerNotesHelper.getCallerNotes(getCurrentPhoneNumber)?.note
                text?.let { note -> copyToClipboard(note) }
                true
            }
        }
    }

    private fun callerNotesText(note: String?) {
        binding.apply {
            val empty = note == null || note == ""
            callerNotes.text = if (empty) getString(R.string.add_notes) else note
                callerNotes.alpha = if (empty) 0.6f else 1f
        }
    }

    private fun changeNoteDialog(number: String) {
        val callerNote = callerNotesHelper.getCallerNotes(number)
        ChangeTextDialog(
            activity = this@CallHistoryActivity,
            title = getString(R.string.add_notes) + " ($number)",
            currentText = callerNote?.note,
            maxLength = CALLER_NOTES_MAX_LENGTH,
            showNeutralButton = true,
            neutralTextRes = R.string.delete
        ) {
            if (it != "") {
                callerNotesHelper.addCallerNotes(number, it, callerNote) {
                    callerNotesText(it)
                }
            } else {
                callerNotesHelper.deleteCallerNotes(callerNote) {
                    callerNotesText(it)
                }
            }
        }
    }

    private fun setupMenu() {
        binding.callHistoryToolbar.menu.apply {
            updateMenuItemColors(this)

            findItem(R.id.delete).setOnMenuItemClickListener {
                askConfirmRemove()
                true
            }

            findItem(R.id.share).setOnMenuItemClickListener {
                launchShare()
                true
            }

            findItem(R.id.call_anonymously).setOnMenuItemClickListener {
                if (currentRecentCall != null) {
                    if (config.showWarningAnonymousCall) {
                        val text = String.format(getString(R.string.call_anonymously_warning), getCurrentPhoneNumber)
                        ConfirmationAdvancedDialog(
                            this@CallHistoryActivity,
                            text,
                            R.string.call_anonymously_warning,
                            R.string.ok,
                            R.string.do_not_show_again,
                            fromHtml = true
                        ) {
                            if (it) {
                                makeCall(currentRecentCall!!, "#31#")
                            } else {
                                config.showWarningAnonymousCall = false
                                makeCall(currentRecentCall!!, "#31#")
                            }
                        }
                    } else {
                        makeCall(currentRecentCall!!, "#31#")
                    }
                }
                true
            }
        }

        val properBackgroundColor = getProperBackgroundColor()
        val contrastColor = properBackgroundColor.getContrastColor()
        val itemColor = if (baseConfig.topAppBarColorIcon) getProperPrimaryColor() else contrastColor
        binding.callHistoryToolbar.apply {
            overflowIcon = resources.getColoredDrawableWithColor(R.drawable.ic_three_dots_vector, itemColor)
            setNavigationIconTint(itemColor)
            setNavigationOnClickListener {
                finish()
            }
        }
    }

    private fun getStarDrawable(on: Boolean) = AppCompatResources.getDrawable(this, if (on) R.drawable.ic_star_vector else R.drawable.ic_star_outline_vector)

    private fun updateBackgroundHistory() {
        binding.callHistoryList.background.setTint(buttonBg)
    }

    private fun refreshItems(callback: (() -> Unit)?) {
        ensureBackgroundThread {
            initContact()
        }

        refreshCallLog(loadAll = true) {
          //  refreshCallLog(loadAll = true)
        }
    }

    private fun refreshCallLog(loadAll: Boolean = false, callback: (() -> Unit)? = null) {
        getRecentCalls(loadAll) { recents ->
            allRecentCall = recents
            currentRecentCall = recents.firstOrNull { it.id == getCurrentRecentId }
            runOnUiThread {
                updateButton()
            }

            val currentRecentCall = recents.filter { it.phoneNumber == getCurrentPhoneNumber}
            runOnUiThread {
                if (recents.isEmpty() || currentRecentCall.isEmpty()) {
                        binding.apply {
                            callHistoryListContainer.beGone()
                            callHistoryPlaceholderContainer.beVisible()
                            callHistoryToolbar.menu.findItem(R.id.delete).isVisible = false
                        }
                    } else {
                        binding.apply {
                            callHistoryListContainer.beVisible()
                            callHistoryPlaceholderContainer.beGone()
                            callHistoryToolbar.menu.findItem(R.id.delete).isVisible = true
                        }
                        gotRecents(currentRecentCall)
                        updateBackgroundHistory()
                    }
                }

            callback?.invoke()
        }
    }

    private fun getRecentCalls(loadAll: Boolean, callback: (List<RecentCall>) -> Unit) {
        val queryCount = if (loadAll) config.queryLimitRecent else RecentsHelper.QUERY_LIMIT
        val existingRecentCalls = allRecentCall

        with(recentsHelper) {
            getRecentCalls(existingRecentCalls, queryCount) {
//                prepareCallLog(it, callback)
                callback(it)
            }
        }
    }

//    private fun prepareCallLog(calls: List<RecentCall>, callback: (List<RecentCall>) -> Unit) {
//        if (calls.isEmpty()) {
//            callback(emptyList())
//            return
//        }
//
//        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
//            ensureBackgroundThread {
//                val privateContacts = getPrivateContacts()
//                val updatedCalls = updateNamesIfEmpty(
//                    calls = maybeFilterPrivateCalls(calls, privateContacts),
//                    contacts = contacts,
//                    privateContacts = privateContacts
//                )
//
//                callback(
//                    updatedCalls
//                )
//            }
//        }
//    }
//
//    private fun getPrivateContacts(): ArrayList<Contact> {
//        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
//        return MyContactsContentProvider.getContacts(this, privateCursor)
//    }
//
//    private fun maybeFilterPrivateCalls(calls: List<RecentCall>, privateContacts: List<Contact>): List<RecentCall> {
//        val ignoredSources = baseConfig.ignoredContactSources
//        return if (SMT_PRIVATE in ignoredSources) {
//            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
//            calls.filterNot { it.phoneNumber in privateNumbers }
//        } else {
//            calls
//        }
//    }
//
//    private fun updateNamesIfEmpty(calls: List<RecentCall>, contacts: List<Contact>, privateContacts: List<Contact>): List<RecentCall> {
//        if (calls.isEmpty()) return mutableListOf()
//
//        val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
//        return calls.map { call ->
//            if (call.phoneNumber == call.name) {
//                val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(call.phoneNumber) }
//                val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == call.phoneNumber }
//
//                when {
//                    privateContact != null -> withUpdatedName(call = call, name = privateContact.getNameToDisplay())
//                    contact != null -> withUpdatedName(call = call, name = contact.getNameToDisplay())
//                    else -> call
//                }
//            } else {
//                call
//            }
//        }
//    }

    private fun withUpdatedName(call: RecentCall, name: String): RecentCall {
        return call.copy(
            name = name,
            groupedCalls = call.groupedCalls
                ?.map { it.copy(name = name) }
                ?.toMutableList()
                ?.ifEmpty { null }
        )
    }

    private fun gotRecents(recents: List<RecentCall> = config.parseRecentCallsCache().filter { it.phoneNumber == getCurrentPhoneNumber}) {
        val currAdapter = binding.callHistoryList.adapter
        if (currAdapter == null) {
            recentsAdapter = CallHistoryAdapter(
                activity = this as SimpleActivity,
                recyclerView = binding.callHistoryList,
                refreshItemsListener = null,
                hideTimeAtOtherDays = false,
                itemDelete = { deleted ->
                    allRecentCall = allRecentCall.filter { it !in deleted }
                },
                itemClick = {}
            )

            binding.callHistoryList.adapter = recentsAdapter
            recentsAdapter?.updateItems(recents)
            setupCallHistoryListCount(recents.size)

            if (this.areSystemAnimationsEnabled) {
                binding.callHistoryList.scheduleLayoutAnimation()
            }
        } else {
            recentsAdapter?.updateItems(recents)
            setupCallHistoryListCount(recents.size)
        }
    }

    private fun setupCallHistoryListCount(count: Int) {
        binding.callHistoryListCount.apply {
            beVisibleIf(count > 6)
            text = String.format(getString(R.string.total_g), count.toString())
        }
    }

    private fun initContact() {
        var wasLookupKeyUsed = false
        var contactId: Int
        try {
            contactId = intent.getIntExtra(CONTACT_ID, 0)
        } catch (e: Exception) {
            return
        }
        if (contactId == 0 ) {
            val data = intent.data
            if (data != null) {
                val rawId = if (data.path!!.contains("lookup")) {
                    val lookupKey = getLookupKeyFromUri(data)
                    if (lookupKey != null) {
                        contact = ContactsHelper(this).getContactWithLookupKey(lookupKey)
                        wasLookupKeyUsed = true
                    }

                    getLookupUriRawId(data)
                } else {
                    getContactUriRawId(data)
                }

                if (rawId != -1) {
                    contactId = rawId
                }
            }
        }

        if (contactId != 0 && !wasLookupKeyUsed) {
            if (permissionContact) contact =
                ContactsHelper(this).getContactWithId(contactId, intent.getBooleanExtra(IS_PRIVATE, false))

            if (contact != null) {
                getDuplicateContacts {
                    ContactsHelper(this).getContactSources {
                        contactSources = it
                        runOnUiThread {
                            setupMenuForContact()
                            setupVideoCallActions()
                            setupMessengersActions()
                            setupEmails()
                            setupEvents()
                        }
                    }
                }
            }
        } else {
            if (contact == null) {
              //  finish()
                runOnUiThread {
                    setupMenuForNoContact()
                }
            } else {
                getDuplicateContacts {
                    runOnUiThread {
                        //setupFavorite()
                        setupVideoCallActions()
                        setupMessengersActions()
                        setupEmails()
                        setupEvents()
                    }
                }
            }
        }
    }

    private fun setupMenuForNoContact() {
        val contrastColor = getProperBackgroundColor().getContrastColor()
        val itemColor = if (baseConfig.topAppBarColorIcon) getProperPrimaryColor() else contrastColor

        val editMenu = binding.callHistoryToolbar.menu.findItem(R.id.edit)
        editMenu.isVisible = true
        val editIcon = resources.getColoredDrawableWithColor(R.drawable.ic_add_person_vector, itemColor)
        editMenu.icon = editIcon
        editMenu.setTitle(R.string.add_contact)
        editMenu.setOnMenuItemClickListener {
            addContact()
            true
        }
    }

    private fun addContact() {
        val phoneNumber = if (config.formatPhoneNumbers) getCurrentPhoneNumber.formatPhoneNumber() else getCurrentPhoneNumber
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            launchActivityIntent(this)
        }
    }

    private fun setupMenuForContact() {
        val contrastColor = getProperBackgroundColor().getContrastColor()
        val itemColor = if (baseConfig.topAppBarColorIcon) getProperPrimaryColor() else contrastColor

        val favoriteMenu = binding.callHistoryToolbar.menu.findItem(R.id.favorite)
        favoriteMenu.isVisible = true
        val favoriteIcon = getStarDrawable(contact!!.starred == 1)
        favoriteIcon!!.setTint(itemColor)
        favoriteMenu.icon = favoriteIcon
        favoriteMenu.setOnMenuItemClickListener {
            val newIsStarred = if (contact!!.starred == 1) 0 else 1
            ensureBackgroundThread {
                val contacts = arrayListOf(contact!!)
                if (newIsStarred == 1) {
                    ContactsHelper(this@CallHistoryActivity).addFavorites(contacts)
                } else {
                    ContactsHelper(this@CallHistoryActivity).removeFavorites(contacts)
                }
            }
            contact!!.starred = newIsStarred
            val favoriteIconNew = getStarDrawable(contact!!.starred == 1)
            favoriteIconNew!!.setTint(itemColor)
            favoriteMenu.icon = favoriteIconNew
            true
        }

        val editMenu = binding.callHistoryToolbar.menu.findItem(R.id.edit)
        editMenu.isVisible = true
        editMenu.setOnMenuItemClickListener {
            contact?.let { startContactEdit(it) }
            true
        }

        val openWithMenu = binding.callHistoryToolbar.menu.findItem(R.id.open_with)
        openWithMenu.isVisible = true
        openWithMenu.setOnMenuItemClickListener {
            openWith()
            true
        }
    }

    private fun getDuplicateContacts(callback: () -> Unit) {
        ContactsHelper(this).getDuplicatesOfContact(contact!!, false) { contacts ->
            ensureBackgroundThread {
                duplicateContacts.clear()
                val displayContactSources = getVisibleContactSources()
                contacts.filter { displayContactSources.contains(it.source) }.forEach {
                    val duplicate = ContactsHelper(this).getContactWithId(it.id, it.isPrivate())
                    if (duplicate != null) {
                        duplicateContacts.add(duplicate)
                    }
                }

                runOnUiThread {
                    callback()
                }
            }
        }
    }

    private fun setupVideoCallActions() {
        if (contact != null) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            duplicateContacts.forEach {
                sources[it] = getPublicContactSourceSync(it.source, contactSources)
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (_, value) -> value.lowercase() }.toMap() as LinkedHashMap<Contact, String>
            }

            val videoActions = arrayListOf<SocialAction>()
            for ((key, value) in sources) {

                if (value.lowercase() == WHATSAPP) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val whatsappVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(whatsappVideoActions)
                    }
                }

                if (value.lowercase() == SIGNAL) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val signalVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(signalVideoActions)
                    }
                }

                if (value.lowercase() == VIBER) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val viberVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(viberVideoActions)
                    }
                }

                if (value.lowercase() == TELEGRAM) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val telegramVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(telegramVideoActions)
                    }
                }

                if (value.lowercase() == THREEMA) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val threemaVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(threemaVideoActions)
                    }
                }
            }

            binding.threeButton.apply {
                alpha = if (videoActions.isNotEmpty()) 1f else 0.5f
                isEnabled = videoActions.isNotEmpty()
                setOnLongClickListener { toast(R.string.video_call); true; }
                if (videoActions.isNotEmpty()) setOnClickListener { showVideoCallAction(videoActions) }
            }
        }
    }

    private fun showVideoCallAction(actions: ArrayList<SocialAction>) {
        ensureBackgroundThread {
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    ChooseSocialDialog(this@CallHistoryActivity, actions) { action ->
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (e: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupMessengersActions() {
        binding.contactMessengersActionsHolder.removeAllViews()
        if (contact != null) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            duplicateContacts.forEach {
                sources[it] = getPublicContactSourceSync(it.source, contactSources)
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (_, value) -> value.lowercase() }.toMap() as LinkedHashMap<Contact, String>
            }
            for ((key, value) in sources) {
                val isLastItem = sources.keys.last()
                ItemViewMessengersActionsBinding.inflate(layoutInflater, binding.contactMessengersActionsHolder, false).apply {
                    contactMessengerActionName.text = if (value == "") getString(R.string.phone_storage) else value
                    val text = " (ID:" + key.source + ")"
                    contactMessengerActionAccount.text = text
                    val properTextColor = getProperTextColor()
                    contactMessengerActionName.setTextColor(properTextColor)
                    contactMessengerActionAccount.setTextColor(properTextColor)
                    contactMessengerActionHolder.setOnClickListener {
                        if (contactMessengerActionAccount.isVisible()) contactMessengerActionAccount.beGone()
                        else contactMessengerActionAccount.beVisible()
                    }
                    val properPrimaryColor = getProperPrimaryColor()
                    contactMessengerActionNumber.setTextColor(properPrimaryColor)
                    binding.contactMessengersActionsHolder.addView(root)

                    arrayOf(
                        contactMessengerActionMessageIcon, contactMessengerActionCallIcon, contactMessengerActionVideoIcon,
                    ).forEach {
                        it.background.setTint(properTextColor)
                        it.background.alpha = 40
                        it.setColorFilter(properPrimaryColor)
                    }

                    dividerContactMessengerAction.setBackgroundColor(properTextColor)
                    dividerContactMessengerAction.beGoneIf(isLastItem == key)

                    if (value.lowercase() == WHATSAPP) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.lowercase() == SIGNAL) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.lowercase() == VIBER) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            contactMessengerActionNumber.beGoneIf(contact!!.phoneNumbers.size > 1 && messageActions.isEmpty())
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.lowercase() == TELEGRAM) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(messageActions)
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(callActions)
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(videoActions)
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.lowercase() == THREEMA) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contactMessengerActionNumber.text = number
                            root.copyOnLongClick(number)
                            binding.contactMessengersActionsHolder.beVisible()
                            contactMessengerActionHolder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contactMessengerActionMessage.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contactMessengerActionCall.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contactMessengerActionVideo.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }
                }
            }
            //binding.contactMessengersActionsHolder.beVisible()
        } else {
            binding.contactMessengersActionsHolder.beGone()
        }
    }

    private fun showMessengerAction(actions: ArrayList<SocialAction>) {
        ensureBackgroundThread {
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    if (actions.size > 1) {
//                        ChooseSocialDialog(this@ViewContactActivity, actions) { action ->
//                            Intent(Intent.ACTION_VIEW).apply {
//                                val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
//                                setDataAndType(uri, action.mimetype)
//                                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
//                                try {
//                                    startActivity(this)
//                                } catch (e: SecurityException) {
//                                    handlePermission(PERMISSION_CALL_PHONE) { success ->
//                                        if (success) {
//                                            startActivity(this)
//                                        } else {
//                                            toast(R.string.no_phone_call_permission)
//                                        }
//                                    }
//                                } catch (e: ActivityNotFoundException) {
//                                    toast(R.string.no_app_found)
//                                } catch (e: Exception) {
//                                    showErrorToast(e)
//                                }
//                            }
//                        }
                    } else {
                        val action = actions.first()
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (e: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    // a contact cannot have different emails per contact source. Such contacts are handled as separate ones, not duplicates of each other
    private fun setupEmails() {
        binding.contactEmailsHolder.removeAllViews()
        val emails = contact!!.emails
        if (emails.isNotEmpty()) {

            binding.fourButton.apply {
                alpha = 1f
                isEnabled = true
                setOnClickListener {
                    if (emails.size == 1) sendEmailIntent(emails.first().value)
                    else {
                        val items = java.util.ArrayList<RadioItem>()
                        emails.forEachIndexed { index, email ->
                            items.add(RadioItem(index, email.value))
                        }

                        RadioGroupDialog(this@CallHistoryActivity, items, R.string.email) {
                            sendEmailIntent(emails[it as Int].value)
                        }
                    }
                }
                setOnLongClickListener { toast(R.string.email); true; }
            }

            val isFirstItem = emails.first()
            val isLastItem = emails.last()
            emails.forEach {
                ItemViewEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false).apply {
                    val email = it
                    binding.contactEmailsHolder.addView(root)
                    contactEmail.text = email.value
                    contactEmailType.text = getEmailTypeText(email.type, email.label)
                    val properTextColor = getProperTextColor()
                    contactEmailType.setTextColor(properTextColor)
                    root.copyOnLongClick(email.value)

                    root.setOnClickListener {
                        sendEmailIntent(email.value)
                    }

                    contactEmailIcon.beVisibleIf(isFirstItem == email)
                    contactEmailIcon.setColorFilter(properTextColor)
                    dividerContactEmail.setBackgroundColor(properTextColor)
                    dividerContactEmail.beGoneIf(isLastItem == email)
                    contactEmail.setTextColor(getProperPrimaryColor())
                }
            }
            binding.contactEmailsHolder.beVisible()
        } else {
            binding.contactEmailsHolder.beGone()
        }
    }

    private fun getEmailTypeText(type: Int, label: String): String {
        return if (type == ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM) {
            label
        } else {
            getString(
                when (type) {
                    ContactsContract.CommonDataKinds.Email.TYPE_HOME -> R.string.home
                    ContactsContract.CommonDataKinds.Email.TYPE_WORK -> R.string.work
                    ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> R.string.mobile
                    else -> R.string.other
                }
            )
        }
    }

    private fun setupEvents() {
        if (contact != null) {
            var events = contact!!.events.toMutableSet() as LinkedHashSet<Event>

            duplicateContacts.forEach {
                events.addAll(it.events)
            }

            events = events.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Event>
            binding.contactEventsHolder.removeAllViews()

            if (events.isNotEmpty()) {
                val isFirstItem = events.first()
                val isLastItem = events.last()
                events.forEach {
                    ItemViewEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false).apply {
                        val event = it
                        binding.contactEventsHolder.addView(root)
                        it.value.getDateTimeFromDateString(true, contactEvent)
                        contactEventType.setText(getEventTextId(it.type))
                        val properTextColor = getProperTextColor()
                        contactEventType.setTextColor(properTextColor)
                        root.copyOnLongClick(it.value.getDateFormattedFromDateString(true))

                        contactEventIcon.beVisibleIf(isFirstItem == event)
                        contactEventIcon.setColorFilter(properTextColor)
                        dividerContactEvent.setBackgroundColor(properTextColor)
                        dividerContactEvent.beGoneIf(isLastItem == event)
                        contactEvent.setTextColor(getProperPrimaryColor())
                    }
                }
                binding.contactEventsHolder.beVisible()
            } else {
                binding.contactEventsHolder.beGone()
            }
        }
    }

    private fun getEventTextId(type: Int) = when (type) {
        ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> R.string.anniversary
        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> R.string.birthday
        else -> R.string.other
    }

    private fun updateButton() {
        val call = currentRecentCall
        if (call != null) {
            if (call.phoneNumber == call.name || call.isABusinessCall() || call.isVoiceMail || isDestroyed || isFinishing) {
                val drawable =
                    if (call.isABusinessCall()) AppCompatResources.getDrawable(this, R.drawable.placeholder_company)
                    else if (call.isVoiceMail) AppCompatResources.getDrawable(this, R.drawable.placeholder_voicemail)
                    else AppCompatResources.getDrawable(this, R.drawable.placeholder_contact)
                if (baseConfig.useColoredContacts) {
                    val letterBackgroundColors = getLetterBackgroundColors()
                    val color = letterBackgroundColors[abs(call.name.hashCode()) % letterBackgroundColors.size].toInt()
                    (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                }
                binding.topDetails.callHistoryImage.setImageDrawable(drawable)
            } else {
                if (!isFinishing && !isDestroyed) SimpleContactsHelper(this.applicationContext).loadContactImage(call.photoUri, binding.topDetails.callHistoryImage, call.name)
            }

            if (contact != null) {
                val contactPhoneNumber = contact!!.phoneNumbers.firstOrNull { it.normalizedNumber == getCurrentPhoneNumber }
                if (contactPhoneNumber != null) {
                    binding.callHistoryNumberTypeContainer.beVisible()
                    binding.callHistoryNumberType.apply {
                        beVisible()
                        val phoneNumberType = contactPhoneNumber.type
                        val phoneNumberLabel = contactPhoneNumber.label
                        text = getPhoneNumberTypeText(phoneNumberType, phoneNumberLabel)
                    }
                    binding.callHistoryFavoriteIcon.apply {
                        beVisibleIf(contactPhoneNumber.isPrimary)
                        applyColorFilter(getProperTextColor())
                    }
                }

                arrayOf(
                    binding.topDetails.callHistoryImage, binding.topDetails.callHistoryName
                ).forEach {
                    it.setOnClickListener { viewContactInfo(contact!!) }
                }
                binding.topDetails.callHistoryImage.apply {
                    setOnLongClickListener { toast(R.string.contact_details); true; }
                    contentDescription = getString(R.string.contact_details)
                }
            } else {
                val country = if (getCurrentPhoneNumber.startsWith("+")) getCountryByNumber(getCurrentPhoneNumber) else ""
                if (country != "") {
                    binding.callHistoryNumberTypeContainer.beVisible()
                    binding.callHistoryNumberType.apply {
                        beVisible()
                        text = country
                    }
                }

                arrayOf(
                    binding.topDetails.callHistoryImage, binding.topDetails.callHistoryName
                ).forEach {
                    it.setOnClickListener {
                        addContact()
                    }
                }
                binding.topDetails.callHistoryImage.apply {
                    setOnLongClickListener { toast(R.string.add_contact); true; }
                    contentDescription = getString(R.string.add_contact)
                }
            }

            binding.callHistoryPlaceholderContainer.beGone()

            val name = call.name
            val formatPhoneNumbers = config.formatPhoneNumbers
            val nameToShow = if (name == call.phoneNumber && formatPhoneNumbers) {
                SpannableString(name.formatPhoneNumber())
            } else {
                SpannableString(name)
            }
            binding.topDetails.callHistoryName.apply {
                text = formatterUnicodeWrap(nameToShow.toString())
                setTextColor(getProperTextColor())
                setOnLongClickListener {
                    copyToClipboard(nameToShow.toString())
                    true
                }
            }

            binding.topDetails.callHistoryCompany.apply {
                val company = formatterUnicodeWrap(call.company)
                beVisibleIf(company != "" && company != nameToShow.toString())
                text = company
                setTextColor(getProperTextColor())
                setOnLongClickListener {
                    copyToClipboard(company)
                    true
                }
            }

            binding.topDetails.callHistoryJobPosition.apply {
                val jobPosition = formatterUnicodeWrap(call.jobPosition)
                beVisibleIf(jobPosition != "")
                text = jobPosition
                setTextColor(getProperTextColor())
                setOnLongClickListener {
                    copyToClipboard(jobPosition)
                    true
                }
            }

            binding.oneButton.apply {
                setOnClickListener {
                    launchSendSMSIntentRecommendation(call.phoneNumber)
                }
                setOnLongClickListener { toast(R.string.send_sms); true; }
            }

            binding.twoButton.apply {
                setOnClickListener {
                    makeCall(call)
                }
                setOnLongClickListener { toast(R.string.call); true; }
            }

            if (areMultipleSIMsAvailable() && !call.isUnknownNumber) {
                binding.defaultSimButtonContainer.beVisible()
                updateDefaultSIMButton(call)
                val phoneNumber = call.phoneNumber.replace("+", "%2B")
                val simList = getAvailableSIMCardLabels()
                binding.defaultSim1Button.setOnClickListener {
                    val sim1 = simList[0]
                    if ((config.getCustomSIM("tel:$phoneNumber") ?: "") == sim1.handle) {
                        removeDefaultSIM()
                    } else {
                        config.saveCustomSIM("tel:$phoneNumber", sim1.handle)
                        toast(sim1.label)
                    }
                    updateDefaultSIMButton(call)
                    binding.defaultSim1Button.performHapticFeedback()
                }
                if (simList.size > 1) {
                    binding.defaultSim2Button.setOnClickListener {
                        val sim2 = simList[1]
                        if ((config.getCustomSIM("tel:$phoneNumber") ?: "") == sim2.handle) {
                            removeDefaultSIM()
                        } else {
                            config.saveCustomSIM("tel:$phoneNumber", sim2.handle)
                            toast(sim2.label)
                        }
                        updateDefaultSIMButton(call)
                        binding.defaultSim2Button.performHapticFeedback()
                    }
                }  else binding.defaultSimButtonContainer.beGone()
            } else binding.defaultSimButtonContainer.beGone()

            binding.blockButton.apply {
                setOnClickListener {
                    askConfirmBlock()
                }
            }
        } else {
            binding.callHistoryListContainer.beGone()
            binding.callHistoryPlaceholderContainer.beVisible()
            binding.callHistoryToolbar.menu.findItem(R.id.delete).isVisible = false
        }
    }

    private fun updateDefaultSIMButton(call: RecentCall) {
        val background = getProperTextColor()
        val sim1 = config.simIconsColors[1]
        val sim2 = config.simIconsColors[2]
        val phoneNumber = call.phoneNumber.replace("+","%2B")
        val simList = getAvailableSIMCardLabels()
        binding.apply {
            defaultSim1Icon.background.setTint(background)
            defaultSim1Icon.background.alpha = 40
            defaultSim1Icon.setColorFilter(background.adjustAlpha(0.60f))
            defaultSim1Id.setTextColor(black)

            defaultSim2Icon.background.setTint(background)
            defaultSim2Icon.background.alpha = 40
            defaultSim2Icon.setColorFilter(background.adjustAlpha(0.60f))
            defaultSim2Id.setTextColor(black)

            if (simList.size > 1) {
                if ((config.getCustomSIM("tel:$phoneNumber") ?: "") == simList[0].handle && !call.isUnknownNumber) {
                    defaultSim1Icon.background.setTint(sim1)
                    defaultSim1Icon.background.alpha = 255
                    defaultSim1Icon.setColorFilter(white)
                    defaultSim1Id.setTextColor(sim1)
                }

                if ((config.getCustomSIM("tel:$phoneNumber") ?: "") == simList[1].handle && !call.isUnknownNumber) {
                    defaultSim2Icon.background.setTint(sim2)
                    defaultSim2Icon.background.alpha = 255
                    defaultSim2Icon.setColorFilter(white)
                    defaultSim2Id.setTextColor(sim2)
                }
            }
        }
    }

    private fun makeCall(call: RecentCall, prefix: String = "") {
        val phoneNumber = call.phoneNumber
        if (config.showCallConfirmation) {
            CallConfirmationDialog(this as SimpleActivity, call.name) {
                launchCallIntent("$prefix$phoneNumber", key = BuildConfig.RIGHT_APP_KEY)
            }
        } else {
            launchCallIntent("$prefix$phoneNumber", key = BuildConfig.RIGHT_APP_KEY)
        }
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getCurrentPhoneNumber.replace("+","%2B")
        config.removeCustomSIM("tel:$phoneNumber")
    }

    private fun askConfirmBlock() {
        if (isDefaultDialer()) {
            val baseString = if (isNumberBlocked(getCurrentPhoneNumber, getBlockedNumbers())) {
                R.string.unblock_confirmation
            } else { R.string.block_confirmation }
            val question = String.format(resources.getString(baseString), getCurrentPhoneNumber)

            ConfirmationDialog(this, question) {
                blockNumbers()
            }
        } else toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
    }

    private fun blockNumbers() {
        config.needUpdateRecents = true
        val red = resources.getColor(R.color.red_missed)
        runOnUiThread {
            if (isNumberBlocked(getCurrentPhoneNumber, getBlockedNumbers())) {
                deleteBlockedNumber(getCurrentPhoneNumber)
                binding.blockButton.text = getString(R.string.block_number)
                binding.blockButton.setTextColor(red)
            } else {
                addBlockedNumber(getCurrentPhoneNumber)
                binding.blockButton.text = getString(R.string.unblock_number)
                binding.blockButton.setTextColor(getProperPrimaryColor())
            }
        }
    }

    private fun askConfirmRemove() {
        val message = if (recentsAdapter?.getSelectedItems()!!.isEmpty()) {
            getString(R.string.clear_history_confirmation)
        } else getString(R.string.remove_confirmation)
        ConfirmationDialog(this, message) {
            handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (getCurrentPhoneNumber.isEmpty()) {
            return
        }
        config.needUpdateRecents = true

        val callsToRemove = allRecentCall.filter { getCurrentPhoneNumber.contains(it.phoneNumber) } as ArrayList<RecentCall>
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.groupedCalls?.mapTo(idsToRemove) { call -> call.id }
        }

        RecentsHelper(this).removeRecentCalls(idsToRemove) {
            val callerNote = callerNotesHelper.getCallerNotes(getCurrentPhoneNumber)
            callerNotesHelper.deleteCallerNotes(callerNote)

            runOnUiThread {
                onBackPressed()
            }
        }
    }

//    private fun finishActMode() {
//        recentsAdapter?.finishActMode()
//    }

    private fun viewContactInfo(contact: Contact) {
        this.startContactDetailsIntentRecommendation(contact)
    }

    private fun launchShare() {
        val text = getCurrentPhoneNumber
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, getCurrentPhoneNumber)
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            startActivity(Intent.createChooser(this, getString(R.string.invite_via)))
        }
    }

    private fun View.copyOnLongClick(value: String) {
        setOnLongClickListener {
            copyToClipboard(value)
            true
        }
    }

    private fun openWith() {
        if (contact != null) {
            val uri = getContactPublicUri(contact!!)
            launchViewContactIntent(uri)
        }
    }
}

// hide private contacts from recent calls
//private fun List<RecentCall>.hidePrivateContacts(privateContacts: List<Contact>, shouldHide: Boolean): List<RecentCall> {
//    return if (shouldHide) {
//        filterNot { recent ->
//            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
//            recent.phoneNumber in privateNumbers
//        }
//    } else {
//        this
//    }
//}

//private fun List<RecentCall>.setNamesIfEmpty(contacts: List<Contact>, privateContacts: List<Contact>): ArrayList<RecentCall> {
//    val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
//    return map { recent ->
//        if (recent.phoneNumber == recent.name) {
//            val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
//            val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }
//
//            when {
//                privateContact != null -> recent.copy(name = privateContact.getNameToDisplay())
//                contact != null -> recent.copy(name = contact.getNameToDisplay())
//                else -> recent
//            }
//        } else {
//            recent
//        }
//    } as ArrayList
//}
