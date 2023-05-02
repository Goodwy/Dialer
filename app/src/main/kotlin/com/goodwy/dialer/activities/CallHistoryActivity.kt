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
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
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
import com.goodwy.dialer.adapters.RecentCallsAdapter
import com.goodwy.dialer.dialogs.ChooseSocialDialog
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.interfaces.RefreshItemsListener
import com.goodwy.dialer.models.RecentCall
import kotlinx.android.synthetic.main.activity_call_history.*
import kotlinx.android.synthetic.main.activity_dialpad.*
import kotlinx.android.synthetic.main.item_view_email.*
import kotlinx.android.synthetic.main.item_view_email.contact_email
import kotlinx.android.synthetic.main.item_view_email.contact_email_holder
import kotlinx.android.synthetic.main.item_view_email.contact_email_type
import kotlinx.android.synthetic.main.item_view_email.view.*
import kotlinx.android.synthetic.main.item_view_event.*
import kotlinx.android.synthetic.main.item_view_event.view.*
import kotlinx.android.synthetic.main.item_view_messengers_actions.*
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_account
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_call
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_call_icon
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_holder
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_message
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_message_icon
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_name
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_number
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_video
import kotlinx.android.synthetic.main.item_view_messengers_actions.contact_messenger_action_video_icon
import kotlinx.android.synthetic.main.item_view_messengers_actions.view.*
import kotlinx.android.synthetic.main.top_view.*
import kotlin.collections.ArrayList

class CallHistoryActivity : SimpleActivity(), RefreshItemsListener {
    private var allContacts = ArrayList<Contact>()
    private var allRecentCall = ArrayList<RecentCall>()
    private var contact: Contact? = null
    private var duplicateContacts = ArrayList<Contact>()
    private var contactSources = ArrayList<ContactSource>()
    private var privateCursor: Cursor? = null
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFFEBEBEB.toInt()

