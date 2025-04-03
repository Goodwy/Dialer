package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
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
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.core.view.ScrollingView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.behaviorule.arturdumchev.library.pixels
import com.google.android.material.snackbar.Snackbar
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.PermissionRequiredDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MySearchMenu
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ViewPagerAdapter
import com.goodwy.dialer.databinding.ActivityMainBinding
import com.goodwy.dialer.dialogs.ChangeSortingDialog
import com.goodwy.dialer.dialogs.FilterContactSourcesDialog
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.fragments.ContactsFragment
import com.goodwy.dialer.fragments.FavoritesFragment
import com.goodwy.dialer.fragments.MyViewPagerFragment
import com.goodwy.dialer.fragments.RecentsFragment
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import me.grantland.widget.AutofitHelper
import java.io.InputStreamReader

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
    private var storedBackgroundColor = 0
    private var currentOldScrollY = 0
    var cachedContacts = ArrayList<Contact>()
    private var cachedFavorites = ArrayList<Contact>()
    private var storedContactShortcuts = ArrayList<Contact>()

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        updateNavigationBarColor = false
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        storeStateVariables()
        val useBottomNavigationBar = config.bottomNavigationBar
        updateMaterialActivityViews(binding.mainCoordinator, binding.mainHolder, useTransparentNavigation = false, useTopSearchMenu = useBottomNavigationBar)

        EventBus.getDefault().register(this)
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

        setupSecondaryLanguage()
    }

    override fun onResume() {
        super.onResume()
        if (storedShowTabs != config.showTabs || storedShowPhoneNumbers != config.showPhoneNumbers) {
            System.exit(0)
            return
        }

        @SuppressLint("UnsafeIntentLaunch")
        if (config.tabsChanged || storedBackgroundColor != getProperBackgroundColor()) {
            config.lastUsedViewPagerPage = 0
            finish()
            startActivity(intent)
            return
        }

        val properTextColor = getProperTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(this, R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.setImageDrawable(dialpadIcon)

        updateTextColors(binding.mainHolder)
        setupTabColors()
        binding.mainMenu.updateColors(getStartRequiredStatusBarColor(), scrollingView?.computeVerticalScrollOffset() ?: 0)

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            getContactsFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            getFavoritesFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            storedStartNameWithSurname = config.startNameWithSurname
        }

        if (/*!isSearchOpen && */!binding.mainMenu.isSearchOpen) {
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

//        Handler().postDelayed({
//            getRecentsFragment()?.refreshItems()
//        }, 2000)
        invalidateOptionsMenu()

        //Screen slide animation
        val animation = when (config.screenSlideAnimation) {
            1 -> ZoomOutPageTransformer()
            2 -> DepthPageTransformer()
            else -> null
        }
        binding.viewPager.setPageTransformer(true, animation)
        binding.viewPager.setPagingEnabled(!config.useSwipeToAction)

        val properBackgroundColor = getProperBackgroundColor()
        getAllFragments().forEach {
            it?.setBackgroundColor(properBackgroundColor)
        }
        if (getCurrentFragment() is RecentsFragment) clearMissedCalls()

        checkShortcuts()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowTabs = showTabs
            storedStartNameWithSurname = startNameWithSurname
            storedShowPhoneNumbers = showPhoneNumbers
            storedFontSize = fontSize
            tabsChanged = false
        }
        storedBackgroundColor = getProperBackgroundColor()
    }

    @Deprecated("Deprecated in Java")
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

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else if (isSearchOpen && mSearchMenuItem != null) {
            mSearchMenuItem!!.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        val getRecentsFragment = getRecentsFragment()
        val getFavoritesFragment = getFavoritesFragment()
        binding.mainMenu.getToolbar().menu.apply {
            findItem(R.id.search).isVisible = !config.bottomNavigationBar
            findItem(R.id.clear_call_history).isVisible = currentFragment == getRecentsFragment
            findItem(R.id.sort).isVisible = currentFragment != getRecentsFragment
            findItem(R.id.filter).isVisible = currentFragment != getRecentsFragment
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
        RadioGroupDialog(this, ArrayList(items), currentColumnCount, R.string.column_count) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                getFavoritesFragment()?.columnCountChanged()
            }
        }
    }

    private fun changeViewType() {
        config.viewType = if (config.viewType == VIEW_TYPE_LIST) VIEW_TYPE_GRID else VIEW_TYPE_LIST
        refreshMenuItems()
        getFavoritesFragment()?.refreshItems()
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
            val textColor = getProperTextColor()
            findViewById<TextView>(androidx.appcompat.R.id.search_src_text).apply {
                setTextColor(textColor)
                setHintTextColor(textColor)
            }
            findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn).apply {
                setImageResource(com.goodwy.commons.R.drawable.ic_clear_round)
                setColorFilter(textColor)
            }
            findViewById<View>(androidx.appcompat.R.id.search_plate)?.apply { // search underline
                background.setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)
            }
            setIconifiedByDefault(false)
            findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon).apply {
                setColorFilter(textColor)
            }

            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(R.string.search)
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

        @Suppress("DEPRECATION")
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
        config.needUpdateRecents = true
        runOnUiThread {
            getRecentsFragment()?.refreshItems()
        }
    }

    private fun clearCallHistory() {
        val confirmationText = "${getString(R.string.clear_history_confirmation)}\n\n${getString(R.string.cannot_be_undone)}"
        ConfirmationDialog(this, confirmationText) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    getRecentsFragment()?.refreshItems(invalidate = true)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val iconColor = getProperPrimaryColor()
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != iconColor) {
            val launchDialpad = getLaunchDialpadShortcut(iconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = iconColor
            } catch (ignored: Exception) { }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(iconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = AppCompatResources.getDrawable(this, R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(iconColor)
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

    private fun createContactShortcuts() {
        ensureBackgroundThread {
            if (isRPlus() && shortcutManager.isRequestPinShortcutSupported) {
                val starred = cachedFavorites.filter { it.phoneNumbers.isNotEmpty() }.take(3)
                if (storedContactShortcuts != starred) {
                    val allShortcuts = shortcutManager.dynamicShortcuts.filter { it.id != "launch_dialpad" }.map { it.id }
                    shortcutManager.removeDynamicShortcuts(allShortcuts)

                    storedContactShortcuts.clear()
                    storedContactShortcuts.addAll(starred)

                    starred.reversed().forEach { contact ->
                        val name = contact.getNameToDisplay()
                        getShortcutImageNeedBackground(contact.photoUri, name) { image ->
                            this.runOnUiThread {
                                val number = if (contact.phoneNumbers.size == 1) {
                                    contact.phoneNumbers[0].normalizedNumber
                                } else {
                                    contact.phoneNumbers.firstOrNull { it.isPrimary }?.normalizedNumber
                                }

                                if (number != null) {
                                    this.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                                        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                                        val intent = Intent(action).apply {
                                            data = Uri.fromParts("tel", number, null)
                                            putExtra(IS_RIGHT_APP, BuildConfig.RIGHT_APP_KEY)
                                        }

                                        val shortcut = ShortcutInfo.Builder(this, "contact_${contact.id}")
                                            .setShortLabel(name)
                                            .setIcon(Icon.createWithAdaptiveBitmap(image))
                                            .setIntent(intent)
                                            .build()
                                        this.shortcutManager.pushDynamicShortcut(shortcut)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
            icons.add(R.drawable.ic_star_vector_scaled)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_filled_scaled)
        }

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_rounded_scaled)
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

    @Suppress("DEPRECATION")
    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                if (config.bottomNavigationBar) {
                    binding.mainTabsHolder.getTabAt(position)?.select()
                    if (config.changeColourTopBar) scrollChange()
                } else binding.mainTopTabsHolder.getTabAt(position)?.select()

                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
                if (getCurrentFragment() == getRecentsFragment()) {
                    clearMissedCalls()
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
            if (config.bottomNavigationBar && config.changeColourTopBar) scrollChange()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun scrollChange() {
        val myRecyclerView = getCurrentFragment()?.myRecyclerView()
        scrollingView = myRecyclerView
        val scrollingViewOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        currentOldScrollY = scrollingViewOffset
        binding.mainMenu.updateColors(getStartRequiredStatusBarColor(), scrollingViewOffset)
        setupSearchMenuScrollListenerNew(myRecyclerView, binding.mainMenu)
    }

    private fun setupSearchMenuScrollListenerNew(scrollingView: ScrollingView?, searchMenu: MySearchMenu) {
        this.scrollingView = scrollingView
        this.mySearchMenu = searchMenu
        if (scrollingView is RecyclerView) {
            scrollingView.setOnScrollChangeListener { _, _, _, _, _ ->
                val newScrollY = scrollingView.computeVerticalScrollOffset()
                if (newScrollY == 0 || currentOldScrollY == 0) scrollingChanged(newScrollY)
                currentScrollY = newScrollY
                currentOldScrollY = currentScrollY
            }
        }
    }

    private fun scrollingChanged(newScrollY: Int) {
        if (newScrollY > 0 && currentOldScrollY == 0) {
            val colorFrom = window.statusBarColor
            val colorTo = getColoredMaterialStatusBarColor()
            animateMySearchMenuColors(colorFrom, colorTo)
        } else if (newScrollY == 0 && currentOldScrollY > 0) {
            val colorFrom = window.statusBarColor
            val colorTo = getRequiredStatusBarColor()
            animateMySearchMenuColors(colorFrom, colorTo)
        }
    }

    private fun getStartRequiredStatusBarColor(): Int {
        val scrollingViewOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        return if (scrollingViewOffset == 0) {
            getProperBackgroundColor()
        } else {
            getColoredMaterialStatusBarColor()
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
                if (config.closeSearch) {
                    closeSearch()
                } else {
                    //On tab switch, the search string is not deleted
                    //It should not start on the first startup
                    if (isSearchOpen) getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                }

                binding.viewPager.currentItem = it.position
                it.icon?.applyColorFilter(properPrimaryColor)
                it.icon?.alpha = 220 // max 255

//                val lastPosition = binding.mainTopTabsHolder.tabCount - 1
//                if (it.position == lastPosition && config.showTabs and TAB_CALL_HISTORY > 0) {
//                    clearMissedCalls()
//                }

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
                if (config.closeSearch) {
                    binding.mainMenu.closeSearch()
                } else {
                    //On tab switch, the search string is not deleted
                    //It should not start on the first startup
                    if (binding.mainMenu.isSearchOpen) getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }

                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])

//                val lastPosition = binding.mainTabsHolder.tabCount - 1
//                if (it.position == lastPosition && config.showTabs and TAB_CALL_HISTORY > 0) {
//                    clearMissedCalls()
//                }

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

    private fun launchSettings() {
        binding.mainMenu.closeSearch()
        closeSearch()
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
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

            config.needUpdateRecents = true
            getRecentsFragment()?.refreshItems {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                }
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    fun cacheContacts() {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ContactsHelper(this).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            if (SMT_PRIVATE !in config.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                    contacts.sort()
                }
            }

            try {
                cachedContacts.clear()
                cachedContacts.addAll(contacts)
            } catch (ignored: Exception) {
            }
        }
    }

    fun cacheFavorites(contacts: List<Contact>) {
        try {
            cachedFavorites.clear()
            cachedFavorites.addAll(contacts)
        } catch (_: Exception) {
        }
        createContactShortcuts()
    }

    private fun setupSecondaryLanguage() {
        if (!DialpadT9.Initialized) {
            val reader = InputStreamReader(resources.openRawResource(R.raw.t9languages))
            DialpadT9.readFromJson(reader.readText())
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshCallLog(event: Events.RefreshCallLog) {
        getRecentsFragment()?.refreshItems()
    }

    private fun closeSearch() {
        if (isSearchOpen) {
            getAllFragments().forEach {
                it?.onSearchQueryChanged("")
            }
            mSearchMenuItem?.collapseActionView()
        }
    }
}
