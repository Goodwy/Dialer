package com.goodwy.dialer.dialogs

import android.annotation.SuppressLint
import android.telecom.PhoneAccountHandle
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.databinding.RadioButtonIconBinding
import com.goodwy.commons.extensions.*
import com.goodwy.dialer.R
import com.goodwy.dialer.databinding.DialogSelectSimBinding
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getAvailableSIMCardLabels

@SuppressLint("MissingPermission", "SetTextI18n")
class SelectSIMDialog(
    val activity: BaseSimpleActivity,
    val phoneNumber: String,
    onDismiss: () -> Unit = {},
    val callback: (handle: PhoneAccountHandle?, label: String?) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogSelectSimBinding::inflate)

    init {
        val isManageSpeedDial = phoneNumber == ""
        binding.selectSimLabel.beGoneIf(isManageSpeedDial)
        binding.divider.beGoneIf(isManageSpeedDial)
        binding.selectSimRememberHolder.beGoneIf(isManageSpeedDial)
        binding.selectSimRememberHolder.setOnClickListener {
            binding.selectSimRemember.toggle()
        }

        activity.getAvailableSIMCardLabels().forEachIndexed { index, SIMAccount ->
            val radioButton = RadioButtonIconBinding.inflate(activity.layoutInflater, null, false).apply {
                val indexText = index + 1
                dialogRadioButton.apply {
                    text = "$indexText - ${SIMAccount.label}"
                    id = index
                    setOnClickListener { selectedSIM(SIMAccount.handle, SIMAccount.label) }
                }
                dialogRadioButtonIcon.apply {
                    val simColor = if (indexText in 1..4) activity.config.simIconsColors[indexText] else activity.config.simIconsColors[0]
                    val res = when (indexText) {
                        1 -> R.drawable.ic_sim_one
                        2 -> R.drawable.ic_sim_two
                        else -> R.drawable.ic_sim_vector
                    }
                    val drawable = ResourcesCompat.getDrawable(resources, res, activity.theme)?.apply {
                        applyColorFilter(simColor)
                    }
                    setImageDrawable(drawable)
                }
            }
            binding.selectSimRadioGroup.addView(radioButton.root, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        activity.getAlertDialogBuilder()
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

    private fun selectedSIM(handle: PhoneAccountHandle, label: String) {
        if (binding.selectSimRemember.isChecked) {
            activity.config.saveCustomSIM(phoneNumber, handle)
        }

        callback(handle, label)
        dialog?.dismiss()
    }
}
