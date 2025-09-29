package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.os.Bundle
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.google.gson.Gson
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.dialogs.RadioGroupIconDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.MyContactsContentProvider
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.commons.helpers.PERMISSION_READ_PHONE_STATE
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.SpeedDialAdapter
import com.goodwy.dialer.databinding.ActivityManageSpeedDialBinding
import com.goodwy.dialer.dialogs.AddSpeedDialDialog
import com.goodwy.dialer.dialogs.SelectContactDialog
import com.goodwy.dialer.dialogs.SelectSIMDialog
import com.goodwy.dialer.extensions.areMultipleSIMsAvailable
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getAvailableSIMCardLabels
import com.goodwy.dialer.extensions.getHandleToUse
import com.goodwy.dialer.interfaces.RemoveSpeedDialListener
import com.goodwy.dialer.models.SpeedDial

class ManageSpeedDialActivity : SimpleActivity(), RemoveSpeedDialListener {
    private val binding by viewBinding(ActivityManageSpeedDialBinding::inflate)

    private var allContacts = mutableListOf<Contact>()
    private var speedDialValues = mutableListOf<SpeedDial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            updateMaterialActivityViews(manageSpeedDialCoordinator, manageSpeedDialHolder, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(manageSpeedDialScrollview, manageSpeedDialToolbar)
        }

        speedDialValues = config.getSpeedDialValues()
        updateAdapter()

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts.addAll(contacts)

            val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        updateTextColors(binding.manageSpeedDialScrollview)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.manageSpeedDialToolbar, NavigationIcon.Arrow, navigationClick = false)
        binding.manageSpeedDialToolbar.setNavigationOnClickListener {
            hideKeyboard()
            onBackPressed()
        }
    }

    override fun onStop() {
        super.onStop()
        config.speedDial = Gson().toJson(speedDialValues)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        config.speedDial = Gson().toJson(speedDialValues)
    }

    @SuppressLint("MissingPermission")
    private fun updateAdapter() {
        SpeedDialAdapter(this, speedDialValues, this, binding.speedDialList) { any ->
            val clickedContact = any as SpeedDial
            if (allContacts.isEmpty()) {
                showAddSpeedDialDialog(clickedContact)
            }

            var readPhoneState = false
            handlePermission(PERMISSION_READ_PHONE_STATE) { permission ->
                readPhoneState = permission
            }
            val items =  if (readPhoneState) {
                arrayListOf(
                    RadioItem(1, getString(R.string.add_number), icon = R.drawable.ic_hashtag),
                    RadioItem(2, getString(R.string.choose_contact), icon = R.drawable.ic_view_contact_details_vector),
                    RadioItem(3, getString(R.string.voicemail), icon = R.drawable.ic_voicemail),
                    RadioItem(0, getString(R.string.delete), icon = R.drawable.ic_delete_outline)
                )
            } else {
                arrayListOf(
                    RadioItem(1, getString(R.string.add_number), icon = R.drawable.ic_hashtag),
                    RadioItem(2, getString(R.string.choose_contact), icon = R.drawable.ic_view_contact_details_vector),
                    RadioItem(0, getString(R.string.delete), icon = R.drawable.ic_delete_outline)
                )
            }

            RadioGroupIconDialog(this, items, titleId = R.string.speed_dial) {
                when (it) {
                    1 -> { showAddSpeedDialDialog(clickedContact) }
                    2 -> {
                        SelectContactDialog(this, allContacts) { selectedContact ->
                            if (selectedContact.phoneNumbers.size > 1) {
                                val radioItems = selectedContact.phoneNumbers.mapIndexed { index, item ->
                                    RadioItem(index, item.normalizedNumber, item)
                                }
                                val userPhoneNumbersList = selectedContact.phoneNumbers.map { it.value }
                                val checkedItemId = userPhoneNumbersList.indexOf(clickedContact.number)
                                RadioGroupDialog(this, ArrayList(radioItems), checkedItemId = checkedItemId) { selectedValue ->
                                    val selectedNumber = selectedValue as PhoneNumber
                                    speedDialValues.first { it.id == clickedContact.id }.apply {
                                        displayName = selectedContact.getNameToDisplay()
                                        number = selectedNumber.normalizedNumber
                                    }
                                    updateAdapter()
                                    showHideVoicemailIcon(clickedContact.id, false)
                                }
                            } else {
                                speedDialValues.first { it.id == clickedContact.id }.apply {
                                    displayName = selectedContact.getNameToDisplay()
                                    number = selectedContact.phoneNumbers.first().normalizedNumber
                                }
                                updateAdapter()
                                showHideVoicemailIcon(clickedContact.id, false)
                            }
                        }}

                    3 -> {
                        if (areMultipleSIMsAvailable()) {
                            SelectSIMDialog(this, "") { handle, label ->
                                val voiceMailNumber = telecomManager.getVoiceMailNumber(handle)
                                if (voiceMailNumber !=null) {
                                    speedDialValues.first { it.id == clickedContact.id }.apply {
                                        displayName = getString(R.string.voicemail) + "($label)"
                                        number = voiceMailNumber
                                    }
                                } else {
                                    toast(R.string.unknown)
                                }
                                updateAdapter()
                                showHideVoicemailIcon(clickedContact.id, true)
                            }
                        } else {
                            getHandleToUse(intent, "") { handle ->
                                val voiceMailNumber = telecomManager.getVoiceMailNumber(handle)
                                if (voiceMailNumber !=null) {
                                    val label = getAvailableSIMCardLabels().firstOrNull()?.label ?: ""
                                    speedDialValues.first { it.id == clickedContact.id }.apply {
                                        displayName = getString(R.string.voicemail) + "($label)"
                                        number = voiceMailNumber
                                    }
                                } else {
                                    toast(R.string.unknown)
                                }
                                updateAdapter()
                                showHideVoicemailIcon(clickedContact.id, true)
                            }
                        }
                    }

                    else -> {
                        speedDialValues.first { it.id == clickedContact.id }.apply {
                            displayName = ""
                            number = ""
                        }
                        updateAdapter()
                        showHideVoicemailIcon(clickedContact.id, false)
                    }
                }
            }
        }.apply {
            binding.speedDialList.adapter = this
        }
    }

    private fun showHideVoicemailIcon(id: Int, isVoicemail: Boolean) {
        if (id == 1) {
            if (isVoicemail) {
                if (!config.showVoicemailIcon) {
                    showHideVoicemailIconDialog()
                }
            } else {
                if (config.showVoicemailIcon) {
                    showHideVoicemailIconDialog()
                }
            }
        }
    }

    private fun showHideVoicemailIconDialog() {
        ConfirmationAdvancedDialog(
            this,
            messageId = R.string.show_voicemail_icon,
            positive = com.goodwy.commons.R.string.yes,
            negative = com.goodwy.commons.R.string.no
        ) {
            if (it) {
                config.showVoicemailIcon = true
            } else {
                config.showVoicemailIcon = false
            }
        }
    }

    private fun showAddSpeedDialDialog(clickedContact: SpeedDial) {
        AddSpeedDialDialog(this, clickedContact) { newNumber ->
            speedDialValues.first { it.id == clickedContact.id }.apply {
                displayName = newNumber
                number = newNumber
            }
            updateAdapter()
            showHideVoicemailIcon(clickedContact.id, true)
        }
    }

    override fun removeSpeedDial(ids: ArrayList<Int>) {
        ids.forEach { dialId ->
            speedDialValues.first { it.id == dialId }.apply {
                displayName = ""
                number = ""
            }
        }
        updateAdapter()
    }
}