    private fun getCurrentPhoneNumber() = intent.getStringExtra(CURRENT_PHONE_NUMBER) ?: ""

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_history)

        updateMaterialActivityViews(call_history_wrapper, call_history_holder, useTransparentNavigation = false, useTopSearchMenu = false)
        setWindowTransparency(true) { _, _, leftNavigationBarSize, rightNavigationBarSize ->
            call_history_wrapper.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
            updateNavigationBarColor(getProperBackgroundColor())
        }
        //SimpleContactsHelper(this).getAvailableContacts(false) { gotContacts(it) }
        //SimpleContactsHelper(this).loadContactImage(getCall().photoUri, item_history_image, getCall().name)
        //oneButton.foreground.applyColorFilter(getProperPrimaryColor())
        //twoButton.foreground.applyColorFilter(getProperPrimaryColor())
        //threeButton.foreground.applyColorFilter(getProperPrimaryColor())
        //fourButton.foreground.applyColorFilter(getProperPrimaryColor())

        var drawableSMS = AppCompatResources.getDrawable(this, R.drawable.ic_messages)
        drawableSMS = DrawableCompat.wrap(drawableSMS!!)
        DrawableCompat.setTint(drawableSMS, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableSMS, PorterDuff.Mode.SRC_IN)
        oneButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableSMS, null, null)

        var drawableCall = AppCompatResources.getDrawable(this, R.drawable.ic_phone_vector)
        drawableCall = DrawableCompat.wrap(drawableCall!!)
        DrawableCompat.setTint(drawableCall, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableCall, PorterDuff.Mode.SRC_IN)
        twoButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableCall, null, null)

        var drawableInfo = AppCompatResources.getDrawable(this, R.drawable.ic_videocam_vector)
        drawableInfo = DrawableCompat.wrap(drawableInfo!!)
        DrawableCompat.setTint(drawableInfo, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableInfo, PorterDuff.Mode.SRC_IN)
        threeButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableInfo, null, null)
        threeButton.alpha = 0.5f

        var drawableShare = AppCompatResources.getDrawable(this, R.drawable.ic_mail_vector)
        drawableShare = DrawableCompat.wrap(drawableShare!!)
        DrawableCompat.setTint(drawableShare, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableShare, PorterDuff.Mode.SRC_IN)
        fourButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableShare, null, null)
        fourButton.alpha = 0.5f

        oneButton.setTextColor(getProperPrimaryColor())
        twoButton.setTextColor(getProperPrimaryColor())
        threeButton.setTextColor(getProperPrimaryColor())
        fourButton.setTextColor(getProperPrimaryColor())
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        call_history_placeholder_container.beGone()
        updateTextColors(call_history_holder)
        updateBackgroundColors()
        refreshItems()
        setupMenu()
    }

    private fun updateBackgroundColors(color: Int = getProperBackgroundColor()) {
        val whiteButton = AppCompatResources.getDrawable(this, R.drawable.call_history_button_white)//resources.getColoredDrawableWithColor(R.drawable.call_history_button_white, white)
        val whiteBackgroundHistory = AppCompatResources.getDrawable(this, R.drawable.call_history_background_white)//resources.getColoredDrawableWithColor(R.drawable.call_history_background_white, white)
        val red = resources.getColor(R.color.red_missed)

        val phoneNumber = if (getCurrentPhoneNumber().startsWith("+")) getPhoneNumberFormat(this@CallHistoryActivity, number = getCurrentPhoneNumber()) else getCurrentPhoneNumber()

        call_history_number_type.beGone()
        call_history_number_type.setTextColor(getProperTextColor())
        call_history_number_press.setOnClickListener {
            val call: RecentCall? = getCallList().firstOrNull()
            if (call != null) {
                makeCall(call)
            }
        }
        call_history_number_press.setOnLongClickListener {
            copyToClipboard(call_history_number.text.toString())
            true
        }
        call_history_number.text = phoneNumber
        call_history_number.setTextColor(getProperPrimaryColor())

        if (baseConfig.backgroundColor == white) {
            supportActionBar?.setBackgroundDrawable(ColorDrawable(0xFFf2f2f6.toInt()))
            window.decorView.setBackgroundColor(0xFFf2f2f6.toInt())
            window.statusBarColor = 0xFFf2f2f6.toInt()
            window.navigationBarColor = 0xFFf2f2f6.toInt()
        } else window.decorView.setBackgroundColor(color)
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray || (baseConfig.isUsingSystemTheme && !isUsingSystemDarkTheme())) {
            call_history_placeholder_container.background = whiteButton
            oneButton.background = whiteButton
            twoButton.background = whiteButton
            threeButton.background = whiteButton
            fourButton.background = whiteButton
            val paddingLeftRight = resources.getDimensionPixelOffset(R.dimen.small_margin)
            val paddingTop = resources.getDimensionPixelOffset(R.dimen.ten_dpi)
            val paddingBottom = resources.getDimensionPixelOffset(R.dimen.medium_margin)
            oneButton.setPadding(paddingLeftRight, paddingTop ,paddingLeftRight ,paddingBottom)
            twoButton.setPadding(paddingLeftRight, paddingTop ,paddingLeftRight ,paddingBottom)
            threeButton.setPadding(paddingLeftRight, paddingTop ,paddingLeftRight ,paddingBottom)
            fourButton.setPadding(paddingLeftRight, paddingTop ,paddingLeftRight ,paddingBottom)
            val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
            call_history_list.background = whiteBackgroundHistory
            call_history_number_container.background = whiteButton
            call_history_number_container.setPadding(padding, padding ,padding ,padding)
            contact_messengers_actions_holder.background = whiteButton
            contact_messengers_actions_holder.setPadding(padding, padding ,padding ,padding)
            contact_emails_holder.background = whiteButton
            contact_emails_holder.setPadding(padding, padding ,padding ,padding)
            contact_events_holder.background = whiteButton
            contact_events_holder.setPadding(padding, padding ,padding ,padding)
            val blockcolor = if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers())) { getProperPrimaryColor() } else { red }
            blockButton.setTextColor(blockcolor)
            blockButton.setPadding(padding, padding ,padding ,padding)
            val blockText = if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers()))
                { resources.getString(R.string.unblock_number) } else { resources.getString(R.string.block_number)}
            blockButton.text = blockText
            blockButton.background = whiteButton
        } else window.decorView.setBackgroundColor(color)
    }

    private fun setupMenu() {
        call_history_toolbar.menu.apply {
            updateMenuItemColors(this)
            if (contact != null) {
                findItem(R.id.favorite).setOnMenuItemClickListener {
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
                    val favoriteIcon = getStarDrawable(contact!!.starred == 1)
                    favoriteIcon!!.setTint(getProperBackgroundColor().getContrastColor())
                    findItem(R.id.favorite).icon = favoriteIcon
                    true
                }
            }
            findItem(R.id.delete).setOnMenuItemClickListener {
                askConfirmRemove()
                true
            }
            findItem(R.id.share).setOnMenuItemClickListener {
                launchShare()
                true
            }
        }

        val color = getProperBackgroundColor().getContrastColor()
        call_history_toolbar.setNavigationIconTint(color)
        call_history_toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun getStarDrawable(on: Boolean) = AppCompatResources.getDrawable(this, if (on) R.drawable.ic_star_vector else R.drawable.ic_star_outline_vector)

    private fun updateBackgroundHistory(color: Int = getProperBackgroundColor()) {
        val whiteBackgroundHistory = resources.getColoredDrawableWithColor(R.drawable.call_history_background_white, white)
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray || (baseConfig.isUsingSystemTheme && !isUsingSystemDarkTheme())) {
            call_history_list.background = whiteBackgroundHistory
        }
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = this.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        val groupSubsequentCalls = false // группировать звонки?   this.config.groupSubsequentCalls ?: false
        RecentsHelper(this).getRecentCalls(groupSubsequentCalls) { recents ->
            ContactsHelper(this@CallHistoryActivity).getContacts { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)

                recents.filter { it.phoneNumber == it.name }.forEach { recent ->
                    var wasNameFilled = false
                    if (privateContacts.isNotEmpty()) {
                        val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
                        if (privateContact != null) {
                            recent.name = privateContact.getNameToDisplay()
                            wasNameFilled = true
                        }
                    }

                    if (!wasNameFilled) {
                        val contact = contacts.filter { it.phoneNumbers.isNotEmpty() }.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }
                        if (contact != null) {
                            recent.name = contact.getNameToDisplay()
                        }
                    }
                }

                //allContacts = contacts
                gotContacts(contacts)
                allRecentCall = recents
                callback?.invoke()
                this.runOnUiThread {
                    if (recents.isEmpty()) {
                        call_history_list_container.beGone()
                        call_history_placeholder_container.beVisible()
                        call_history_toolbar.menu.findItem(R.id.delete).isVisible = false
                    } else {
                        call_history_list_container.beVisible()
                        call_history_placeholder_container.beGone()
                        call_history_toolbar.menu.findItem(R.id.delete).isVisible = true
                        gotRecents(allRecentCall)
                        updateBackgroundColors()
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

//        runOnUiThread {
//            if (!checkDialIntent() && dialpad_input.value.isEmpty()) {
//                dialpadValueChanged("")
//            }
//        }
    }

    private fun gotRecents(recents: ArrayList<RecentCall>) {
        if (recents.isEmpty()) {
            call_history_list_container.beGone()
            call_history_placeholder_container.beVisible()
            call_history_toolbar.menu.findItem(R.id.delete).isVisible = false
        } else {
            call_history_list_container.beVisible()
            call_history_placeholder_container.beGone()
            call_history_toolbar.menu.findItem(R.id.delete).isVisible = true

            val currAdapter = call_history_list.adapter
            val recent = recents.filter { it.phoneNumber == getCurrentPhoneNumber()}.toMutableList() as ArrayList<RecentCall>
            if (currAdapter == null) {
                RecentCallsAdapter(this as SimpleActivity, recent, call_history_list, null) {
                    /*val recentCall = it as RecentCall
                if (this.config.showCallConfirmation) {
                    CallConfirmationDialog(this as SimpleActivity, recentCall.name) {
                        this.launchCallIntent(recentCall.phoneNumber)
                    }
                } else {
                    this.launchCallIntent(recentCall.phoneNumber)
                }*/
                }.apply {
                    call_history_list.adapter = this
                }

                if (this.areSystemAnimationsEnabled) {
                    call_history_list.scheduleLayoutAnimation()
                }
                updateBackgroundColors()
            } else {
                (currAdapter as RecentCallsAdapter).updateItems(recent)
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
                        setupFavorite()
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
        call_history_toolbar.menu.findItem(R.id.favorite).isVisible = true
        val favoriteIcon = getStarDrawable(contact!!.starred == 1)
        favoriteIcon!!.setTint(getProperBackgroundColor().getContrastColor())
        call_history_toolbar.menu.findItem(R.id.favorite).icon = favoriteIcon
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
                sources = sources.toList().sortedBy { (key, value) -> value.toLowerCase() }.toMap() as LinkedHashMap<Contact, String>
            }

            val videoActions = arrayListOf<SocialAction>()
            for ((key, value) in sources) {

                if (value.toLowerCase() == WHATSAPP) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val whatsappVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(whatsappVideoActions)
                    }
                }

                if (value.toLowerCase() == SIGNAL) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val signalVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(signalVideoActions)
                    }
                }

                if (value.toLowerCase() == VIBER) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val viberVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(viberVideoActions)
                    }
                }

                if (value.toLowerCase() == TELEGRAM) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val telegramVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(telegramVideoActions)
                    }
                }

                if (value.toLowerCase() == THREEMA) {
                    val actions = getSocialActions(key.id)
                    if (actions.firstOrNull() != null) {
                        val threemaVideoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                        videoActions.addAll(threemaVideoActions)
                    }
                }
            }

            threeButton.alpha = if (videoActions.isNotEmpty()) 1f else 0.5f

            if (videoActions.isNotEmpty()) threeButton.setOnClickListener { showVideoCallAction(videoActions) }
            threeButton.setOnLongClickListener { toast(R.string.video_call); true; }
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
        contact_messengers_actions_holder.removeAllViews()
        //val contact = allContacts.first { it.phoneNumbers.any {it.normalizedNumber == getCurrentPhoneNumber()} }//getContactList()
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
                layoutInflater.inflate(R.layout.item_view_messengers_actions, contact_messengers_actions_holder, false).apply {
                    contact_messenger_action_name.text = if (value == "") getString(R.string.phone_storage) else value
                    contact_messenger_action_account.text = " (ID:" + key.source + ")"
                    contact_messenger_action_name.setTextColor(getProperTextColor())
                    contact_messenger_action_account.setTextColor(getProperTextColor())
                    contact_messenger_action_holder.setOnClickListener {
                        if (contact_messenger_action_account.isVisible()) contact_messenger_action_account.beGone()
                        else contact_messenger_action_account.beVisible()
                    }
                    contact_messenger_action_number.setTextColor(getProperPrimaryColor())
                    contact_messengers_actions_holder.addView(this)

                    val whiteButton = AppCompatResources.getDrawable(this@CallHistoryActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray || (baseConfig.isUsingSystemTheme && !isUsingSystemDarkTheme())) {
                        contact_messengers_actions_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_messengers_actions_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_messenger_action_message_icon.background.setTint(getProperTextColor())
                    contact_messenger_action_message_icon.background.alpha = 40
                    contact_messenger_action_message_icon.setColorFilter(getProperPrimaryColor())
                    contact_messenger_action_call_icon.background.setTint(getProperTextColor())
                    contact_messenger_action_call_icon.background.alpha = 40
                    contact_messenger_action_call_icon.setColorFilter(getProperPrimaryColor())
                    contact_messenger_action_video_icon.background.setTint(getProperTextColor())
                    contact_messenger_action_video_icon.background.alpha = 40
                    contact_messenger_action_video_icon.setColorFilter(getProperPrimaryColor())
                    contact_messenger_action_holder.divider_contact_messenger_action.setBackgroundColor(getProperTextColor())
                    contact_messenger_action_holder.divider_contact_messenger_action.beGoneIf(isLastItem == key)

                    if (value.toLowerCase() == WHATSAPP) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.toLowerCase() == SIGNAL) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible() //hide not messengers
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.toLowerCase() == VIBER) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            contact_messenger_action_number.beGoneIf(contact!!.phoneNumbers.size > 1 && messageActions.isEmpty())
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.toLowerCase() == TELEGRAM) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(messageActions)
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(callActions)
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    //startMessengerAction(videoActions)
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }

                    if (value.toLowerCase() == THREEMA) {
                        val actions = getSocialActions(key.id)
                        if (actions.firstOrNull() != null) {
                            val plus = if (actions.firstOrNull()!!.label.contains("+", ignoreCase = true)) "+" else ""
                            val number = plus + actions.firstOrNull()!!.label.filter { it.isDigit() }
                            contact_messenger_action_number.text = number
                            copyOnLongClick(number)
                            contact_messengers_actions_holder.beVisible()
                            contact_messenger_action_holder.beVisible()
                            val callActions = actions.filter { it.type == 0 } as ArrayList<SocialAction>
                            val videoActions = actions.filter { it.type == 1 } as ArrayList<SocialAction>
                            val messageActions = actions.filter { it.type == 2 } as ArrayList<SocialAction>
                            if (messageActions.isNotEmpty()) contact_messenger_action_message.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(messageActions)
                                }
                            }
                            if (callActions.isNotEmpty()) contact_messenger_action_call.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(callActions)
                                }
                            }
                            if (videoActions.isNotEmpty()) contact_messenger_action_video.apply {
                                beVisible()
                                setOnClickListener {
                                    showMessengerAction(videoActions)
                                }
                            }
                        }
                    }
                }
            }
            //contact_messengers_actions_holder.beVisible()
        } else {
            contact_messengers_actions_holder.beGone()
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
        contact_emails_holder.removeAllViews()
        val emails = contact!!.emails
        if (emails.isNotEmpty()) {

            fourButton.apply {
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
                layoutInflater.inflate(R.layout.item_view_email, contact_emails_holder, false).apply {
                    val email = it
                    contact_emails_holder.addView(this)
                    contact_email.text = email.value
                    contact_email_type.text = getEmailTypeText(email.type, email.label)
                    contact_email_type.setTextColor(getProperTextColor())
                    copyOnLongClick(email.value)

                    setOnClickListener {
                        sendEmailIntent(email.value)
                    }

                    val whiteButton = AppCompatResources.getDrawable(this@CallHistoryActivity, R.drawable.call_history_button_white)
                    if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray || (baseConfig.isUsingSystemTheme && !isUsingSystemDarkTheme())) {
                        contact_emails_holder.background = whiteButton
                        val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                        contact_emails_holder.setPadding(padding, padding ,padding ,padding)
                    }

                    contact_email_holder.contact_email_icon.beVisibleIf(isFirstItem == email)
                    contact_email_holder.contact_email_icon.setColorFilter(getProperTextColor())
                    contact_email_holder.divider_contact_email.setBackgroundColor(getProperTextColor())
                    contact_email_holder.divider_contact_email.beGoneIf(isLastItem == email)
                    contact_email_holder.contact_email.setTextColor(getProperPrimaryColor())
                }
            }
            contact_emails_holder.beVisible()
        } else {
            contact_emails_holder.beGone()
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
            contact_events_holder.removeAllViews()

            if (events.isNotEmpty()) {
                val isFirstItem = events.first()
                val isLastItem = events.last()
                events.forEach {
                    layoutInflater.inflate(R.layout.item_view_event, contact_events_holder, false).apply {
                        val event = it
                        contact_events_holder.addView(this)
                        it.value.getDateTimeFromDateString(true, contact_event)
                        contact_event_type.setText(getEventTextId(it.type))
                        contact_event_type.setTextColor(getProperTextColor())
                        copyOnLongClick(it.value)

                        val whiteButton = AppCompatResources.getDrawable(this@CallHistoryActivity, R.drawable.call_history_button_white)
                        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray || (baseConfig.isUsingSystemTheme && !isUsingSystemDarkTheme())) {
                            contact_events_holder.background = whiteButton
                            val padding = resources.getDimensionPixelOffset(R.dimen.small_margin)
                            contact_events_holder.setPadding(padding, padding, padding, padding)
                        }

                        contact_event_holder.contact_event_icon.beVisibleIf(isFirstItem == event)
                        contact_event_holder.contact_event_icon.setColorFilter(getProperTextColor())
                        contact_event_holder.divider_contact_event.setBackgroundColor(getProperTextColor())
                        contact_event_holder.divider_contact_event.beGoneIf(isLastItem == event)
                        contact_event_holder.contact_event.setTextColor(getProperPrimaryColor())
                    }
                }
                contact_events_holder.beVisible()
            } else {
                contact_events_holder.beGone()
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
                    call_history_number_type_container.beVisible()
                    call_history_number_type.apply {
                        beVisible()
                        //text = contact.phoneNumbers.filter { it.normalizedNumber == getCurrentPhoneNumber()}.toString()
                        val phoneNumberType = contact.phoneNumbers.first { it.normalizedNumber == getCurrentPhoneNumber() }.type
                        val phoneNumberLabel = contact.phoneNumbers.first { it.normalizedNumber == getCurrentPhoneNumber() }.label
                        text = getPhoneNumberTypeText(phoneNumberType, phoneNumberLabel)
                    }
                    call_history_favorite_icon.apply {
                        beVisibleIf(contact.phoneNumbers.first { it.normalizedNumber == getCurrentPhoneNumber() }.isPrimary)
                        applyColorFilter(getProperTextColor())
                    }
                }

                arrayOf(
                    call_history_image, call_history_name
                ).forEach {
                    it.setOnClickListener { viewContactInfo(contact) }
                }
                call_history_image.setOnLongClickListener { toast(R.string.contact_details); true; }
            } else {
                val country = if (getCurrentPhoneNumber().startsWith("+")) getCountryByNumber(this, getCurrentPhoneNumber()) else ""
                if (country != "") {
                    call_history_number_type_container.beVisible()
                    call_history_number_type.apply {
                        beVisible()
                        text = country
                    }
                }

                arrayOf(
                    call_history_image, call_history_name
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
                call_history_image.setOnLongClickListener { toast(R.string.add_contact); true; }
            }

            if (call.phoneNumber == call.name || isDestroyed || isFinishing) {
                //SimpleContactsHelper(this).loadContactImage(call.photoUri, call_history_image, call.name, letter = false)
                val drawable = AppCompatResources.getDrawable(this, R.drawable.placeholder_contact)
                if (baseConfig.useColoredContacts) {
                    val color = letterBackgroundColors[Math.abs(call.name.hashCode()) % letterBackgroundColors.size].toInt()
                    (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                }
                call_history_image.setImageDrawable(drawable)
            } else {
                SimpleContactsHelper(this.applicationContext).loadContactImage(call.photoUri, call_history_image, call.name)
            }

            call_history_placeholder_container.beGone()

            var nameToShow = SpannableString(call.name)
            if (nameToShow.startsWith("+")) nameToShow = SpannableString(getPhoneNumberFormat(this, number = nameToShow.toString()))
            call_history_name.apply {
                text = nameToShow
                setTextColor(getProperTextColor())
                setOnLongClickListener {
                    copyToClipboard(nameToShow.toString())
                    true
                }
            }

            oneButton.apply {
                setOnClickListener {
                    launchSendSMSIntentRecommendation(call.phoneNumber)
                }
                setOnLongClickListener { toast(R.string.send_sms); true; }
            }

            twoButton.apply {
                setOnClickListener {
                    makeCall(call)
                }
                setOnLongClickListener { toast(R.string.call); true; }
            }

            blockButton.apply {
                setOnClickListener {
                    askConfirmBlock()
                }
            }
        } else {
            call_history_list_container.beGone()
            call_history_placeholder_container.beVisible()
            call_history_toolbar.menu.findItem(R.id.delete).isVisible = false
        }
    }

    private fun getItemCount() = getSelectedItems().size

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

    private fun askConfirmBlock() {
        val baseString = if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers())) {
            R.string.unblock_confirmation
        } else { R.string.block_confirmation }
        val question = String.format(resources.getString(baseString), getCurrentPhoneNumber())

        ConfirmationDialog(this, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        val red = resources.getColor(R.color.red_missed)
        //ensureBackgroundThread {
        runOnUiThread {
            if (isNumberBlocked(getCurrentPhoneNumber(), getBlockedNumbers())) {
                deleteBlockedNumber(getCurrentPhoneNumber())
                blockButton.text = getString(R.string.block_number)
                blockButton.setTextColor(red)
            } else {
                addBlockedNumber(getCurrentPhoneNumber())
                blockButton.text = getString(R.string.unblock_number)
                blockButton.setTextColor(getProperPrimaryColor())
            }
        }
    }

    private fun askConfirmRemove() {
        val message = if ((call_history_list?.adapter as? RecentCallsAdapter)?.getSelectedItems()!!.isEmpty()) {
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
                    refreshItems()//(call_history_list?.adapter as? RecentCallsAdapter)?.removePositions(positions)
                }
            }
        }
    }

    private fun finishActMode() {
        (call_history_list?.adapter as? RecentCallsAdapter)?.finishActMode()
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
