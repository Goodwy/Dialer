package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.SimpleContact
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ViewPagerAdapter
import com.goodwy.dialer.dialogs.ChangeSortingDialog
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.launchCreateNewContactIntent
import com.goodwy.dialer.extensions.updateUnreadCountBadge
import com.goodwy.dialer.fragments.FavoritesFragment
import com.goodwy.dialer.fragments.MyViewPagerFragment
import com.goodwy.dialer.helpers.OPEN_DIAL_PAD_AT_LAUNCH
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.helpers.tabsList
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_contacts.*
import kotlinx.android.synthetic.main.fragment_favorites.*
import kotlinx.android.synthetic.main.fragment_recents.*
import me.grantland.widget.AutofitHelper

class MainActivity : SimpleActivity() {
    private var launchedDialer = false
    private var isSearchOpen = false
    private var mSearchMenuItem: MenuItem? = null
    private var storedShowTabs = 0
    private var searchQuery = ""
    var cachedContacts = ArrayList<SimpleContact>()

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        config.tabsChanged = false

        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false

        if (isDefaultDialer()) {
            checkContactPermissions()

            if (!config.wasOverlaySnackbarConfirmed && !Settings.canDrawOverlays(this)) {
                val snackbar = Snackbar.make(main_holder, R.string.allow_displaying_over_other_apps, Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok) {
                    config.wasOverlaySnackbarConfirmed = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }

                snackbar.setBackgroundTint(getProperBackgroundColor().darkenColor())
                snackbar.setTextColor(getProperTextColor())
                snackbar.setActionTextColor(getProperTextColor())
                snackbar.show()
            }

            handleNotificationPermission { granted ->
                if (!granted) {
                    toast(R.string.no_post_notifications_permissions)
                }
            }
        } else {
            launchSetDefaultDialerIntent()
        }

        if (isQPlus() && config.blockUnknownNumbers) {
            setDefaultCallerIdApp()
        }

        hideTabs()
        setupTabs()

        SimpleContact.sorting = config.sorting
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()

        //clearMissedCalls()
        refreshMenuItems()

        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        main_dialpad_button.setImageDrawable(dialpadIcon)

        setupTabColors()
        setupToolbar(main_toolbar, searchMenuItem = mSearchMenuItem)
        updateTextColors(main_holder)

        if (view_pager.adapter != null) {

            if (config.tabsChanged) {
                if (config.useIconTabs) {
                    main_top_tabs_holder.getTabAt(0)?.text = null
                    main_top_tabs_holder.getTabAt(1)?.text = null
                    main_top_tabs_holder.getTabAt(2)?.text = null
                } else {
                    main_top_tabs_holder.getTabAt(0)?.icon = null
                    main_top_tabs_holder.getTabAt(1)?.icon = null
                    main_top_tabs_holder.getTabAt(2)?.icon = null
                }
            }

            getInactiveTabIndexes(view_pager.currentItem).forEach {
                main_top_tabs_holder.getTabAt(it)?.icon?.applyColorFilter(getProperTextColor())
                main_top_tabs_holder.getTabAt(it)?.icon?.alpha = 220 // max 255
                main_top_tabs_holder.setTabTextColors(getProperTextColor(), getProperPrimaryColor())
            }

            main_top_tabs_holder.getTabAt(view_pager.currentItem)?.icon?.applyColorFilter(properPrimaryColor)
            main_top_tabs_holder.getTabAt(view_pager.currentItem)?.icon?.alpha = 220 // max 255
            getAllFragments().forEach {
                if (it != null) it.setupColors(getProperTextColor(), getProperPrimaryColor(), getProperPrimaryColor())
                main_top_tabs_holder.setTabTextColors(getProperTextColor(), getProperPrimaryColor())
            }
        }

        if (!isSearchOpen) {
            if (storedShowTabs != config.showTabs || config.tabsChanged) {
                //hideTabs()
                System.exit(0)
                return

            }
            refreshItems(true)
        }

        checkShortcuts()
        Handler().postDelayed({
            recents_fragment?.refreshItems()
        }, 2000)
        invalidateOptionsMenu()

        //Screen slide animation
        val animation = when (config.screenSlideAnimation) {
            1 -> ZoomOutPageTransformer()
            2 -> DepthPageTransformer()
            else -> null
        }
        view_pager.setPageTransformer(true, animation)

