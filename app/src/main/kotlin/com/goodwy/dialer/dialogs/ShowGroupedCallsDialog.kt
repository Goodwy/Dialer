package com.goodwy.dialer.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.adapters.RecentCallsAdapter
import com.goodwy.dialer.databinding.DialogShowGroupedCallsBinding
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.models.RecentCall

class ShowGroupedCallsDialog(val activity: BaseSimpleActivity, callIds: ArrayList<Int>) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogShowGroupedCallsBinding::inflate)

    init {
        RecentsHelper(activity).getRecentCalls(false) { allRecents ->
            val recents = allRecents.filter { callIds.contains(it.id) }.toMutableList() as ArrayList<RecentCall>
            activity.runOnUiThread {
                RecentCallsAdapter(activity as SimpleActivity, recents, binding.selectGroupedCallsList, null, false) {
                }.apply {
                    binding.selectGroupedCallsList.adapter = this
                }
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}
