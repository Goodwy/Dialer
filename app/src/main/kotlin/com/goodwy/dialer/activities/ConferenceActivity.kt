package com.goodwy.dialer.activities

import android.content.Intent
import android.os.Bundle
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.dialer.adapters.ConferenceCallsAdapter
import com.goodwy.dialer.databinding.ActivityConferenceBinding
import com.goodwy.dialer.helpers.CallManager
import com.goodwy.dialer.helpers.NoCall
import com.goodwy.dialer.helpers.SHOW_RECENT_CALLS_ON_DIALPAD

class ConferenceActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityConferenceBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(conferenceList))
            setupMaterialScrollListener(conferenceList, conferenceAppbar)
            conferenceList.adapter = ConferenceCallsAdapter(this@ConferenceActivity, conferenceList, ArrayList(CallManager.getConferenceCalls())) {}
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.conferenceAppbar, NavigationIcon.Arrow)
    }

    override fun onBackPressedCompat(): Boolean {
        return when (CallManager.getPhoneState()) {
            NoCall -> {
                finishAndRemoveTask()
                true
            }
            else -> {
                startActivity(Intent(this, CallActivity::class.java))
                true
            }
        }
    }
}
