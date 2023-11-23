package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
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
import androidx.core.view.ViewCompat.performHapticFeedback
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.ContactSource
import com.goodwy.commons.models.contacts.Event
import com.goodwy.commons.models.contacts.SocialAction
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.CallHistoryAdapter
import com.goodwy.dialer.databinding.ActivityCallHistoryBinding
import com.goodwy.dialer.databinding.ItemViewEmailBinding
import com.goodwy.dialer.databinding.ItemViewEventBinding
import com.goodwy.dialer.databinding.ItemViewMessengersActionsBinding
import com.goodwy.dialer.dialogs.ChooseSocialDialog
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.RecentCall
import kotlin.collections.ArrayList
import kotlin.math.abs

class CallHistoryActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCallHistoryBinding::inflate)

    var allContacts = ArrayList<Contact>()
    private var allRecentCall = listOf<RecentCall>()
    private var allRecentCallNotGrouped = listOf<RecentCall>()
    private var contact: Contact? = null
    private var duplicateContacts = ArrayList<Contact>()
    private var contactSources = ArrayList<ContactSource>()
    private var privateCursor: Cursor? = null
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFFEBEBEB.toInt()
    private val black = 0xFF000000.toInt()
    private var buttonBg = white
    private var recentsAdapter: CallHistoryAdapter? = null

    private fun getCurrentPhoneNumber() = intent.getStringExtra(CURRENT_PHONE_NUMBER) ?: ""
    private fun getCurrentRecentId() = intent.getIntExtra(CURRENT_RECENT_CALL, CURRENT_RECENT_CALL_ID)

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.callHistoryWrapper, binding.callHistoryHolder, useTransparentNavigation = true, useTopSearchMenu = false)

        initButton()
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
        binding.threeButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableInfo, null, null)
        binding.threeButton.alpha = 0.5f

        var drawableShare = AppCompatResources.getDrawable(this, R.drawable.ic_mail_vector)
        drawableShare = DrawableCompat.wrap(drawableShare!!)
        DrawableCompat.setTint(drawableShare, properPrimaryColor)
        DrawableCompat.setTintMode(drawableShare, PorterDuff.Mode.SRC_IN)
        binding.fourButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableShare, null, null)
        binding.fourButton.alpha = 0.5f

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
        updateBackgroundColors()
        refreshItems()
        setupMenu()
    }

    private fun updateBackgroundColors(color: Int = getProperBackgroundColor()) {
        val red = resources.getColor(R.color.red_missed)
        val properPrimaryColor = getProperPrimaryColor()

        val phoneNumber = if (getCurrentPhoneNumber().startsWith("+")) getPhoneNumberFormat(this@CallHistoryActivity, number = getCurrentPhoneNumber()) else getCurrentPhoneNumber()

        binding.apply {
            callHistoryNumberType.beGone()
            callHistoryNumberType.setTextColor(getProperTextColor())
            callHistoryNumberContainer.setOnClickListener {
                val call: RecentCall? = getCallList().firstOrNull()
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
                supportActionBar?.setBackgroundDrawable(ColorDrawable(colorToWhite))
                window.decorView.setBackgroundColor(colorToWhite)
                window.statusBarColor = colorToWhite
                //window.navigationBarColor = colorToWhite
            } else window.decorView.setBackgroundColor(color)

            binding.apply {
                arrayOf(
                    oneButton, twoButton, threeButton, fourButton,
                    callHistoryPlaceholderContainer, callHistoryList,
                    callHistoryNumberContainer,
                    contactMessengersActionsHolder,
                    contactEmailsHolder,
                    contactEventsHolder,
                    defaultSimButtonContainer,
                    blockButton
                ).forEach {
                    it.background.setTint(buttonBg)
                }
            }

            if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers())) {
                blockButton.text = getString(R.string.unblock_number)
                blockButton.setTextColor(properPrimaryColor)
            } else {
                blockButton.text = getString(R.string.block_number)
                blockButton.setTextColor(red)
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
        }

        val properBackgroundColor = getProperBackgroundColor()
        val contrastColor = properBackgroundColor.getContrastColor()
        val itemColor = if (baseConfig.topAppBarColored) getProperPrimaryColor() else contrastColor
        binding.callHistoryToolbar.setNavigationIconTint(itemColor)
        binding.callHistoryToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun getStarDrawable(on: Boolean) = AppCompatResources.getDrawable(this, if (on) R.drawable.ic_star_vector else R.drawable.ic_star_outline_vector)

    private fun updateBackgroundHistory() {
        binding.callHistoryList.background.setTint(buttonBg)
    }

    fun refreshItems() {
        RecentsHelper(this).getRecentCalls(false) { recents ->
            allRecentCallNotGrouped = recents
        }
        val privateCursor = this.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        val showAllRecentInHistory = this.config.showAllRecentInHistory
        RecentsHelper(this).getRecentCalls(true) { recents ->
            ContactsHelper(this@CallHistoryActivity).getContacts { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)

                val recentCall = recents.firstOrNull { it.id == getCurrentRecentId() }
                allRecentCall = if (recentCall != null && !showAllRecentInHistory) {
                    val callIds = recentCall.neighbourIDs.map { it }.toMutableList() as ArrayList<Int>
                    callIds.add(recentCall.id)
                    val recentGroup = allRecentCallNotGrouped.filter { callIds.contains(it.id) }.toMutableList() as ArrayList<RecentCall>

                    recentGroup
                        .setNamesIfEmpty(contacts, privateContacts)
                        .hidePrivateContacts(privateContacts, SMT_PRIVATE in baseConfig.ignoredContactSources)
                } else {
                    allRecentCallNotGrouped
                        .setNamesIfEmpty(contacts, privateContacts)
                        .hidePrivateContacts(privateContacts, SMT_PRIVATE in baseConfig.ignoredContactSources)
                }

                gotContacts(contacts)

                this.runOnUiThread {
                    if (recents.isEmpty()) {
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
                        gotRecents(allRecentCall)
                        updateBackgroundHistory()
                        updateButton()
                        ContactsHelper(this).getContactSources {
                            contactSources = it
                            ensureBackgroundThread {
                                if (getContactList() != null) initContact(getContactList()!!.id)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun gotContacts(newContacts: java.util.ArrayList<Contact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }
    }

    private fun gotRecents(recents: List<RecentCall>) {
        if (recents.isEmpty()) {
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

            val currAdapter = binding.callHistoryList.adapter
            val recent = recents.filter { it.phoneNumber == getCurrentPhoneNumber()}.toMutableList() as ArrayList<RecentCall>
            if (currAdapter == null) {
                recentsAdapter = CallHistoryAdapter(this as SimpleActivity, recent.toMutableList(), binding.callHistoryList, null) {}

                binding.callHistoryList.adapter = recentsAdapter

                if (this.areSystemAnimationsEnabled) {
                    binding.callHistoryList.scheduleLayoutAnimation()
                }
            } else {
                recentsAdapter?.updateItems(recent)
            }
        }
    }

    private fun initContact(id: Int) {
        var wasLookupKeyUsed = false
        var contactId: Int = id
        if (contactId == 0 ) {
            val data = intent.data
            if (data != null) {
                val rawId = if (data.path!!.contains("lookup")) {
                    val lookupKey = getLookupKeyFromUri(data)
                    if (lookupKey != null) {
                        contact = ContactsHelper(this).getContactWithLookupKey(lookupKey)
                        //fullContact = contact
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
            contact = ContactsHelper(this).getContactWithId(contactId, intent.getBooleanExtra(IS_PRIVATE, false))
            //fullContact = contact

            if (contact == null) {
               // toast(R.string.unknown_error_occurred)
               // finish()
            } else {
                getDuplicateContacts {
                    runOnUiThread {
                        setupFavorite()
                        setupVideoCallActions()
                        setupMessengersActions()
                        setupEmails()
                        setupEvents()
                    }
                }
            }
        } else {
            if (contact == null) {
              //  finish()
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

    private fun setupFavorite() {
        val favoriteMenu = binding.callHistoryToolbar.menu.findItem(R.id.favorite)
        favoriteMenu.isVisible = true
        val contrastColor = getProperBackgroundColor().getContrastColor()
        val itemColor = if (baseConfig.topAppBarColored) getProperPrimaryColor() else contrastColor
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
                sources = sources.toList().sortedBy { (key, value) -> value.lowercase() }.toMap() as LinkedHashMap<Contact, String>
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

            binding.threeButton.alpha = if (videoActions.isNotEmpty()) 1f else 0.5f

            if (videoActions.isNotEmpty()) binding.threeButton.setOnClickListener { showVideoCallAction(videoActions) }
            binding.threeButton.setOnLongClickListener { toast(R.string.video_call); true; }
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
                sources = sources.toList().sortedBy { (key, value) -> value.lowercase() }.toMap() as LinkedHashMap<Contact, String>
            }
            for ((key, value) in sources) {
                val isLastItem = sources.keys.last()
                ItemViewMessengersActionsBinding.inflate(layoutInflater, binding.contactMessengersActionsHolder, false).apply {
                    contactMessengerActionName.text = if (value == "") getString(R.string.phone_storage) else value
                    contactMessengerActionAccount.text = " (ID:" + key.source + ")"
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
                setOnClickListener {
                    if (emails.size == 1) sendEmailIntent(emails.first().value)
                    else {
                        val items = java.util.ArrayList<RadioItem>()
                        emails.forEachIndexed { index, email ->
                            items.add(RadioItem(index, email.value))
                        }

                        RadioGroupDialog(this@CallHistoryActivity, items) {
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
            allContacts.firstOrNull()!!.events = events.toMutableList() as ArrayList<Event>
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
                        root.copyOnLongClick(it.value)

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
        val call: RecentCall? = getCallList().firstOrNull()
        if (call != null) {

            val contact = getContactList()
            if (contact != null) {

                if (contact.phoneNumbers.firstOrNull { it.normalizedNumber == getCurrentPhoneNumber() } != null) {
                    binding.callHistoryNumberTypeContainer.beVisible()
                    binding.callHistoryNumberType.apply {
                        beVisible()
                        //text = contact.phoneNumbers.filter { it.normalizedNumber == getCurrentPhoneNumber()}.toString()
                        val phoneNumberType = contact.phoneNumbers.first { it.normalizedNumber == getCurrentPhoneNumber() }.type
                        val phoneNumberLabel = contact.phoneNumbers.first { it.normalizedNumber == getCurrentPhoneNumber() }.label
                        text = getPhoneNumberTypeText(phoneNumberType, phoneNumberLabel)
                    }
                    binding.callHistoryFavoriteIcon.apply {
                        beVisibleIf(contact.phoneNumbers.first { it.normalizedNumber == getCurrentPhoneNumber() }.isPrimary)
                        applyColorFilter(getProperTextColor())
                    }
                }

                arrayOf(
                    binding.topDetails.callHistoryImage, binding.topDetails.callHistoryName
                ).forEach {
                    it.setOnClickListener { viewContactInfo(contact) }
                }
                binding.topDetails.callHistoryImage.setOnLongClickListener { toast(R.string.contact_details); true; }
            } else {
                val country = if (getCurrentPhoneNumber().startsWith("+")) getCountryByNumber(this, getCurrentPhoneNumber()) else ""
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
                        Intent().apply {
                            action = Intent.ACTION_INSERT_OR_EDIT
                            type = "vnd.android.cursor.item/contact"
                            putExtra(KEY_PHONE, getCurrentPhoneNumber())
                            launchActivityIntent(this)
                        }
                    }
                }
                binding.topDetails.callHistoryImage.setOnLongClickListener { toast(R.string.add_contact); true; }
            }

            if (call.phoneNumber == call.name || isDestroyed || isFinishing) {
                //SimpleContactsHelper(this).loadContactImage(call.photoUri, binding.topDetails.callHistoryImage, call.name, letter = false)
                val drawable = AppCompatResources.getDrawable(this, R.drawable.placeholder_contact)
                if (baseConfig.useColoredContacts) {
                    val letterBackgroundColors = getLetterBackgroundColors()
                    val color = letterBackgroundColors[abs(call.name.hashCode()) % letterBackgroundColors.size].toInt()
                    (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                }
                binding.topDetails.callHistoryImage.setImageDrawable(drawable)
            } else {
                SimpleContactsHelper(this.applicationContext).loadContactImage(call.photoUri, binding.topDetails.callHistoryImage, call.name)
            }

            binding.callHistoryPlaceholderContainer.beGone()

            var nameToShow = SpannableString(call.name)
            if (nameToShow.startsWith("+")) nameToShow = SpannableString(getPhoneNumberFormat(this, number = nameToShow.toString()))
            binding.topDetails.callHistoryName.apply {
                val name = formatterUnicodeWrap(nameToShow.toString())
                text = name
                setTextColor(getProperTextColor())
                setOnLongClickListener {
                    copyToClipboard(nameToShow.toString())
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

    private fun getSelectedItems() = allRecentCall.filter { getCurrentPhoneNumber().contains(it.phoneNumber) } as ArrayList<RecentCall>

    private fun getCallList() = allRecentCall.filter { it.phoneNumber == getCurrentPhoneNumber()}.toMutableList() as ArrayList<RecentCall>

    private fun getContactList() = allContacts.firstOrNull { it.doesContainPhoneNumber(getCurrentPhoneNumber()) }

    private fun makeCall(call: RecentCall) {
        if (config.showCallConfirmation) {
            CallConfirmationDialog(this as SimpleActivity, call.name) {
                launchCallIntent(call.phoneNumber)
            }
        } else {
            launchCallIntent(call.phoneNumber)
        }
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getCurrentPhoneNumber().replace("+","%2B")
        config.removeCustomSIM("tel:$phoneNumber")
    }

    private fun askConfirmBlock() {
        if (isDefaultDialer()) {
            val baseString = if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers())) {
                R.string.unblock_confirmation
            } else { R.string.block_confirmation }
            val question = String.format(resources.getString(baseString), getCurrentPhoneNumber())

            ConfirmationDialog(this, question) {
                blockNumbers()
            }
        } else toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
    }

    private fun blockNumbers() {
        val red = resources.getColor(R.color.red_missed)
        //ensureBackgroundThread {
        runOnUiThread {
            if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers())) {
                deleteBlockedNumber(getCurrentPhoneNumber())
                binding.blockButton.text = getString(R.string.block_number)
                binding.blockButton.setTextColor(red)
            } else {
                addBlockedNumber(getCurrentPhoneNumber())
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
        if (getCurrentPhoneNumber().isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        //val positions = getSelectedItemPositions()
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.neighbourIDs.mapTo(idsToRemove, { it })
        }

        RecentsHelper(this).removeRecentCalls(idsToRemove) {
            //allRecentCall.removeAll(callsToRemove)
            runOnUiThread {
                if (allRecentCall.isEmpty()) {
                    refreshItems()
                    finishActMode()
                } else {
                    refreshItems()//recentsAdapter?.removePositions(positions)
                }
            }
        }
    }

    private fun finishActMode() {
        recentsAdapter?.finishActMode()
    }

    private fun viewContactInfo(contact: Contact) {
        this.startContactDetailsIntentRecommendation(contact)
    }

    private fun launchShare() {
        val text = getCurrentPhoneNumber()
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, getCurrentPhoneNumber())
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
}

// hide private contacts from recent calls
private fun List<RecentCall>.hidePrivateContacts(privateContacts: List<Contact>, shouldHide: Boolean): List<RecentCall> {
    return if (shouldHide) {
        filterNot { recent ->
            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
            recent.phoneNumber in privateNumbers
        }
    } else {
        this
    }
}

private fun List<RecentCall>.setNamesIfEmpty(contacts: List<Contact>, privateContacts: List<Contact>): ArrayList<RecentCall> {
    val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
    return map { recent ->
        if (recent.phoneNumber == recent.name) {
            val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
            val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }

            when {
                privateContact != null -> recent.copy(name = privateContact.getNameToDisplay())
                contact != null -> recent.copy(name = contact.getNameToDisplay())
                else -> recent
            }
        } else {
            recent
        }
    } as ArrayList
}
