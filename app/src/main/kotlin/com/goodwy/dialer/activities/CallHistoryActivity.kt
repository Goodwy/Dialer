package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.SpannableString
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.SimpleContact
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.RecentCallsAdapter
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.interfaces.RefreshItemsListener
import com.goodwy.dialer.models.RecentCall
import kotlinx.android.synthetic.main.activity_call_history.*
import kotlinx.android.synthetic.main.top_view.*
import kotlin.collections.ArrayList

class CallHistoryActivity : SimpleActivity(), RefreshItemsListener {
    private var allContacts = ArrayList<SimpleContact>()
    private var allRecentCall = ArrayList<RecentCall>()
    private var privateCursor: Cursor? = null
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFFEBEBEB.toInt()

    private fun getCurrentPhoneNumber() = intent.getStringExtra(CURRENT_PHONE_NUMBER) ?: ""
    private fun isInternationalNumber() = getCurrentPhoneNumber()[0].toString() == "+"

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_history)
        //SimpleContactsHelper(this).getAvailableContacts(false) { gotContacts(it) }
        //SimpleContactsHelper(this).loadContactImage(getCall().photoUri, item_history_image, getCall().name)
        //oneButton.foreground.applyColorFilter(getProperPrimaryColor())
        //twoButton.foreground.applyColorFilter(getProperPrimaryColor())
        //threeButton.foreground.applyColorFilter(getProperPrimaryColor())
        //fourButton.foreground.applyColorFilter(getProperPrimaryColor())

        var drawableSMS = resources.getDrawable(R.drawable.ic_sms_vector)
        drawableSMS = DrawableCompat.wrap(drawableSMS!!)
        DrawableCompat.setTint(drawableSMS, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableSMS, PorterDuff.Mode.SRC_IN)
        oneButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableSMS, null, null)

        var drawableCall = resources.getDrawable(R.drawable.ic_phone_vector)
        drawableCall = DrawableCompat.wrap(drawableCall!!)
        DrawableCompat.setTint(drawableCall, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableCall, PorterDuff.Mode.SRC_IN)
        twoButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableCall, null, null)

        var drawableInfo = resources.getDrawable(R.drawable.ic_person_rounded)
        drawableInfo = DrawableCompat.wrap(drawableInfo!!)
        DrawableCompat.setTint(drawableInfo, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableInfo, PorterDuff.Mode.SRC_IN)
        threeButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableInfo, null, null)

        var drawableShare = resources.getDrawable(R.drawable.ic_ios_share)
        drawableShare = DrawableCompat.wrap(drawableShare!!)
        DrawableCompat.setTint(drawableShare, getProperPrimaryColor())
        DrawableCompat.setTintMode(drawableShare, PorterDuff.Mode.SRC_IN)
        fourButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableShare, null, null)

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

        val phoneNumberNormalizer = getPhoneNumberFormat(this@CallHistoryActivity, number = getCurrentPhoneNumber())
        val phoneNumber = if (isInternationalNumber()) phoneNumberNormalizer else getCurrentPhoneNumber()

        call_history_number_type.beGone()
        call_history_number_type.setTextColor(getProperTextColor())
        call_history_number_press.setOnClickListener {
            copyToClipboard(getCurrentPhoneNumber())
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
            call_history_birthdays_container.background = whiteButton
            call_history_birthdays_container.setPadding(padding, padding ,padding ,padding)
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
            findItem(R.id.delete).setOnMenuItemClickListener {
                askConfirmRemove()
                true
            }
        }

        val color = getProperBackgroundColor().getContrastColor()
        call_history_toolbar.setNavigationIconTint(color)
        call_history_toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun updateBackgroundHistory(color: Int = getProperBackgroundColor()) {
        val whiteBackgroundHistory = resources.getColoredDrawableWithColor(R.drawable.call_history_background_white, white)
        if (baseConfig.backgroundColor == white || baseConfig.backgroundColor == gray) {
            call_history_list.background = whiteBackgroundHistory
        }
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = this.getMyContactsCursor(false, true)
        val groupSubsequentCalls = false // группировать звонки?   this.config.groupSubsequentCalls ?: false
        RecentsHelper(this).getRecentCalls(groupSubsequentCalls) { recents ->
            SimpleContactsHelper(this@CallHistoryActivity).getAvailableContacts(false) { contacts ->
                val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

                recents.filter { it.phoneNumber == it.name }.forEach { recent ->
                    var wasNameFilled = false
                    if (privateContacts.isNotEmpty()) {
                        val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
                        if (privateContact != null) {
                            recent.name = privateContact.name
                            wasNameFilled = true
                        }
                    }

                    if (!wasNameFilled) {
                        val contact = contacts.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }
                        if (contact != null) {
                            recent.name = contact.name
                        }
                    }
                }

                allContacts = contacts
                allRecentCall = recents
                callback?.invoke()
                this.runOnUiThread {
                    if (recents.isEmpty()) {
                        call_history_list_container.beGone()
                        call_history_placeholder_container.beVisible()
                    } else {
                        call_history_list_container.beVisible()
                        call_history_placeholder_container.beGone()
                        gotRecents(recents)
                        updateBackgroundColors()
                        updateBackgroundHistory()
                        updateButton()
                    }
                }
            }
        }
    }

    private fun gotRecents(recents: ArrayList<RecentCall>) {
        if (recents.isEmpty()) {
            call_history_list_container.beGone()
            call_history_placeholder_container.beVisible()
        } else {
            call_history_list_container.beVisible()
            call_history_placeholder_container.beGone()

            val currAdapter = call_history_list.adapter
            val recents = allRecentCall.filter { it.phoneNumber == getCurrentPhoneNumber()}.toMutableList() as java.util.ArrayList<RecentCall>
            if (currAdapter == null) {
                RecentCallsAdapter(this as SimpleActivity, recents, call_history_list, null) {
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
                (currAdapter as RecentCallsAdapter).updateItems(recents)
            }
        }
    }

    private fun updateButton() {
        val call: RecentCall? = getCallList().firstOrNull()
        if (call != null) {

            val contact = getContactList()
            if (contact != null) {
                threeButton.apply {
                    var drawableInfo = resources.getDrawable(R.drawable.ic_person_rounded)
                    drawableInfo = DrawableCompat.wrap(drawableInfo!!)
                    DrawableCompat.setTint(drawableInfo, getProperPrimaryColor())
                    DrawableCompat.setTintMode(drawableInfo, PorterDuff.Mode.SRC_IN)
                    setCompoundDrawablesWithIntrinsicBounds(null, drawableInfo, null, null)
                    setOnClickListener {
                        viewContactInfo(contact)
                    }
                }
                if (contact.birthdays.firstOrNull() != null) {
                    val monthName = getDateFormatFromDateString(this@CallHistoryActivity, contact.birthdays.first(), "yyyy-MM-dd")
                    call_history_birthdays_container.beVisible()
                    call_history_birthdays_press.setOnClickListener {
                        copyToClipboard(monthName!!)
                    }
                    call_history_birthdays_title.apply {
                        setTextColor(getProperTextColor())
                    }
                    call_history_birthdays.apply {
                        text = monthName
                        setTextColor(getProperPrimaryColor())
                    }
                }

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
            } else {
                val country = getCountryByNumber(this, getCurrentPhoneNumber())
                if (country != "") {
                    call_history_number_type_container.beVisible()
                    call_history_number_type.apply {
                        beVisible()
                        text = country
                    }
                }

                threeButton.apply {
                    text = resources.getString(R.string.save)
                    var drawableInfo = resources.getDrawable(R.drawable.ic_add_person_vector)
                    drawableInfo = DrawableCompat.wrap(drawableInfo!!)
                    DrawableCompat.setTint(drawableInfo, getProperPrimaryColor())
                    DrawableCompat.setTintMode(drawableInfo, PorterDuff.Mode.SRC_IN)
                    setCompoundDrawablesWithIntrinsicBounds(null, drawableInfo, null, null)
                    setOnClickListener {
                        Intent().apply {
                            action = Intent.ACTION_INSERT_OR_EDIT
                            type = "vnd.android.cursor.item/contact"
                            putExtra(KEY_PHONE, getCurrentPhoneNumber())
                            launchActivityIntent(this)
                        }
                    }
                }
            }

            if (call.phoneNumber == call.name || isDestroyed || isFinishing) {
                //SimpleContactsHelper(this).loadContactImage(call.photoUri, call_history_image, call.name, letter = false)
                val drawable = resources.getDrawable(R.drawable.placeholder_contact)
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
            if (nameToShow[0].toString() == "+") nameToShow = SpannableString(getPhoneNumberFormat(this, number = nameToShow.toString()))
            call_history_name.apply {
                text = nameToShow
                setTextColor(getProperTextColor())
                setOnClickListener {
                    copyToClipboard(call.name)
                }
            }

            oneButton.apply {
                setOnClickListener {
                    launchSendSMSIntent(call.phoneNumber)
                }
            }

            twoButton.apply {
                setOnClickListener {
                    makeСall(call)
                }
            }

            fourButton.apply {
                setOnClickListener {
                    launchShare()
                }
            }

            blockButton.apply {
                setOnClickListener {
                    askConfirmBlock()
                }
            }
        } else {
            call_history_list_container.beGone()
            call_history_placeholder_container.beVisible()
        }
    }

    private fun getItemCount() = getSelectedItems().size

    private fun getSelectedItems() = allRecentCall.filter { getCurrentPhoneNumber().contains(it.phoneNumber) } as java.util.ArrayList<RecentCall>

    private fun getCallList() = allRecentCall.filter { it.phoneNumber == getCurrentPhoneNumber()}.toMutableList() as java.util.ArrayList<RecentCall>

    private fun getContactList() = allContacts.firstOrNull { it.doesContainPhoneNumber(getCurrentPhoneNumber()) }

    private fun makeСall(call: RecentCall) {
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
        val idsToRemove = java.util.ArrayList<Int>()
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

    private fun viewContactInfo(contact: SimpleContact) {
        this.startContactDetailsIntent(contact)
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
}
