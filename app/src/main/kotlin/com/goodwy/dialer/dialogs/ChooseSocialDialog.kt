package com.goodwy.dialer.dialogs

import android.app.Activity
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.beGone
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.dialer.R
import com.goodwy.dialer.extensions.getPackageDrawable
import com.goodwy.commons.models.contacts.SocialAction
import kotlinx.android.synthetic.main.dialog_choose_social.view.*
import kotlinx.android.synthetic.main.item_choose_social.view.*

class ChooseSocialDialog(val activity: Activity, actions: ArrayList<SocialAction>, val callback: (action: SocialAction) -> Unit) {
    private lateinit var dialog: AlertDialog

    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_choose_social, null)
        actions.sortBy { it.type }
        actions.forEach { action ->
            val item = (activity.layoutInflater.inflate(R.layout.item_choose_social, null) as RelativeLayout).apply {
                item_social_label.text = action.label
                setOnClickListener {
                    callback(action)
                    dialog.dismiss()
                }

                val drawable = activity.getPackageDrawable(action.packageName)
                if (drawable == null) {
                    item_social_image.beGone()
                } else {
                    item_social_image.setImageDrawable(drawable)
                }
            }

            view.dialog_choose_social.addView(item, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val builder = activity.getAlertDialogBuilder()

        builder.apply {
            activity.setupDialogStuff(view, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }
}
