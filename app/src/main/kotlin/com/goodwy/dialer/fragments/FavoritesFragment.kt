package com.goodwy.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MyGridLayoutManager
import com.goodwy.commons.views.MyLinearLayoutManager
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.activities.MainActivity
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.adapters.ContactsAdapter
import com.goodwy.dialer.databinding.FragmentFavoritesBinding
import com.goodwy.dialer.databinding.FragmentLettersLayoutBinding
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.setupWithContacts
import com.goodwy.dialer.extensions.startContactDetailsIntent
import com.goodwy.dialer.helpers.Converters
import com.goodwy.dialer.interfaces.RefreshItemsListener

class FavoritesFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LettersInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentLettersLayoutBinding
    private var allContacts = ArrayList<Contact>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentLettersLayoutBinding.bind(FragmentFavoritesBinding.bind(this).favoritesFragment)
        innerBinding = LettersInnerBinding(binding)
    }

    override fun setupFragment() {
        binding.root.setBackgroundColor(context.getProperBackgroundColor())
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        binding.fragmentPlaceholder.text = context.getString(placeholderResId)
        binding.fragmentPlaceholder2.beGone()
        binding.letterFastscrollerThumb.beGone()
        binding.letterFastscroller.beGone()
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.apply {
            fragmentPlaceholder.setTextColor(textColor)
            (fragmentList.adapter as? MyRecyclerViewAdapter)?.apply {
                updateTextColor(textColor)
                updatePrimaryColor()
                updateBackgroundColor(context.getProperBackgroundColor())
            }

            letterFastscroller.textColor = textColor.getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        ContactsHelper(context).getContacts { contacts ->
            allContacts = contacts

            if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                val privateCursor = context?.getMyContactsCursor(favoritesOnly = true, withPhoneNumbersOnly = true)
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor).map {
                    it.copy(starred = 1)
                }
                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }
            }
            val favorites = contacts.filter { it.starred == 1 } as ArrayList<Contact>

            allContacts = if (activity!!.config.isCustomOrderSelected) {
                sortByCustomOrder(favorites)
            } else {
                favorites
            }

            activity?.runOnUiThread {
                gotContacts(allContacts)
                callback?.invoke()
            }
        }
    }

    private fun gotContacts(contacts: ArrayList<Contact>) {
        setupLetterFastScroller(contacts)
        binding.apply {
            if (contacts.isEmpty()) {
                fragmentPlaceholder.beVisible()
                fragmentList.beGone()
            } else {
                fragmentPlaceholder.beGone()
                fragmentList.beVisible()

                fragmentList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        activity?.hideKeyboard()
                    }
                })

                updateListAdapter()
            }
        }
    }

    private fun updateListAdapter() {
        val viewType = context.config.viewType
        setViewType(viewType, allContacts.size)

        val currAdapter = binding.fragmentList.adapter as ContactsAdapter?
        if (currAdapter == null) {
            ContactsAdapter(
                activity = activity as SimpleActivity,
                contacts = allContacts,
                recyclerView = binding.fragmentList,
                refreshItemsListener = this,
                viewType = viewType,
                showDeleteButton = false,
                enableDrag = true,
                showNumber = context.baseConfig.showPhoneNumbers,
                itemClick = { it ->
                    if (context.config.showCallConfirmation) {
                        CallConfirmationDialog(activity as SimpleActivity, (it as Contact).getNameToDisplay()) {
                            activity?.apply {
                                initiateCall(it) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
                            }
                        }
                    } else {
                        activity?.apply {
                            initiateCall(it as Contact) { launchCallIntent(it, key = BuildConfig.RIGHT_APP_KEY) }
                        }
                    }
                },
                profileIconClick = {
                    activity?.startContactDetailsIntent(it as Contact)
                }).apply {
                binding.fragmentList.adapter = this

                onDragEndListener = {
                    val adapter = binding.fragmentList.adapter
                    if (adapter is ContactsAdapter) {
                        val items = adapter.contacts
                        saveCustomOrderToPrefs(items)
                        setupLetterFastScroller(items)
                        (activity as MainActivity).cacheFavorites(items)
                    }
                }

                onSpanCountListener = { newSpanCount ->
                    context.config.contactsGridColumnCount = newSpanCount
                }
            }

            if (context.areSystemAnimationsEnabled) {
                binding.fragmentList.scheduleLayoutAnimation()
            }
            (activity as MainActivity).cacheFavorites(allContacts)
        } else {
            currAdapter.viewType = viewType
            currAdapter.updateItems(allContacts)
            (activity as MainActivity).cacheFavorites(allContacts)
        }
    }

    fun columnCountChanged() {
        if (binding.fragmentList.layoutManager is MyGridLayoutManager) (binding.fragmentList.layoutManager as MyGridLayoutManager).spanCount = context!!.config.contactsGridColumnCount
        binding.fragmentList.adapter?.apply {
            notifyItemRangeChanged(0, allContacts.size)
        }
    }

    private fun sortByCustomOrder(favorites: List<Contact>): ArrayList<Contact> {
        val favoritesOrder = activity!!.config.favoritesContactsOrder

        if (favoritesOrder.isEmpty()) {
            return ArrayList(favorites)
        }

        val orderList = Converters().jsonToStringList(favoritesOrder)
        val map = orderList.withIndex().associate { it.value to it.index }
        val sorted = favorites.sortedBy { map[it.contactId.toString()] }

        return ArrayList(sorted)
    }

    private fun saveCustomOrderToPrefs(items: List<Contact>) {
        activity?.apply {
            val orderIds = items.map { it.contactId }
            val orderGsonString = Gson().toJson(orderIds)
            config.favoritesContactsOrder = orderGsonString
        }
    }

    private fun setupLetterFastScroller(contacts: List<Contact>) {
        binding.letterFastscroller.setupWithContacts(binding.fragmentList, contacts)
    }

    override fun onSearchClosed() {
        binding.fragmentPlaceholder.beVisibleIf(allContacts.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(allContacts)
        setupLetterFastScroller(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
        val fixedText = text.trim().replace("\\s+".toRegex(), " ")
        val shouldNormalize = fixedText.normalizeString() == fixedText
        val contacts = allContacts.filter {
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
        }.sortedByDescending {
            it.name.startsWith(fixedText, true)
        }.toMutableList() as ArrayList<Contact>

        binding.fragmentPlaceholder.beVisibleIf(contacts.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(contacts, fixedText)
        setupLetterFastScroller(contacts)
    }

    private fun setViewType(viewType: Int, size: Int = 0) {
        val spanCount = context.config.contactsGridColumnCount

        val layoutManager = if (viewType == VIEW_TYPE_GRID) {
            binding.letterFastscroller.beGone()
            MyGridLayoutManager(context, spanCount)
        } else {
            binding.letterFastscroller.beGone()
//            binding.letterFastscroller.beVisibleIf(size > 10)
//            if (size > 50) binding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleTiny
//            else if (size > 30) binding.letterFastscroller.textAppearanceRes = R.style.DialpadLetterStyleSmall
            MyLinearLayoutManager(context)
        }
        binding.fragmentList.layoutManager = layoutManager
    }

    override fun myRecyclerView() = binding.fragmentList
}
