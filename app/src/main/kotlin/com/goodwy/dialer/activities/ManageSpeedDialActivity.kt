package com.goodwy.dialer.activities

import android.os.Bundle
import com.goodwy.commons.extensions.hideKeyboard
import com.google.gson.Gson
import com.goodwy.commons.extensions.updateTextColors
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.SpeedDialAdapter
import com.goodwy.dialer.dialogs.SelectContactDialog
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.interfaces.RemoveSpeedDialListener
import com.goodwy.dialer.models.SpeedDial
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_manage_speed_dial.*

class ManageSpeedDialActivity : SimpleActivity(), RemoveSpeedDialListener {
    private var allContacts = ArrayList<Contact>()
    private var speedDialValues = ArrayList<SpeedDial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_speed_dial)

        updateMaterialActivityViews(manage_speed_dial_coordinator, manage_speed_dial_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(manage_speed_dial_scrollview, manage_speed_dial_toolbar)

        speedDialValues = config.getSpeedDialValues()
        updateAdapter()
        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts = contacts
        }

        updateTextColors(manage_speed_dial_scrollview)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(manage_speed_dial_toolbar, NavigationIcon.Arrow, navigationClick = false)
        manage_speed_dial_toolbar.setNavigationOnClickListener {
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
        SpeedDialAdapter(this, speedDialValues, this, speed_dial_list) {
            val clickedContact = it as SpeedDial
            if (allContacts.isEmpty()) {
                return@SpeedDialAdapter
            }

            SelectContactDialog(this, allContacts) { selectedContact ->
                speedDialValues.first { it.id == clickedContact.id }.apply {
                    displayName = selectedContact.getNameToDisplay()
                    number = selectedContact.phoneNumbers.first().normalizedNumber
                }
                updateAdapter()
            }
        }.apply {
            speed_dial_list.adapter = this
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
