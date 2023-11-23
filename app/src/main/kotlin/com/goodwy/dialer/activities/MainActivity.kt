package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.core.view.updateLayoutParams
import androidx.viewpager.widget.ViewPager
import com.behaviorule.arturdumchev.library.pixels
import com.google.android.material.snackbar.Snackbar
import com.goodwy.commons.dialogs.ChangeViewTypeDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.PermissionRequiredDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ViewPagerAdapter
import com.goodwy.dialer.databinding.ActivityMainBinding
import com.goodwy.dialer.dialogs.ChangeSortingDialog
import com.goodwy.dialer.dialogs.FilterContactSourcesDialog
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.launchCreateNewContactIntent
import com.goodwy.dialer.extensions.updateUnreadCountBadge
import com.goodwy.dialer.fragments.ContactsFragment
import com.goodwy.dialer.fragments.FavoritesFragment
import com.goodwy.dialer.fragments.MyViewPagerFragment
import com.goodwy.dialer.fragments.RecentsFragment
import com.goodwy.dialer.helpers.*
import me.grantland.widget.AutofitHelper

class MainActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityMainBinding::inflate)

    private var launchedDialer = false
    private var isSearchOpen = false
    private var mSearchMenuItem: MenuItem? = null
    private var storedShowTabs = 0
    private var storedFontSize = 0
    private var searchQuery = ""
    private var storedStartNameWithSurname = false
    private var storedShowPhoneNumbers = false
    var cachedContacts = ArrayList<Contact>()

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        updateNavigationBarColor = false
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        config.tabsChanged = false
        val useBottomNavigationBar = config.bottomNavigationBar
        updateMaterialActivityViews(binding.mainCoordinator, binding.mainHolder, useTransparentNavigation = false, useTopSearchMenu = useBottomNavigationBar)
        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false
        val properBackgroundColor = getProperBackgroundColor()

        if (isDefaultDialer()) {
            checkContactPermissions()

            if (!config.wasOverlaySnackbarConfirmed && !Settings.canDrawOverlays(this)) {
                val snackbar = Snackbar.make(binding.mainHolder, R.string.allow_displaying_over_other_apps, Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok) {
                    config.wasOverlaySnackbarConfirmed = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }

                snackbar.setBackgroundTint(properBackgroundColor.darkenColor())
                val properTextColor = getProperTextColor()
                snackbar.setTextColor(properTextColor)
                snackbar.setActionTextColor(properTextColor)
                val snackBarView: View = snackbar.view
                snackBarView.translationY = -pixels(R.dimen.snackbar_bottom_margin)
                snackbar.show()
            }

            handleNotificationPermission { granted ->
                if (!granted) {
                    PermissionRequiredDialog(this, R.string.allow_notifications_incoming_calls, { openNotificationSettings() })
                }
            }
        } else {
            launchSetDefaultDialerIntent()
        }

        if (isQPlus() && (baseConfig.blockUnknownNumbers || baseConfig.blockHiddenNumbers)) {
            setDefaultCallerIdApp()
        }

        binding.mainMenu.apply {
            updateTitle(getAppLauncherName())
            searchBeVisibleIf(useBottomNavigationBar)
        }

        // TODO TRANSPARENT Navigation Bar
        if (!useBottomNavigationBar) {
            setWindowTransparency(true) { _, bottomNavigationBarSize, leftNavigationBarSize, rightNavigationBarSize ->
                binding.mainCoordinator.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
                binding.mainDialpadButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    setMargins(0, 0, 0, bottomNavigationBarSize + pixels(R.dimen.activity_margin).toInt())
                }
            }

            setupTopTabs()
        } else {
            setupTabs()
        }
        Contact.sorting = config.sorting

        binding.mainTopTabsContainer.beGoneIf(binding.mainTopTabsHolder.tabCount == 1 || useBottomNavigationBar)

        val marginTop = if (useBottomNavigationBar) actionBarSize + pixels(R.dimen.top_toolbar_search_height).toInt() else actionBarSize
        binding.mainHolder.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            setMargins(0, marginTop, 0, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        if (storedShowTabs != config.showTabs || config.tabsChanged || storedShowPhoneNumbers != config.showPhoneNumbers) {
            config.lastUsedViewPagerPage = 0
//            finish()
//            startActivity(intent)
            System.exit(0)
            return
        }

        updateMenuColors()
        //refreshMenuItems()
        val properTextColor = getProperTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(this, R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.setImageDrawable(dialpadIcon)

        updateTextColors(binding.mainHolder)
        setupTabColors()
        setupToolbar(binding.mainToolbar, searchMenuItem = mSearchMenuItem)

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            getContactsFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            getFavoritesFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            storedStartNameWithSurname = config.startNameWithSurname
        }

        if (!isSearchOpen && !binding.mainMenu.isSearchOpen) {
            refreshItems(true)
        }

        if (binding.viewPager.adapter != null && !config.bottomNavigationBar) {

            if (config.tabsChanged) {
                if (config.useIconTabs) {
                    binding.mainTopTabsHolder.getTabAt(0)?.text = null
                    binding.mainTopTabsHolder.getTabAt(1)?.text = null
                    binding.mainTopTabsHolder.getTabAt(2)?.text = null
                } else {
                    binding.mainTopTabsHolder.getTabAt(0)?.icon = null
                    binding.mainTopTabsHolder.getTabAt(1)?.icon = null
                    binding.mainTopTabsHolder.getTabAt(2)?.icon = null
                }
            }

            getInactiveTabIndexes(binding.viewPager.currentItem).forEach {
                binding.mainTopTabsHolder.getTabAt(it)?.icon?.applyColorFilter(properTextColor)
                binding.mainTopTabsHolder.getTabAt(it)?.icon?.alpha = 220 // max 255
                binding.mainTopTabsHolder.setTabTextColors(properTextColor, properPrimaryColor)
            }

            binding.mainTopTabsHolder.getTabAt(binding.viewPager.currentItem)?.icon?.applyColorFilter(properPrimaryColor)
            binding.mainTopTabsHolder.getTabAt(binding.viewPager.currentItem)?.icon?.alpha = 220 // max 255
            getAllFragments().forEach {
                it?.setupColors(properTextColor, properPrimaryColor, properPrimaryColor)
                binding.mainTopTabsHolder.setTabTextColors(properTextColor, properPrimaryColor)
            }
        } else if (binding.viewPager.adapter != null && config.bottomNavigationBar) {
            getAllFragments().forEach {
                it?.setupColors(properTextColor, properPrimaryColor, properPrimaryColor)
            }
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        checkShortcuts()
        Handler().postDelayed({
            getRecentsFragment()?.refreshItems()
        }, 2000)
        invalidateOptionsMenu()

        //Screen slide animation
        val animation = when (config.screenSlideAnimation) {
            1 -> ZoomOutPageTransformer()
            2 -> DepthPageTransformer()
            else -> null
        }
        binding.viewPager.setPageTransformer(true, animation)

        getAllFragments().forEach {
            it?.setBackgroundColor(getProperBackgroundColor())
        }
        val properBackgroundColor = getProperBackgroundColor()
        getFavoritesFragment()?.setBackgroundColor(properBackgroundColor)
        getRecentsFragment()?.setBackgroundColor(properBackgroundColor)
        getContactsFragment()?.setBackgroundColor(properBackgroundColor)
    }

    @SuppressLint("MissingSuperCall")
    override fun onDestroy() {
        super.onDestroy()
        storedShowTabs = config.showTabs
        config.tabsChanged = false
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onPause() {
        super.onPause()
        storedShowTabs = config.showTabs
        config.tabsChanged = false
        storedStartNameWithSurname = config.startNameWithSurname
        storedShowPhoneNumbers = config.showPhoneNumbers
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we don't really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
        } else if (requestCode == REQUEST_CODE_SET_DEFAULT_CALLER_ID && resultCode != Activity.RESULT_OK) {
            toast(R.string.must_make_default_caller_id_app, length = Toast.LENGTH_LONG)
            baseConfig.blockUnknownNumbers = false
            baseConfig.blockHiddenNumbers = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, launchedDialer)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    override fun onBackPressed() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else if (isSearchOpen && mSearchMenuItem != null) {
            mSearchMenuItem!!.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        val getRecentsFragment = getRecentsFragment()
        val getFavoritesFragment = getFavoritesFragment()
        binding.mainMenu.getToolbar().menu.apply {
            findItem(R.id.search).isVisible = !config.bottomNavigationBar
            findItem(R.id.clear_call_history).isVisible = currentFragment == getRecentsFragment
            findItem(R.id.sort).isVisible = currentFragment != getRecentsFragment
            findItem(R.id.create_new_contact).isVisible = currentFragment == getContactsFragment()
            findItem(R.id.change_view_type).isVisible = currentFragment == getFavoritesFragment
            findItem(R.id.column_count).isVisible = currentFragment == getFavoritesFragment && config.viewType == VIEW_TYPE_GRID
            findItem(R.id.show_blocked_numbers).isVisible = currentFragment == getRecentsFragment
            findItem(R.id.show_blocked_numbers).title = if (config.showBlockedNumbers) getString(R.string.hide_blocked_numbers) else getString(R.string.show_blocked_numbers)
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            getToolbar().inflateMenu(R.menu.menu)
            toggleHideOnScroll(false)
            if (config.bottomNavigationBar) {
                setupMenu()

                onSearchClosedListener = {
                    getAllFragments().forEach {
                        it?.onSearchQueryChanged("")
                    }
                }

                onSearchTextChangedListener = { text ->
                    getCurrentFragment()?.onSearchQueryChanged(text)
                    clearSearch()
                }
            } else setupSearch(getToolbar().menu)

            getToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.show_blocked_numbers -> showBlockedNumbers()
                    R.id.clear_call_history -> clearCallHistory()
                    R.id.create_new_contact -> launchCreateNewContactIntent()
                    R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                    R.id.filter -> showFilterDialog()
                    R.id.settings -> launchSettings()
                    R.id.about -> launchAbout()
                    R.id.change_view_type -> changeViewType()
                    R.id.column_count -> changeColumnCount()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        RadioGroupDialog(this, ArrayList(items), currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                getFavoritesFragment()?.columnCountChanged()
            }
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this) {
            refreshMenuItems()
            getFavoritesFragment()?.refreshItems()
        }
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
        binding.mainMenu.updateColors()
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            initFragments()
        }
    }

    private fun setupSearch(menu: Menu) {
        updateMenuItemColors(menu)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        (mSearchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(R.string.search)
            //setBackgroundColor(getProperTextColor())
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        searchQuery = newText
                        getCurrentFragment()?.onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                isSearchOpen = true
                binding.mainDialpadButton.beGone()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchClosed()
                }

                isSearchOpen = false
                binding.mainDialpadButton.beVisible()
                return true
            }
        })
    }

    private fun showBlockedNumbers() {
        config.showBlockedNumbers = !config.showBlockedNumbers
        binding.mainMenu.getToolbar().menu.findItem(R.id.show_blocked_numbers).title = if (config.showBlockedNumbers) getString(R.string.hide_blocked_numbers) else getString(R.string.show_blocked_numbers)

        runOnUiThread {
            getRecentsFragment()?.refreshItems()
        }
    }

    private fun clearCallHistory() {
        val confirmationText = "${getString(R.string.clear_history_confirmation)}\n\n${getString(R.string.cannot_be_undone)}"
        ConfirmationDialog(this, confirmationText) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    getRecentsFragment()?.refreshItems()
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = resources.getDrawable(R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun setupTabColors() {
        val properPrimaryColor = getProperPrimaryColor()
        // bottom tab bar
        if (config.bottomNavigationBar) {
            val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
            updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[binding.viewPager.currentItem])

            getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
                val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
                updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
            }

            val bottomBarColor = getBottomNavigationBackgroundColor()
            binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
            if (binding.mainTabsHolder.tabCount != 1) updateNavigationBarColor(bottomBarColor)
            else {
                // TODO TRANSPARENT Navigation Bar
                setWindowTransparency(true) { _, bottomNavigationBarSize, leftNavigationBarSize, rightNavigationBarSize ->
                    binding.mainCoordinator.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
                    binding.mainDialpadButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        setMargins(0, 0, 0, bottomNavigationBarSize + pixels(R.dimen.activity_margin).toInt())
                    }
                }
            }
        } else {
            // top tab bar
            val lastUsedPage = getDefaultTab()
            val properTextColor = getProperTextColor()
            binding.mainTopTabsHolder.apply {
                //background = ColorDrawable(getProperBackgroundColor())
                setSelectedTabIndicatorColor(getProperBackgroundColor())
                getTabAt(lastUsedPage)?.select()
                getTabAt(lastUsedPage)?.icon?.applyColorFilter(properPrimaryColor)
                getTabAt(lastUsedPage)?.icon?.alpha = 220 // max 255

                getInactiveTabIndexes(lastUsedPage).forEach {
                    getTabAt(it)?.icon?.applyColorFilter(properTextColor)
                    getTabAt(it)?.icon?.alpha = 220 // max 255
                }
            }
        }
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds(): List<Int> {
        val showTabs = config.showTabs
        val icons = mutableListOf<Int>()

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_filled_vector)
        }

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_rounded)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_filled_vector)
        }

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_rounded)
        }

        return icons
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                if (config.bottomNavigationBar) binding.mainTabsHolder.getTabAt(position)?.select() else binding.mainTopTabsHolder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
                if (getCurrentFragment() == getRecentsFragment()) {
                    ensureBackgroundThread {
                        clearMissedCalls()
                    }
                }
            }
        })

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        if (config.bottomNavigationBar) {
            binding.mainTabsHolder.onGlobalLayout {
                Handler().postDelayed({
                    var wantedTab = getDefaultTab()

                    // open the Recents tab if we got here by clicking a missed call notification
                    if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                        wantedTab = binding.mainTabsHolder.tabCount - 2
                        ensureBackgroundThread {
                            clearMissedCalls()
                        }
                    }

                    binding.mainTabsHolder.getTabAt(wantedTab)?.select()
                    refreshMenuItems()
                }, 100L)
            }
        } else {
            binding.mainTopTabsHolder.onGlobalLayout {
                Handler().postDelayed({
                    var wantedTab = getDefaultTab()

                    // open the Recents tab if we got here by clicking a missed call notification
                    if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                        wantedTab = binding.mainTopTabsHolder.tabCount - 2
                        ensureBackgroundThread {
                            clearMissedCalls()
                        }
                    }

                    binding.mainTopTabsHolder.getTabAt(wantedTab)?.select()
                    refreshMenuItems()
                }, 100L)
            }
        }

        binding.mainDialpadButton.setOnClickListener {
            launchDialpad()
        }

        binding.viewPager.onGlobalLayout {
            refreshMenuItems()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun setupTopTabs() {
        // top tab bar
        binding.mainTabsHolder.beGone()
        val selectedTabIndex = binding.mainTopTabsHolder.selectedTabPosition
        binding.viewPager.adapter = null
        binding.mainTopTabsHolder.removeAllTabs()
        var skippedTabs = 0
        var isAnySelected = false

        val properTextColor = getProperTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value == 0) {
                skippedTabs++
            } else {
                val tab = if (config.useIconTabs) binding.mainTopTabsHolder.newTab().setIcon(getTabIcon(index)) else binding.mainTopTabsHolder.newTab().setText(getTabLabel(index))
                tab.contentDescription = getTabContentDescription(index)
                val wasAlreadySelected = selectedTabIndex > -1 && selectedTabIndex == index - skippedTabs
                val shouldSelect = !isAnySelected && wasAlreadySelected
                if (shouldSelect) {
                    isAnySelected = true
                }
                binding.mainTopTabsHolder.addTab(tab, index - skippedTabs, shouldSelect)
                binding.mainTopTabsHolder.setTabTextColors(properTextColor,
                    properPrimaryColor)
            }
        }

        binding.mainTopTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                it.icon?.applyColorFilter(properTextColor)
                it.icon?.alpha = 220 // max 255
                getFavoritesFragment()?.refreshItems() //to save sorting
            },
            tabSelectedAction = {
                closeSearch()
                binding.viewPager.currentItem = it.position
                it.icon?.applyColorFilter(properPrimaryColor)
                it.icon?.alpha = 220 // max 255
                if (config.openSearch) {
                    if (getCurrentFragment() is ContactsFragment) {
                        mSearchMenuItem!!.expandActionView()
                    }
                }
            }
        )
        if (!isAnySelected) {
            binding.mainTopTabsHolder.selectTab(binding.mainTopTabsHolder.getTabAt(getDefaultTab()))
        }
        storedShowTabs = config.showTabs
        config.tabsChanged = false
    }

    private fun setupTabs() {
        // bottom tab bar
        binding.mainTopTabsHolder.beGone()
        binding.viewPager.adapter = null
        binding.mainTabsHolder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIcon(index))
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.apply {
                        text = getTabLabel(index)
                        beGoneIf(config.useIconTabs)
                    }
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    binding.mainTabsHolder.addTab(this)
                }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position])
            },
            tabSelectedAction = {
                binding.mainMenu.closeSearch()
                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])
                if (config.openSearch) {
                    if (getCurrentFragment() is ContactsFragment) {
                        binding.mainMenu.requestFocusAndShowKeyboard()
                    }
                }
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
        storedShowPhoneNumbers = config.showPhoneNumbers
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.favorites_tab
            1 -> R.string.recents
            else -> R.string.contacts_tab
        }

        return resources.getString(stringId)
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_star_vector
            1 -> R.drawable.ic_clock_filled_vector
            else -> R.drawable.ic_person_rounded
        }
        return resources.getColoredDrawableWithColor(this@MainActivity, drawableId, getProperTextColor())!!
    }

    private fun getTabContentDescription(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.favorites_tab
            1 -> R.string.call_history_tab
            else -> R.string.contacts_tab
        }

        return resources.getString(stringId)
    }

    private fun refreshItems(openLastTab: Boolean = false) {
        if (isDestroyed || isFinishing) {
            return
        }

        binding.apply {
            if (viewPager.adapter == null) {
                viewPager.adapter = ViewPagerAdapter(this@MainActivity)
                viewPager.currentItem = if (openLastTab) config.lastUsedViewPagerPage else getDefaultTab()
                viewPager.onGlobalLayout {
                    refreshFragments()
                }
            } else {
                refreshFragments()
            }
        }
    }

    private fun launchDialpad() {
        Intent(applicationContext, DialpadActivity::class.java).apply {
            startActivity(this)
        }
    }

    fun refreshFragments() {
        getContactsFragment()?.refreshItems()
        getFavoritesFragment()?.refreshItems()
        getRecentsFragment()?.refreshItems()
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>?>()

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(getFavoritesFragment())
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(getRecentsFragment())
        }

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(getContactsFragment())
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? = getAllFragments().getOrNull(binding.viewPager.currentItem)

    private fun getContactsFragment(): ContactsFragment? = findViewById(R.id.contacts_fragment)

    private fun getFavoritesFragment(): FavoritesFragment? = findViewById(R.id.favorites_fragment)

    private fun getRecentsFragment(): RecentsFragment? = findViewById(R.id.recents_fragment)

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        val mainTabsHolder = if (config.bottomNavigationBar) binding.mainTabsHolder else binding.mainTopTabsHolder
        return when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < mainTabsHolder.tabCount) config.lastUsedViewPagerPage else 0
            TAB_FAVORITES -> 0
            TAB_CALL_HISTORY -> if (showTabsMask and TAB_FAVORITES > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_CONTACTS > 0) {
                    if (showTabsMask and TAB_FAVORITES > 0) {
                        if (showTabsMask and TAB_CALL_HISTORY > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_CALL_HISTORY > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun clearMissedCalls() {
        try {
            // notification cancellation triggers MissedCallNotifier.clearMissedCalls() which, in turn,
            // should update the database and reset the cached missed call count in MissedCallNotifier.java
            // https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#170
            telecomManager.cancelMissedCallsNotification()

            notificationManager.cancel(420)
            config.numberMissedCalls = 0
            updateUnreadCountBadge(0)
        } catch (ignored: Exception) {
        }
    }

    private fun launchSettings() {
        closeSearch()
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_1_title_dialer_g, R.string.faq_1_text_dialer_g),
            FAQItem(R.string.faq_2_title_dialer_g, R.string.faq_2_text_dialer_g),
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons_g),
            //FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons),
            FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        val productIdX1 = BuildConfig.PRODUCT_ID_X1
        val productIdX2 = BuildConfig.PRODUCT_ID_X2
        val productIdX3 = BuildConfig.PRODUCT_ID_X3
        val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
        val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
         val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3

        startAboutActivity(
            appNameId = R.string.app_name_g,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true,
            licensingKey = BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
            productIdX1 = productIdX1, productIdX2 = productIdX2, productIdX3 = productIdX3,
            subscriptionIdX1 = subscriptionIdX1, subscriptionIdX2 = subscriptionIdX2, subscriptionIdX3 = subscriptionIdX3,
            playStoreInstalled = isPlayStoreInstalled()
        )
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            getFavoritesFragment()?.refreshItems {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                }
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                }
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    private fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            getFavoritesFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getRecentsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    fun cacheContacts(contacts: List<Contact>) {
        try {
            cachedContacts.clear()
            cachedContacts.addAll(contacts)
        } catch (e: Exception) {
        }
    }

    private fun closeSearch() {
        if (isSearchOpen) {
            getAllFragments().forEach {
                it?.onSearchQueryChanged("")
            }
            mSearchMenuItem?.collapseActionView()
        }
    }

    /*private fun actionBarSize(): Float {
        val styledAttributes = theme.obtainStyledAttributes(IntArray(1) { android.R.attr.actionBarSize })
        val actionBarSize = styledAttributes.getDimension(0, 0F)
        styledAttributes.recycle()
        return actionBarSize
    }*/

    private val actionBarSize
        get() = theme.obtainStyledAttributes(intArrayOf(android.R.attr.actionBarSize))
            .let { attrs -> attrs.getDimension(0, 0F).toInt().also { attrs.recycle() } }
}
