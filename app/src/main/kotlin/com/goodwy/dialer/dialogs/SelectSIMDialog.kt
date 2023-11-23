package com.goodwy.dialer.dialogs

import android.annotation.SuppressLint
import android.telecom.PhoneAccountHandle
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.dialer.R
import com.goodwy.dialer.databinding.DialogSelectSimBinding
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getAvailableSIMCardLabels

@SuppressLint("MissingPermission")
class SelectSIMDialog(
    val activity: BaseSimpleActivity,
    val phoneNumber: String,
    onDismiss: () -> Unit = {},
    val callback: (handle: PhoneAccountHandle?) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogSelectSimBinding::inflate)

    init {
        binding.selectSimRememberHolder.setOnClickListener {
            binding.selectSimRemember.toggle()
        }

        activity.getAvailableSIMCardLabels().forEachIndexed { index, SIMAccount ->
            val radioButton = (activity.layoutInflater.inflate(R.layout.radio_button, null) as RadioButton).apply {
                text = "${index + 1} - ${SIMAccount.label}"
                id = index
                setOnClickListener { selectedSIM(SIMAccount.handle) }
            }
            binding.selectSimRadioGroup.addView(radioButton, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        activity.getAlertDialogBuilder()
            //.setOnCancelListener { callback.invoke(null) }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                }
            }

        dialog?.setOnDismissListener {
            onDismiss()
        }
    }

    private fun selectedSIM(handle: PhoneAccountHandle) {
        if (binding.selectSimRemember.isChecked) {
            activity.config.saveCustomSIM(phoneNumber, handle)
        }

        callback(handle)
        dialog?.dismiss()
    }
}
