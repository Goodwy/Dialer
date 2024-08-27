package com.goodwy.dialer.dialogs

import android.content.DialogInterface.BUTTON_POSITIVE
import com.goodwy.commons.extensions.*
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.databinding.DialogChangeTextBinding

class ChangeTextDialog(val activity: SimpleActivity, val currentText: String?, val callback: (newText: String) -> Unit) {

    init {
        val binding = DialogChangeTextBinding.inflate(activity.layoutInflater)
        val view = binding.root
        binding.text.setText(currentText)

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok, null)
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.quick_answers) { alertDialog ->
                    alertDialog.showKeyboard(binding.text)
                    alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                        val text = binding.text.value
                        alertDialog.dismiss()
                        callback(text)
                    }
                }
            }
    }
}
