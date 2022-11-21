package com.goodwy.dialer.activities

import android.os.Bundle
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ConferenceCallsAdapter
import com.goodwy.dialer.helpers.CallManager
import kotlinx.android.synthetic.main.activity_conference.*

class ConferenceActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conference)

        conference_calls_list.adapter = ConferenceCallsAdapter(this, conference_calls_list, ArrayList(CallManager.getConferenceCalls())) {}
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(conference_toolbar, NavigationIcon.Arrow)
    }
}
