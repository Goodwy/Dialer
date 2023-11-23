package com.goodwy.dialer.activities

import android.os.Bundle
import com.google.gson.Gson
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.getMyContactsCursor
import com.goodwy.commons.extensions.hideKeyboard
import com.goodwy.commons.extensions.updateTextColors
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.MyContactsContentProvider
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.adapters.SpeedDialAdapter
import com.goodwy.dialer.databinding.ActivityManageSpeedDialBinding
import com.goodwy.dialer.dialogs.SelectContactDialog
import com.goodwy.dialer.extensions.config
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

            val privateCursor = getMyContactsCursor(false, true)
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

    private fun updateAdapter() {
        SpeedDialAdapter(this, speedDialValues, this, binding.speedDialList) {
            val clickedContact = it as SpeedDial
            if (allContacts.isEmpty()) {
                return@SpeedDialAdapter
            }

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
                    }
                } else {
                    speedDialValues.first { it.id == clickedContact.id }.apply {
                        displayName = selectedContact.getNameToDisplay()
                        number = selectedContact.phoneNumbers.first().normalizedNumber
                    }
                    updateAdapter()
                }
            }
        }.apply {
            binding.speedDialList.adapter = this
        }
    }

    override fun removeSpeedDial(ids: ArrayList<Int>) {
        ids.forEach {
            val dialId = it
            speedDialValues.first { it.id == dialId }.apply {
                displayName = ""
                number = ""
            }
        }
        updateAdapter()
    }
}
