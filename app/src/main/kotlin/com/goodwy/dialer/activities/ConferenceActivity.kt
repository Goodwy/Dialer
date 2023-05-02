package com.goodwy.dialer.activities

import android.content.Intent
import android.os.Bundle
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ConferenceCallsAdapter
import com.goodwy.dialer.helpers.CallManager
import com.goodwy.dialer.helpers.NoCall
import com.goodwy.dialer.helpers.SingleCall
import kotlinx.android.synthetic.main.activity_conference.*
import kotlinx.android.synthetic.main.activity_main.*

class ConferenceActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conference)

        updateMaterialActivityViews(conference_coordinator, conference_list, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(conference_list, conference_toolbar)
        conference_list.adapter = ConferenceCallsAdapter(this, conference_list, ArrayList(CallManager.getConferenceCalls())) {}
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(conference_toolbar, NavigationIcon.Arrow)
    }

    override fun onBackPressed() {
        when (CallManager.getPhoneState()) {
            NoCall -> {
                finishAndRemoveTask()
            }
            else -> {
                startActivity(Intent(this, CallActivity::class.java))
                super.onBackPressed()
            }
        }
    }
}
