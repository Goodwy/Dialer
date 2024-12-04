package com.goodwy.dialer.dialogs

import android.annotation.SuppressLint
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.content.DialogInterface.BUTTON_POSITIVE
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import com.goodwy.commons.extensions.*
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.databinding.DialogChangeTextBinding

@SuppressLint("SetTextI18n")
class ChangeTextDialog(
    val activity: SimpleActivity,
    val title: String = activity.getString(R.string.quick_answers),
    val currentText: String?,
    val maxLength: Int = 0,
    val showNeutralButton: Boolean = false,
    val neutralTextRes: Int = com.goodwy.commons.R.string.use_default,
    val callback: (newText: String) -> Unit) {

    init {
        val binding = DialogChangeTextBinding.inflate(activity.layoutInflater)
        val view = binding.root
        binding.text.apply {

            if (maxLength > 0) {
                binding.count.beVisible()
                val filterArray = arrayOfNulls<InputFilter>(1)
                filterArray[0] = LengthFilter(maxLength)
                filters = filterArray
                onTextChangeListener {
                    val length = it.length
                    binding.count.text = "$length/$maxLength"
                }
            }

            setText(currentText)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok, null)
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .setNeutralButton(neutralTextRes, null)
            .apply {
                activity.setupDialogStuff(view, this, titleText = title) { alertDialog ->
                    alertDialog.showKeyboard(binding.text)
                    alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                        val text = binding.text.value
                        alertDialog.dismiss()
                        callback(text)
                    }
                    alertDialog.getButton(BUTTON_NEUTRAL).beVisibleIf(showNeutralButton)
                    alertDialog.getButton(BUTTON_NEUTRAL).setOnClickListener {
                        val text = ""
                        alertDialog.dismiss()
                        callback(text)
                    }
                }
            }
    }
}
