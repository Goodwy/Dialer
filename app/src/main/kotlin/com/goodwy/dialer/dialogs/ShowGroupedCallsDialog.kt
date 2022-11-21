package com.goodwy.dialer.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.adapters.RecentCallsAdapter
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.models.RecentCall
import kotlinx.android.synthetic.main.dialog_show_grouped_calls.view.*
import java.util.*

class ShowGroupedCallsDialog(val activity: BaseSimpleActivity, callIds: ArrayList<Int>) {
    private var dialog: AlertDialog? = null
    private var view = activity.layoutInflater.inflate(R.layout.dialog_show_grouped_calls, null)

    init {
        view.apply {
            RecentsHelper(activity).getRecentCalls(false) { allRecents ->
                val recents = allRecents.filter { callIds.contains(it.id) }.toMutableList() as ArrayList<RecentCall>
                activity.runOnUiThread {
                    RecentCallsAdapter(activity as SimpleActivity, recents, select_grouped_calls_list, null, false) {
                    }.apply {
                        select_grouped_calls_list.adapter = this
                    }
                }
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(view, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}
