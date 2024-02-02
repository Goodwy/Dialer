package com.goodwy.dialer.dialogs

import android.app.Activity
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.beGone
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.dialer.extensions.getPackageDrawable
import com.goodwy.dialer.databinding.DialogChooseSocialBinding
import com.goodwy.dialer.databinding.ItemChooseSocialBinding
import com.goodwy.commons.models.contacts.SocialAction

class ChooseSocialDialog(val activity: Activity, actions: ArrayList<SocialAction>, val callback: (action: SocialAction) -> Unit) {
    private lateinit var dialog: AlertDialog

    init {
        val binding = DialogChooseSocialBinding.inflate(activity.layoutInflater)
        actions.sortBy { it.type }
        actions.forEach { action ->
            val item = ItemChooseSocialBinding.inflate(activity.layoutInflater).apply {
                itemSocialLabel.text = action.label
                root.setOnClickListener {
                    callback(action)
                    dialog.dismiss()
                }

                val drawable = activity.getPackageDrawable(action.packageName)
                if (drawable == null) {
                    itemSocialImage.beGone()
                } else {
                    itemSocialImage.setImageDrawable(drawable)
                }
            }

            binding.dialogChooseSocial.addView(item.root, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val builder = activity.getAlertDialogBuilder()

        builder.apply {
            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }
}
