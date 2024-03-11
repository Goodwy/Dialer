package com.goodwy.dialer.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.beGoneIf
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.commons.helpers.*
import com.goodwy.dialer.R
import com.goodwy.dialer.databinding.DialogChangeSortingBinding
import com.goodwy.dialer.extensions.config

class ChangeSortingDialog(val activity: BaseSimpleActivity, private val showCustomSorting: Boolean = false, private val callback: () -> Unit) {
    private val binding by activity.viewBinding(DialogChangeSortingBinding::inflate)

    private var currSorting = 0
    private var config = activity.config

    init {
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.sort_by)
            }

        currSorting = if (showCustomSorting && config.isCustomOrderSelected) {
            SORT_BY_CUSTOM
        } else {
            config.sorting
        }

        setupSortRadio()
        setupOrderRadio()
        setupSymbolsFirst()
    }

    private fun setupSortRadio() {
        binding.apply {
            sortingDialogRadioSorting.setOnCheckedChangeListener { group, checkedId ->
                val isCustomSorting = checkedId == sortingDialogRadioCustom.id
                sortingDialogRadioOrder.beGoneIf(isCustomSorting)
                sortingDialogSymbolsFirstCheckbox.beGoneIf(isCustomSorting)
                divider.beGoneIf(isCustomSorting)
            }

            val sortBtn = when {
                currSorting and SORT_BY_FIRST_NAME != 0 -> sortingDialogRadioFirstName
                currSorting and SORT_BY_MIDDLE_NAME != 0 -> sortingDialogRadioMiddleName
                currSorting and SORT_BY_SURNAME != 0 -> sortingDialogRadioSurname
                currSorting and SORT_BY_FULL_NAME != 0 -> sortingDialogRadioFullName
                currSorting and SORT_BY_CUSTOM != 0 -> sortingDialogRadioCustom
                else -> sortingDialogRadioDateCreated
            }
            sortBtn.isChecked = true

            if (showCustomSorting) {
                sortingDialogRadioCustom.isChecked = config.isCustomOrderSelected
            }
            sortingDialogRadioCustom.beGoneIf(!showCustomSorting)
        }
    }

    private fun setupOrderRadio() {
        var orderBtn = binding.sortingDialogRadioAscending

        if (currSorting and SORT_DESCENDING != 0) {
            orderBtn = binding.sortingDialogRadioDescending
        }
        orderBtn.isChecked = true
    }

    private fun setupSymbolsFirst() {
        binding.sortingDialogSymbolsFirstCheckbox.isChecked = config.sortingSymbolsFirst
    }

    private fun dialogConfirmed() {
        var sorting = when (binding.sortingDialogRadioSorting.checkedRadioButtonId) {
            R.id.sorting_dialog_radio_first_name -> SORT_BY_FIRST_NAME
            R.id.sorting_dialog_radio_middle_name -> SORT_BY_MIDDLE_NAME
            R.id.sorting_dialog_radio_surname -> SORT_BY_SURNAME
            R.id.sorting_dialog_radio_full_name -> SORT_BY_FULL_NAME
            R.id.sorting_dialog_radio_custom -> SORT_BY_CUSTOM
            else -> SORT_BY_DATE_CREATED
        }

        if (sorting != SORT_BY_CUSTOM && binding.sortingDialogRadioOrder.checkedRadioButtonId == R.id.sorting_dialog_radio_descending) {
            sorting = sorting or SORT_DESCENDING
        }

        if (showCustomSorting) {
            if (sorting == SORT_BY_CUSTOM) {
                config.isCustomOrderSelected = true
            } else {
                config.isCustomOrderSelected = false
                config.sorting = sorting
            }
        } else {
            config.sorting = sorting
        }

        config.sortingSymbolsFirst = binding.sortingDialogSymbolsFirstCheckbox.isChecked

        callback()
    }
}
