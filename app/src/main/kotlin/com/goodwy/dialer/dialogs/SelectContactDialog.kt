package com.goodwy.dialer.dialogs

import android.content.res.Configuration
import android.graphics.Color
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.getProperText
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MySearchMenu
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.adapters.ContactsAdapter
import com.goodwy.dialer.databinding.DialogSelectContactBinding
import com.goodwy.dialer.extensions.setupWithContacts

class SelectContactDialog(val activity: SimpleActivity, val contacts: List<Contact>, val callback: (selectedContact: Contact) -> Unit) {
    private val binding by activity.viewBinding(DialogSelectContactBinding::inflate)

    private var dialog: AlertDialog? = null

    init {
        binding.apply {
            letterFastscroller.textColor = activity.getProperTextColor().getColorStateList()
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = activity.getProperPrimaryColor().getContrastColor()
            letterFastscrollerThumb.thumbColor = activity.getProperPrimaryColor().getColorStateList()

            setupLetterFastScroller(contacts)
            configureSearchView()

            selectContactList.adapter = ContactsAdapter(
                activity = activity,
                contacts = contacts.toMutableList(),
                recyclerView = selectContactList,
                showIcon = false,
                allowLongClick = false,
                itemClick = {
                    callback(it as Contact)
                    dialog?.dismiss()
                }
            )
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(R.string.cancel, null)
            .setOnKeyListener { _, i, keyEvent ->
                if (keyEvent.action == KeyEvent.ACTION_UP && i == KeyEvent.KEYCODE_BACK) {
                    backPressed()
                }
                true
            }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.choose_contact) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun setupLetterFastScroller(contacts: List<Contact>) {
        try {
            //Decrease the font size based on the number of letters in the letter scroller
            val all = contacts.map { it.getNameToDisplay().substring(0, 1) }
            val unique: Set<String> = HashSet(all)
            val sizeUnique = unique.size
            if (isHighScreenSize()) {
                if (sizeUnique > 48) binding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleTooTiny
                else if (sizeUnique > 37) binding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleTiny
                else binding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleSmall
            } else {
                if (sizeUnique > 36) binding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleTooTiny
                else if (sizeUnique > 30) binding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleTiny
                else binding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleSmall
            }
        } catch (_: Exception) { }

        binding.letterFastscroller.setupWithContacts(binding.selectContactList, contacts)
    }

    private fun isHighScreenSize(): Boolean {
        return when (activity.resources.configuration.screenLayout
            and Configuration.SCREENLAYOUT_LONG_MASK) {
            Configuration.SCREENLAYOUT_LONG_NO -> false
            else -> true
        }
    }

    private fun configureSearchView() = with(binding.contactSearchView) {
        updateHintText(context.getString(R.string.search_contacts))
        binding.topToolbarSearch.imeOptions = EditorInfo.IME_ACTION_DONE

        toggleHideOnScroll(true)
        setupMenu()
        setSearchViewListeners()
        updateSearchViewUi()
    }

    private fun MySearchMenu.updateSearchViewUi() {
        getToolbar().beInvisible()
        updateColors()
        setBackgroundColor(Color.TRANSPARENT)
        binding.topAppBarLayout.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun MySearchMenu.setSearchViewListeners() {
        onSearchOpenListener = {
            //updateSearchViewLeftIcon(R.drawable.ic_cross_vector)
        }
        onSearchClosedListener = {
            binding.topToolbarSearch.clearFocus()
            activity.hideKeyboard(binding.topToolbarSearch)
            //updateSearchViewLeftIcon(R.drawable.ic_search_vector)
        }

        onSearchTextChangedListener = { text ->
            filterContactListBySearchQuery(text)
            clearSearch()
        }
    }

//    private fun updateSearchViewLeftIcon(iconResId: Int) = with(binding.root.findViewById<ImageView>(R.id.top_toolbar_search_icon)) {
//        post {
//            setImageResource(iconResId)
//        }
//    }

    private fun filterContactListBySearchQuery(query: String) {
        val adapter = binding.selectContactList.adapter as? ContactsAdapter
        var contactsToShow = contacts
        val fixedText = query.trim().replace("\\s+".toRegex(), " ")
        val shouldNormalize = fixedText.normalizeString() == fixedText
        if (query.isNotEmpty()) {
            contactsToShow = contacts.filter {
                getProperText(it.getNameToDisplay(), shouldNormalize).contains(fixedText, true) ||
                    getProperText(it.nickname, shouldNormalize).contains(fixedText, true) ||
                    (fixedText.toIntOrNull() != null && it.phoneNumbers.any {
                        fixedText.normalizePhoneNumber().isNotEmpty() && it.normalizedNumber.contains(fixedText.normalizePhoneNumber(), true)
                    }) ||
                    it.emails.any { it.value.contains(fixedText, true) } ||
                    it.addresses.any { getProperText(it.value, shouldNormalize).contains(fixedText, true) } ||
                    it.IMs.any { it.value.contains(fixedText, true) } ||
                    getProperText(it.notes, shouldNormalize).contains(fixedText, true) ||
                    getProperText(it.organization.company, shouldNormalize).contains(fixedText, true) ||
                    getProperText(it.organization.jobPosition, shouldNormalize).contains(fixedText, true) ||
                    it.websites.any { it.contains(fixedText, true) }
            }
        }
        checkPlaceholderVisibility(contactsToShow)

        if (adapter?.contacts != contactsToShow) {
            adapter?.updateItems(contactsToShow)
            setupLetterFastScroller(contactsToShow)

            binding.selectContactList.apply {
                post {
                    scrollToPosition(0)
                }
            }
        }
    }

    private fun checkPlaceholderVisibility(contacts: List<Contact>) = with(binding) {
        contactsEmptyPlaceholder.beVisibleIf(contacts.isEmpty())

        if (contactSearchView.isSearchOpen) {
            contactsEmptyPlaceholder.text = activity.getString(R.string.no_items_found)
        }

        letterFastscroller.beVisibleIf(contactsEmptyPlaceholder.isGone())
        letterFastscrollerThumb.beVisibleIf(contactsEmptyPlaceholder.isGone())
    }

    private fun backPressed() {
        if (binding.contactSearchView.isSearchOpen) {
            binding.contactSearchView.closeSearch()
        } else {
            dialog?.dismiss()
        }
    }
}