        favorites_fragment?.setBackgroundColor(getProperBackgroundColor())
        recents_fragment?.setBackgroundColor(getProperBackgroundColor())
        contacts_fragment?.setBackgroundColor(getProperBackgroundColor())
    }

    @SuppressLint("MissingSuperCall")
    override fun onDestroy() {
        super.onDestroy()
        storedShowTabs = config.showTabs
        config.tabsChanged = false
        config.lastUsedViewPagerPage = view_pager.currentItem
    }

    override fun onPause() {
        super.onPause()
        storedShowTabs = config.showTabs
        config.tabsChanged = false
        config.lastUsedViewPagerPage = view_pager.currentItem
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
        if (isSearchOpen && mSearchMenuItem != null) {
            mSearchMenuItem!!.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        main_toolbar.menu.apply {
            findItem(R.id.clear_call_history).isVisible = currentFragment == recents_fragment
            findItem(R.id.sort).isVisible = currentFragment != recents_fragment
            findItem(R.id.create_new_contact).isVisible = currentFragment == contacts_fragment
            //findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(R.bool.hide_google_relations)
            findItem(R.id.settings).setIcon(getSettingsIcon(config.settingsIcon))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                findItem(R.id.settings).iconTintList = ColorStateList.valueOf(getProperTextColor())
            }
        }
    }

    private fun setupOptionsMenu() {
        setupSearch(main_toolbar.menu)
        main_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.clear_call_history -> clearCallHistory()
                R.id.create_new_contact -> launchCreateNewContactIntent()
                R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
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
                main_dialpad_button.beGone()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchClosed()
                }

                isSearchOpen = false
                main_dialpad_button.beVisible()
                return true
            }
        })
    }

    private fun clearCallHistory() {
        ConfirmationDialog(this, "", R.string.clear_history_confirmation) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    recents_fragment?.refreshItems()
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
        // bottom tab bar
        val activeView = main_tabs_holder.getTabAt(view_pager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true)

        getInactiveTabIndexes(view_pager.currentItem).forEach { index ->
            val inactiveView = main_tabs_holder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false)
        }

        val bottomBarColor = getBottomNavigationBackgroundColor()
        main_tabs_holder.setBackgroundColor(bottomBarColor)
        if (config.bottomNavigationBar) updateNavigationBarColor(bottomBarColor)

        // top tab bar
        val lastUsedPage = getDefaultTab()
        main_top_tabs_holder.apply {
            //background = ColorDrawable(getProperBackgroundColor())
            setSelectedTabIndicatorColor(getProperBackgroundColor())
            getTabAt(lastUsedPage)?.select()
            getTabAt(lastUsedPage)?.icon?.applyColorFilter(getProperPrimaryColor())
            getTabAt(lastUsedPage)?.icon?.alpha = 220 // max 255

            getInactiveTabIndexes(lastUsedPage).forEach {
                getTabAt(it)?.icon?.applyColorFilter(getProperTextColor())
                getTabAt(it)?.icon?.alpha = 220 // max 255
            }
        }

        main_top_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                it.icon?.applyColorFilter(getProperTextColor())
                it.icon?.alpha = 220 // max 255
            },
            tabSelectedAction = {
                view_pager.currentItem = it.position
                it.icon?.applyColorFilter(getProperPrimaryColor())
                it.icon?.alpha = 220 // max 255

            }
        )
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until tabsList.size).filter { it != activeIndex }

    private fun initFragments() {
        view_pager.offscreenPageLimit = 2
        view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                main_tabs_holder.getTabAt(position)?.select()
                main_top_tabs_holder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    if (it != null) it.finishActMode()
                }
                refreshMenuItems()
                if (getCurrentFragment() == recents_fragment) clearMissedCalls()
            }
        })

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        main_tabs_holder.onGlobalLayout {
            Handler().postDelayed({
                var wantedTab = getDefaultTab()

                // open the Recents tab if we got here by clicking a missed call notification
                if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                    wantedTab = main_tabs_holder.tabCount - 2
                    ensureBackgroundThread {
                        clearMissedCalls()
                    }
                }

                main_tabs_holder.getTabAt(wantedTab)?.select()
                refreshMenuItems()
            }, 100L)
        }
        main_top_tabs_holder.onGlobalLayout {
            Handler().postDelayed({
                var wantedTab = getDefaultTab()

                // open the Recents tab if we got here by clicking a missed call notification
                if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                    wantedTab = main_top_tabs_holder.tabCount - 2
                    ensureBackgroundThread {
                        clearMissedCalls()
                    }
                }

                main_top_tabs_holder.getTabAt(wantedTab)?.select()
                refreshMenuItems()
            }, 100L)
        }

        main_dialpad_button.setOnClickListener {
            launchDialpad()
        }

        view_pager.onGlobalLayout {
            refreshMenuItems()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun hideTabs() {
        // top tab bar
        val selectedTabIndex = main_top_tabs_holder.selectedTabPosition
        view_pager.adapter = null
        main_top_tabs_holder.removeAllTabs()
        var skippedTabs = 0
        var isAnySelected = false
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value == 0) {
                skippedTabs++
            } else {
                val tab = if (config.useIconTabs) main_top_tabs_holder.newTab().setIcon(getTabIcon(index)) else main_top_tabs_holder.newTab().setText(getTabLabel(index))
                tab.contentDescription = getTabContentDescription(index)
                val wasAlreadySelected = selectedTabIndex > -1 && selectedTabIndex == index - skippedTabs
                val shouldSelect = !isAnySelected && wasAlreadySelected
                if (shouldSelect) {
                    isAnySelected = true
                }
                main_top_tabs_holder.addTab(tab, index - skippedTabs, shouldSelect)
                main_top_tabs_holder.setTabTextColors(getProperTextColor(),
                    getProperPrimaryColor())
            }
        }
        if (!isAnySelected) {
            main_top_tabs_holder.selectTab(main_top_tabs_holder.getTabAt(getDefaultTab()))
        }
        main_top_tabs_container.beGoneIf(main_top_tabs_holder.tabCount == 1 || config.bottomNavigationBar)
        storedShowTabs = config.showTabs
        config.tabsChanged = false
    }

    private fun setupTabs() {
        // bottom tab bar
        view_pager.adapter = null
        main_tabs_holder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                main_tabs_holder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.apply {
                        setImageDrawable(getTabIcon(index))
                        alpha = 0.86f
                    }
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.apply {
                        text = getTabLabel(index)
                        alpha = 0.86f
                        beGoneIf(config.useIconTabs)
                    }
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    main_tabs_holder.addTab(this)
                }
            }
        }

        main_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false)
            },
            tabSelectedAction = {
                closeSearch()
                view_pager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true)
            }
        )

        main_tabs_holder.beGoneIf(main_tabs_holder.tabCount == 1 || !config.bottomNavigationBar)
        storedShowTabs = config.showTabs
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
            1 -> R.drawable.ic_clock
            else -> R.drawable.ic_person_rounded
        }
        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
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

        if (view_pager.adapter == null) {
            view_pager.adapter = ViewPagerAdapter(this)
            //view_pager.currentItem = if (openLastTab) main_top_tabs_holder.selectedTabPosition else getDefaultTab()
            view_pager.currentItem = if (openLastTab) config.lastUsedViewPagerPage else getDefaultTab()
            view_pager.onGlobalLayout {
                refreshFragments()
            }
        } else {
            refreshFragments()
        }
    }

    private fun launchDialpad() {
        Intent(applicationContext, DialpadActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun refreshFragments() {
        favorites_fragment?.refreshItems()
        recents_fragment?.refreshItems()
        contacts_fragment?.refreshItems()
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment>()

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(favorites_fragment)
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(recents_fragment)
        }

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(contacts_fragment)
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment? = getAllFragments().getOrNull(view_pager.currentItem)

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        return when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < main_top_tabs_holder.tabCount) config.lastUsedViewPagerPage else 0
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
        closeSearch()
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons_g))
            //faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name_g, licenses, BuildConfig.VERSION_NAME, faqItems, true, BuildConfig.GOOGLE_PLAY_LICENSING_KEY, BuildConfig.PRODUCT_ID_X1, BuildConfig.PRODUCT_ID_X2, BuildConfig.PRODUCT_ID_X3)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            favorites_fragment?.refreshItems {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                }
            }

            contacts_fragment?.refreshItems {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                }
            }
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

    fun cacheContacts(contacts: List<SimpleContact>) {
        try {
            cachedContacts.clear()
            cachedContacts.addAll(contacts)
        } catch (e: Exception) {
        }
    }
}
