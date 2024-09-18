package com.goodwy.dialer.dialogs

import android.telecom.PhoneAccountHandle
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.*
import com.goodwy.dialer.R
import com.goodwy.dialer.databinding.DialogSelectSimButtonBinding
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getAvailableSIMCardLabels

class SelectSimButtonDialog(
    val activity: BaseSimpleActivity,
    val phoneNumber: String,
    onDismiss: () -> Unit = {},
    val callback: (handle: PhoneAccountHandle?, label: String?) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogSelectSimButtonBinding::inflate)

    init {

        val textColor = activity.baseConfig.simIconsColors[1].getContrastColor()

        activity.getAvailableSIMCardLabels().forEachIndexed { index, SIMAccount ->
            val indexText = index + 1
            if (indexText == 1) {
                binding.sim1Button.apply {
                    val drawable = resources.getColoredDrawableWithColor(activity, R.drawable.button_gray_bg, activity.baseConfig.simIconsColors[1])
                    background = drawable
                    setPadding(2, 2, 2, 2)
                    setTextColor(textColor)
                    setOnClickListener { selectedSIM(SIMAccount.handle, SIMAccount.label) }
                }
            } else if (indexText == 2) {
                binding.sim2Button.apply {
                    val drawable = resources.getColoredDrawableWithColor(activity, R.drawable.button_gray_bg, activity.baseConfig.simIconsColors[2])
                    background = drawable
                    setPadding(2,2,2,2)
                    setTextColor(textColor)
                    setOnClickListener { selectedSIM(SIMAccount.handle, SIMAccount.label) }
                }
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                val title = activity.getString(R.string.select_sim)
                activity.setupDialogStuff(binding.root, this, titleText = title) { alertDialog ->
                    dialog = alertDialog
                }
            }

        dialog?.setOnDismissListener {
            onDismiss()
        }

        binding.cancelButton.apply {
            val drawable = resources.getColoredDrawableWithColor(activity, R.drawable.button_gray_bg, 0xFFEB5545.toInt())
            background = drawable
            setPadding(2,2,2,2)
            setTextColor(textColor)
            setOnClickListener {
                dialog?.dismiss()
            }
        }

        binding.selectSimRememberHolder.setOnClickListener {
            binding.selectSimRemember.toggle()
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
