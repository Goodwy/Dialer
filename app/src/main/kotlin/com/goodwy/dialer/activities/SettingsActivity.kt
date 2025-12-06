package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import com.behaviorule.arturdumchev.library.pixels
import com.goodwy.commons.activities.ManageBlockedNumbersActivity
import com.goodwy.commons.dialogs.*
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.databinding.ActivitySettingsBinding
import com.goodwy.dialer.dialogs.ChangeTextDialog
import com.goodwy.dialer.dialogs.ExportCallHistoryDialog
import com.goodwy.dialer.dialogs.ManageVisibleTabsDialog
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.models.RecentCall
import com.goodwy.dialer.helpers.*
import com.google.gson.Gson
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    companion object {
        private const val CALL_HISTORY_FILE_TYPE = "application/json"
        private val IMPORT_CALL_HISTORY_FILE_TYPES = buildList {
            add("application/json")
            if (!isQPlus()) {
                // Workaround for https://github.com/FossifyOrg/Messages/issues/88
                add("application/octet-stream")
            }
        }
    }

    private val binding by viewBinding(ActivitySettingsBinding::inflate)
    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                toast(R.string.importing)
                importCallHistory(uri)
            }
        }

    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument(CALL_HISTORY_FILE_TYPE)) { uri ->
        if (uri != null) {
            toast(R.string.exporting)
            RecentsHelper(this).getRecentCalls(queryLimit = QUERY_LIMIT_MAX_VALUE) { recents ->
                exportCallHistory(recents, uri)
            }
        }
    }

    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    private val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    private val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    private val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    override fun onCreate(savedInstanceState: Bundle?) {
        useOverflowIcon = false
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        binding.apply {
//            setupEdgeToEdge(padBottomSystem = listOf(settingsNestedScrollview))
            setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)
        }

        val iapList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
        val subList: ArrayList<String> =
            arrayListOf(
                subscriptionIdX1, subscriptionIdX2, subscriptionIdX3,
                subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3
            )
        val ruStoreList: ArrayList<String> =
            arrayListOf(
                productIdX1, productIdX2, productIdX3,
                subscriptionIdX1, subscriptionIdX2, subscriptionIdX3,
                subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3
            )
        PurchaseHelper().checkPurchase(
            this@SettingsActivity,
            iapList = iapList,
            subList = subList,
            ruStoreList = ruStoreList
        ) { updatePro ->
            if (updatePro) updatePro()
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()

        setupCustomizeColors()
        setupDialPadOpen()
        setupOverflowIcon()
        setupFloatingButtonStyle()
        setupUseColoredContacts()
        setupContactsColorList()
        setupColorSimIcons()
        setupSimCardColorList()

        setupManageBlockedNumbers()
        setupUseSpeechToText()
        setupChangeDateTimeFormat()
        setupFormatPhoneNumbers()
        setupFontSize()
        setupUseEnglish()
        setupLanguage()

        setupDefaultTab()
        setupManageShownTabs()
        setupNavigationBarStyle()
        setupUseIconTabs()
        setupScreenSlideAnimation()
        setupOpenSearch()
        setupEndSearch()

        setupOnFavoriteClick()
        setupOnRecentClick()
        setupOnContactClick()
        setupUseSwipeToAction()
        setupSwipeVibration()
        setupSwipeRipple()
        setupSwipeRightAction()
        setupSwipeLeftAction()
        setupDeleteConfirmation()

        setupManageSpeedDial()
        setupDialpadStyle()
        setupShowRecentCallsOnDialpad()

        setupFlashForAlerts()

        setupBackgroundCallScreen()
        setupTransparentCallScreen()
        setupAnswerStyle()
        setupCallButtonStyle()
        setupAlwaysShowFullscreen()
        setupKeepCallsInPopUp()
        setupBackPressedEndCall()
        setupQuickAnswers()
        setupCallerDescription()
        setupSimDialogStyle()
        setupHideDialpadLetters()
        setupDialpadNumbers()
        setupDisableProximitySensor()
        setupDialpadVibrations()
        setupCallStartEndVibrations()
        setupDialpadBeeps()
        setupDisableSwipeToAnswer()
        setupShowCallConfirmation()
        setupCallUsingSameSim()
        setupCallBlockButton()

        setupBlockCallFromAnotherApp()

        setupGroupCalls()
        setupGroupSubsequentCalls()
        setupQueryLimitRecent()
        setupShowDividers()
        setupShowContactThumbnails()
        setupContactThumbnailsSize()
        setupShowPhoneNumbers()
        setupStartNameWithSurname()
        setupShowNicknameInsteadNames()
        setupUseRelativeDate()
        setupChangeColourTopBar()

        setupCallsExport()
        setupCallsImport()

        setupTipJar()
        setupAbout()

        updateTextColors(binding.settingsHolder)

        binding.apply {
            val properTextColor = getProperTextColor()
            val properPrimaryColor = getProperPrimaryColor()
            val surfaceColor = getSurfaceColor()

            arrayOf(
                settingsAppearanceLabel,
                settingsGeneralLabel,
                settingsTabsLabel,
                settingsSwipeGesturesLabel,
                settingsDialpadLabel,
                settingsCallsLabel,
                settingsNotificationsLabel,
                settingsSecurityLabel,
                settingsListViewLabel,
                settingsBackupsLabel,
                settingsOtherLabel).forEach {
                it.setTextColor(properPrimaryColor)
            }

            arrayOf(
                settingsColorCustomizationHolder,
                settingsGeneralHolder,
                settingsTabsHolder,
                settingsSwipeGesturesHolder,
                settingsDialpadHolder,
                settingsCallsHolder,
                settingsNotificationsHolder,
                settingsSecurityHolder,
                settingsListViewHolder,
                settingsBackupsHolder,
                settingsOtherHolder
            ).forEach {
                it.setCardBackgroundColor(surfaceColor)
            }

            arrayOf(
                settingsCustomizeColorsChevron,
                settingsManageShownTabsChevron,
                settingsExportCallsChevron,
                settingsImportCallsChevron,
                settingsManageBlockedNumbersChevron,
                settingsManageSpeedDialChevron,
                settingsTipJarChevron,
                settingsAboutChevron,
                settingsDialpadStyleChevron
            ).forEach {
                it.applyColorFilter(properTextColor)
            }
        }
    }

    private fun updatePro(isPro: Boolean = checkPro()) {
        binding.apply {
            settingsPurchaseThankYouHolder.beGoneIf(isPro)
            settingsTipJarHolder.beVisibleIf(isPro)

            val stringId =
                if (isRTLLayout) com.goodwy.strings.R.string.swipe_right_action
                else com.goodwy.strings.R.string.swipe_left_action
            settingsSwipeLeftActionLabel.text = addLockedLabelIfNeeded(stringId, isPro)

            arrayOf(
                settingsSimCardColor1Holder,
                settingsSimCardColor2Holder,
                settingsSwipeLeftActionHolder
            ).forEach {
                it.alpha = if (isPro) 1f else 0.4f
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupOptionsMenu() {
        binding.settingsToolbar.menu.apply {
            findItem(R.id.calling_accounts).isVisible = canLaunchAccountsConfiguration()
        }
        binding.settingsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.calling_accounts -> {
                    launchAccountsConfiguration()
                    true
                }
                R.id.whats_new -> {
                    WhatsNewDialog(this@SettingsActivity, whatsNewList())
                    true
                }
                else -> false
            }
        }
    }

    private fun setupPurchaseThankYou() = binding.apply {
        settingsPurchaseThankYouHolder.beGoneIf(checkPro(false))
        settingsPurchaseThankYouHolder.onClick = { launchPurchase() }
    }

    private fun setupCustomizeColors() {
        binding.settingsCustomizeColorsHolder.setOnClickListener {
            startCustomizationActivity(
                showAccentColor = true,
                isCollection = isOrWasThankYouInstalled() || isCollection(),
                productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
                productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
                subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                showAppIconColor = true
            )
        }
    }

    private fun setupUseEnglish() {
        binding.apply {
            settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
            settingsUseEnglish.isChecked = config.useEnglish
            settingsUseEnglishHolder.setOnClickListener {
                settingsUseEnglish.toggle()
                config.useEnglish = settingsUseEnglish.isChecked
                exitProcess(0)
            }
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        if (isTiramisuPlus()) {
            settingsLanguageHolder.beVisible()
            settingsLanguageHolder.setOnClickListener {
                launchChangeAppLanguageIntent()
            }
        } else {
            settingsLanguageHolder.beGone()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupManageBlockedNumbers() = binding.apply {
        settingsManageBlockedNumbersCount.text =
            if (baseConfig.blockingEnabled) getBlockedNumbers().size.toString()
            else getString(R.string.off)

        val getProperTextColor = getProperTextColor()
        val red = resources.getColor(R.color.red_missed, theme)
        val colorUnknown = if (baseConfig.blockUnknownNumbers) red else getProperTextColor
        val alphaUnknown = if (baseConfig.blockUnknownNumbers) 1f else 0.6f
        settingsManageBlockedNumbersIconUnknown.apply {
            beGoneIf(!baseConfig.blockingEnabled)
            applyColorFilter(colorUnknown)
            alpha = alphaUnknown
        }

        val colorHidden = if (baseConfig.blockHiddenNumbers) red else getProperTextColor
        val alphaHidden = if (baseConfig.blockHiddenNumbers) 1f else 0.6f
        settingsManageBlockedNumbersIconHidden.apply {
            beGoneIf(!baseConfig.blockingEnabled)
            applyColorFilter(colorHidden)
            alpha = alphaHidden
        }

        settingsManageBlockedNumbersHolder.setOnClickListener {
            Intent(this@SettingsActivity, ManageBlockedNumbersActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupUseSpeechToText() = binding.apply {
        settingsUseSpeechToText.isChecked = config.useSpeechToText
        settingsUseSpeechToTextHolder.setOnClickListener {
            settingsUseSpeechToText.toggle()
            config.useSpeechToText = settingsUseSpeechToText.isChecked
            config.needRestart = true
        }
    }

    private fun setupManageSpeedDial() {
        binding.settingsManageSpeedDialHolder.setOnClickListener {
            Intent(this, ManageSpeedDialActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupChangeDateTimeFormat() {
        updateDateTimeFormat()
        binding.settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this, true) {
                updateDateTimeFormat()
                config.needRestart = true
            }
        }
    }

    private fun updateDateTimeFormat() {
        val cal = Calendar.getInstance(Locale.ENGLISH).timeInMillis
        val formatDate = cal.formatDate(this@SettingsActivity)
        binding.settingsChangeDateTimeFormat.text = formatDate
    }

    private fun setupFontSize() = binding.apply {
        settingsFontSize.text = getFontSizeText()
        settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large)))

            RadioGroupDialog(
                this@SettingsActivity,
                items,
                config.fontSize,
                R.string.font_size,
                defaultItemId = FONT_SIZE_MEDIUM
            ) {
                config.fontSize = it as Int
                settingsFontSize.text = getFontSizeText()
                config.needRestart = true
            }
        }
    }

    private fun setupDefaultTab() {
        binding.settingsDefaultTab.text = getDefaultTabText()
        binding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_LAST_USED, getString(R.string.last_used_tab)),
                RadioItem(TAB_FAVORITES, getString(R.string.favorites_tab), icon = R.drawable.ic_star_vector_scaled),
                RadioItem(TAB_CALL_HISTORY, getString(R.string.recents), icon = R.drawable.ic_clock_filled_scaled),
                RadioItem(TAB_CONTACTS, getString(R.string.contacts_tab), icon = R.drawable.ic_person_rounded_scaled))

            RadioGroupIconDialog(
                this@SettingsActivity,
                items,
                config.defaultTab,
                R.string.default_tab,
                defaultItemId = TAB_LAST_USED
            ) {
                config.defaultTab = it as Int
                binding.settingsDefaultTab.text = getDefaultTabText()
            }
        }
    }

    private fun getDefaultTabText() = getString(
        when (baseConfig.defaultTab) {
            TAB_FAVORITES -> R.string.favorites_tab
            TAB_CALL_HISTORY -> R.string.recents
            TAB_CONTACTS -> R.string.contacts_tab
            else -> R.string.last_used_tab
        }
    )

    private fun setupNavigationBarStyle() {
        binding.settingsNavigationBarStyleLabel.text = formatWithDeprecatedBadge(R.string.tab_navigation)
        binding.settingsNavigationBarStyle.text = getNavigationBarStyleText()
        binding.settingsNavigationBarStyleHolder.setOnClickListener {
            val top = formatWithDeprecatedBadge(R.string.top)
            val items = arrayListOf(
                RadioItem(0, top, icon = R.drawable.ic_tab_top),
                RadioItem(1, getString(R.string.bottom), icon = R.drawable.ic_tab_bottom),
            )

            val checkedItemId = if (config.bottomNavigationBar) 1 else 0
            RadioGroupIconDialog(this@SettingsActivity, items, checkedItemId, R.string.tab_navigation) {
                config.bottomNavigationBar = it == 1
                config.needRestart = true
                binding.settingsNavigationBarStyle.text = getNavigationBarStyleText()
                binding.settingsChangeColourTopBarHolder.beVisibleIf(config.bottomNavigationBar)
            }
        }
    }

    private fun setupChangeColourTopBar() {
        binding.apply {
            settingsChangeColourTopBarHolder.beVisibleIf(config.bottomNavigationBar)
            settingsChangeColourTopBar.isChecked = config.changeColourTopBar
            settingsChangeColourTopBarHolder.setOnClickListener {
                settingsChangeColourTopBar.toggle()
                config.changeColourTopBar = settingsChangeColourTopBar.isChecked
                config.needRestart = true
            }
        }
    }

    private fun setupUseIconTabs() {
        binding.apply {
            settingsUseIconTabs.isChecked = config.useIconTabs
            settingsUseIconTabsHolder.setOnClickListener {
                settingsUseIconTabs.toggle()
                config.useIconTabs = settingsUseIconTabs.isChecked
                config.needRestart = true
            }
        }
    }

    private fun setupManageShownTabs() {
        binding.settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupScreenSlideAnimation() {
        binding.settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
        binding.settingsScreenSlideAnimationHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, getString(R.string.no), icon = com.goodwy.commons.R.drawable.ic_view_array),
                RadioItem(1, getString(R.string.screen_slide_animation_zoomout), icon = R.drawable.ic_view_carousel),
                RadioItem(2, getString(R.string.screen_slide_animation_depth), icon = R.drawable.ic_playing_cards),
            )

            RadioGroupIconDialog(
                this@SettingsActivity,
                items,
                config.screenSlideAnimation,
                R.string.screen_slide_animation,
                defaultItemId = 1
            ) {
                config.screenSlideAnimation = it as Int
                config.needRestart = true
                binding.settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
            }
        }
    }

    private fun setupDialPadOpen() {
        binding.apply {
            settingsOpenDialpadAtLaunch.isChecked = config.openDialPadAtLaunch
            settingsOpenDialpadAtLaunchHolder.setOnClickListener {
                settingsOpenDialpadAtLaunch.toggle()
                config.openDialPadAtLaunch = settingsOpenDialpadAtLaunch.isChecked
            }
        }
    }

    private fun setupShowDividers() = binding.apply {
        settingsShowDividers.isChecked = config.useDividers
        settingsShowDividersHolder.setOnClickListener {
            settingsShowDividers.toggle()
            config.useDividers = settingsShowDividers.isChecked
            config.needRestart = true
        }
    }

    private fun setupShowContactThumbnails() {
        binding.apply {
            settingsShowContactThumbnails.isChecked = config.showContactThumbnails
            settingsShowContactThumbnailsHolder.setOnClickListener {
                settingsShowContactThumbnails.toggle()
                config.showContactThumbnails = settingsShowContactThumbnails.isChecked
                settingsContactThumbnailsSizeHolder.beVisibleIf(config.showContactThumbnails)
            }
        }
    }

    private fun setupContactThumbnailsSize() = binding.apply {
        val pro = checkPro()
        settingsContactThumbnailsSizeHolder.beVisibleIf(config.showContactThumbnails)
        settingsContactThumbnailsSizeHolder.alpha = if (pro) 1f else 0.4f
        settingsContactThumbnailsSizeLabel.text = addLockedLabelIfNeeded(R.string.contact_thumbnails_size, pro)
        settingsContactThumbnailsSize.text = getContactThumbnailsSizeText()
        settingsContactThumbnailsSizeHolder.setOnClickListener {
            if (pro) {
                val items = arrayListOf(
                    RadioItem(CONTACT_THUMBNAILS_SIZE_SMALL, getString(R.string.small), CONTACT_THUMBNAILS_SIZE_SMALL),
                    RadioItem(CONTACT_THUMBNAILS_SIZE_MEDIUM, getString(R.string.medium), CONTACT_THUMBNAILS_SIZE_MEDIUM),
                    RadioItem(CONTACT_THUMBNAILS_SIZE_LARGE, getString(R.string.large), CONTACT_THUMBNAILS_SIZE_LARGE),
                    RadioItem(CONTACT_THUMBNAILS_SIZE_EXTRA_LARGE, getString(R.string.extra_large), CONTACT_THUMBNAILS_SIZE_EXTRA_LARGE)
                )

                RadioGroupDialog(
                    this@SettingsActivity,
                    items,
                    config.contactThumbnailsSize,
                    R.string.contact_thumbnails_size,
                    defaultItemId = CONTACT_THUMBNAILS_SIZE_MEDIUM
                ) {
                    config.contactThumbnailsSize = it as Int
                    settingsContactThumbnailsSize.text = getContactThumbnailsSizeText()
                    config.needRestart = true
                }
            } else {
                RxAnimation.from(settingsContactThumbnailsSizeHolder)
                    .shake(shakeTranslation = 2f)
                    .subscribe()

                showSnackbar(binding.root)
            }
        }
    }

    private fun getContactThumbnailsSizeText() = getString(
        when (baseConfig.contactThumbnailsSize) {
            CONTACT_THUMBNAILS_SIZE_SMALL -> com.goodwy.commons.R.string.small
            CONTACT_THUMBNAILS_SIZE_MEDIUM -> com.goodwy.commons.R.string.medium
            CONTACT_THUMBNAILS_SIZE_LARGE -> com.goodwy.commons.R.string.large
            else -> com.goodwy.commons.R.string.extra_large
        }
    )

    private fun setupShowPhoneNumbers() {
        binding.apply {
            settingsShowPhoneNumbers.isChecked = config.showPhoneNumbers
            settingsShowPhoneNumbersHolder.setOnClickListener {
                settingsShowPhoneNumbers.toggle()
                config.showPhoneNumbers = settingsShowPhoneNumbers.isChecked
            }
        }
    }

    private fun setupUseColoredContacts() = binding.apply {
        settingsColoredContacts.isChecked = config.useColoredContacts
        settingsColoredContactsHolder.setOnClickListener {
            settingsColoredContacts.toggle()
            config.useColoredContacts = settingsColoredContacts.isChecked
            settingsContactColorListHolder.beVisibleIf(config.useColoredContacts)
            config.needRestart = true
        }
    }

    private fun setupContactsColorList() = binding.apply {
        settingsContactColorListHolder.beVisibleIf(config.useColoredContacts)
        settingsContactColorListIcon.setImageResource(getContactsColorListIcon(config.contactColorList))
        settingsContactColorListHolder.setOnClickListener {
            val items = arrayListOf(
                com.goodwy.commons.R.drawable.ic_color_list,
                com.goodwy.commons.R.drawable.ic_color_list_android,
                com.goodwy.commons.R.drawable.ic_color_list_ios,
                com.goodwy.commons.R.drawable.ic_color_list_arc
            )

            IconListDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.contactColorList,
                defaultItemId = LBC_ANDROID,
                titleId = com.goodwy.strings.R.string.overflow_icon
            ) { wasPositivePressed, newValue ->
                if (wasPositivePressed) {
                    if (config.contactColorList != newValue) {
                        config.contactColorList = newValue
                        settingsContactColorListIcon.setImageResource(getContactsColorListIcon(config.contactColorList))
                        config.needRestart = true
                    }
                }
            }
        }
    }

    private fun setupBackgroundCallScreen() {
        val pro = checkPro()
        val black = if (pro) getString(R.string.black) else getString(R.string.black_locked)
        binding.settingsBackgroundCallScreen.text = getBackgroundCallScreenText()
        binding.settingsBackgroundCallScreenHolder.setOnClickListener {
            val items = if (isTiramisuPlus()) {
                arrayListOf(
                    RadioItem(THEME_BACKGROUND, getString(R.string.theme), icon = R.drawable.ic_theme),
                    RadioItem(BLUR_AVATAR, getString(R.string.blurry_contact_photo), icon = R.drawable.ic_contact_blur),
                    RadioItem(AVATAR, getString(R.string.contact_photo), icon = R.drawable.ic_contact_photo),
                    RadioItem(BLACK_BACKGROUND, black, icon = R.drawable.ic_theme_black)
                )
            } else {
                arrayListOf(
                    RadioItem(THEME_BACKGROUND, getString(R.string.theme), icon = R.drawable.ic_theme),
                    RadioItem(BLUR_AVATAR, getString(R.string.blurry_contact_photo), icon = R.drawable.ic_contact_blur),
                    RadioItem(AVATAR, getString(R.string.contact_photo), icon = R.drawable.ic_contact_photo),
                    RadioItem(TRANSPARENT_BACKGROUND, getString(R.string.blurry_wallpaper), icon = R.drawable.ic_wallpaper),
                    RadioItem(BLACK_BACKGROUND, black, icon = R.drawable.ic_theme_black)
                )
            }

            RadioGroupIconDialog(
                this@SettingsActivity,
                items,
                config.backgroundCallScreen,
                R.string.call_screen_background,
                defaultItemId = BLUR_AVATAR
            ) {
                if (it as Int == TRANSPARENT_BACKGROUND) {
                    if (hasPermission(PERMISSION_READ_STORAGE)) {
                        config.backgroundCallScreen = it
                        binding.settingsBackgroundCallScreen.text = getBackgroundCallScreenText()
                    } else {
                        handlePermission(PERMISSION_READ_STORAGE) { permission ->
                            if (permission) {
                                config.backgroundCallScreen = it
                                binding.settingsBackgroundCallScreen.text = getBackgroundCallScreenText()
                            } else {
                                toast(R.string.no_storage_permissions)
                            }
                        }
                    }
                } else if (it == BLACK_BACKGROUND) {
                    if (pro) {
                        config.backgroundCallScreen = it
                        binding.settingsBackgroundCallScreen.text = getBackgroundCallScreenText()
                    } else {
                        RxAnimation.from(binding.settingsBackgroundCallScreenHolder)
                            .shake(shakeTranslation = 2f)
                            .subscribe()

                        showSnackbar(binding.root)
                    }
                } else {
                    config.backgroundCallScreen = it
                    binding.settingsBackgroundCallScreen.text = getBackgroundCallScreenText()
                }
            }
        }
    }

    private fun getBackgroundCallScreenText() = getString(
        when (config.backgroundCallScreen) {
            BLUR_AVATAR -> R.string.blurry_contact_photo
            AVATAR -> R.string.contact_photo
            TRANSPARENT_BACKGROUND -> R.string.blurry_wallpaper
            BLACK_BACKGROUND -> R.string.black
            else -> R.string.theme
        }
    )

    private fun setupTransparentCallScreen() {
        binding.apply {
            if (isTiramisuPlus()) settingsTransparentCallScreenHolder.beGone()
            else {
                if (hasPermission(PERMISSION_READ_STORAGE)) {
                    settingsTransparentCallScreen.isChecked = config.transparentCallScreen
                } else settingsTransparentCallScreen.isChecked = false
                settingsTransparentCallScreenHolder.setOnClickListener {
                    if (hasPermission(PERMISSION_READ_STORAGE)) {
                        settingsTransparentCallScreen.toggle()
                        config.transparentCallScreen = settingsTransparentCallScreen.isChecked
                    } else {
                        handlePermission(PERMISSION_READ_STORAGE) {
                            if (it) {
                                settingsTransparentCallScreen.toggle()
                                config.transparentCallScreen = settingsTransparentCallScreen.isChecked
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupAnswerStyle() {
        binding.settingsAnswerStyle.text = getAnswerStyleText()
        binding.settingsAnswerStyleHolder.setOnClickListener {
            launchAnswerStyleDialog()
        }
    }

    private fun launchAnswerStyleDialog() {
        val pro = checkPro()
        val sliderOutline = addLockedLabelIfNeeded(R.string.answer_slider_outline, pro)
        val sliderVertical = addLockedLabelIfNeeded(R.string.answer_slider_vertical, pro)
        val items = arrayListOf(
            RadioItem(ANSWER_BUTTON, getString(R.string.buttons), icon = R.drawable.ic_answer_buttons),
            RadioItem(ANSWER_SLIDER, getString(R.string.answer_slider), icon = R.drawable.ic_slider),
            RadioItem(ANSWER_SLIDER_OUTLINE, sliderOutline, icon = R.drawable.ic_slider_outline),
            RadioItem(ANSWER_SLIDER_VERTICAL, sliderVertical, icon = R.drawable.ic_slider_vertical),
        )

        RadioGroupIconDialog(
            this@SettingsActivity,
            items,
            config.answerStyle,
            R.string.answer_style,
            defaultItemId = ANSWER_BUTTON
        ) {
            if (it as Int == ANSWER_SLIDER_OUTLINE || it == ANSWER_SLIDER_VERTICAL) {
                if (pro) {
                    config.answerStyle = it
                    binding.settingsAnswerStyle.text = getAnswerStyleText()
                } else {
                    RxAnimation.from(binding.settingsAnswerStyleHolder)
                        .shake(shakeTranslation = 2f)
                        .subscribe()

                    showSnackbar(binding.root)
                }
            } else {
                config.answerStyle = it
                binding.settingsAnswerStyle.text = getAnswerStyleText()
            }

        }
    }

    private fun getAnswerStyleText() = getString(
        when (config.answerStyle) {
            ANSWER_SLIDER -> R.string.answer_slider
            ANSWER_SLIDER_OUTLINE -> R.string.answer_slider_outline
            ANSWER_SLIDER_VERTICAL -> R.string.answer_slider_vertical
            else -> R.string.buttons
        }
    )

    private fun setupCallButtonStyle() {
        val isSmallScreen =
            resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_SMALL
        binding.settingsCallButtonStyleHolder.beVisibleIf(!isSmallScreen)
        binding.settingsCallButtonStyle.text = getCallButtonStyleText()
        binding.settingsCallButtonStyleHolder.setOnClickListener {
            launchCallButtonStyleDialog()
        }
    }

    private fun launchCallButtonStyleDialog() {
        val pro = checkPro()
        val iOS17Text = addLockedLabelIfNeeded(R.string.bottom, pro)
        val items = arrayListOf(
            RadioItem(IOS16, getString(R.string.top), icon = R.drawable.ic_call_button_top),
            RadioItem(IOS17, iOS17Text, icon = R.drawable.ic_call_button_bottom),
        )

        RadioGroupIconDialog(this@SettingsActivity, items, config.callButtonStyle, R.string.call_button_style) {
            if (it as Int == IOS17) {
                if (pro) {
                    config.callButtonStyle = it
                    binding.settingsCallButtonStyle.text = getCallButtonStyleText()
                } else {
                    RxAnimation.from(binding.settingsCallButtonStyleHolder)
                        .shake(shakeTranslation = 2f)
                        .subscribe()

                    showSnackbar(binding.root)
                }
            } else {
                config.callButtonStyle = it
                binding.settingsCallButtonStyle.text = getCallButtonStyleText()
            }

        }
    }

    private fun getCallButtonStyleText() = getString(
        when (config.callButtonStyle) {
            IOS16 -> R.string.top
            else -> R.string.bottom
        }
    )

    private fun setupCallerDescription() {
        binding.settingsShowCallerDescription.text = getCallerDescriptionText()
        binding.settingsShowCallerDescriptionHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SHOW_CALLER_NOTHING, getString(R.string.nothing)),
                RadioItem(SHOW_CALLER_COMPANY, getString(R.string.company)),
                RadioItem(SHOW_CALLER_NICKNAME, getString(R.string.nickname)))

            RadioGroupDialog(
                this@SettingsActivity,
                items,
                config.showCallerDescription,
                R.string.show_caller_description_g,
                defaultItemId = SHOW_CALLER_COMPANY
            ) {
                config.showCallerDescription = it as Int
                binding.settingsShowCallerDescription.text = getCallerDescriptionText()
            }
        }
    }

    private fun getCallerDescriptionText() = getString(
        when (config.showCallerDescription) {
            SHOW_CALLER_COMPANY -> R.string.company
            SHOW_CALLER_NICKNAME -> R.string.nickname
            else -> R.string.nothing
        }
    )

    private fun setupAlwaysShowFullscreen() {
        binding.apply {
            settingsAlwaysShowFullscreen.isChecked = config.showIncomingCallsFullScreen
            settingsAlwaysShowFullscreenHolder.setOnClickListener {
                settingsAlwaysShowFullscreen.toggle()
                config.showIncomingCallsFullScreen = settingsAlwaysShowFullscreen.isChecked
                settingsKeepCallsInPopUpHolder.beVisibleIf(!config.showIncomingCallsFullScreen)
            }
        }
    }

    private fun setupKeepCallsInPopUp() {
        binding.apply {
            settingsKeepCallsInPopUpHolder.beVisibleIf(!config.showIncomingCallsFullScreen)
            settingsKeepCallsInPopUp.isChecked = config.keepCallsInPopUp
            settingsKeepCallsInPopUpHolder.setOnClickListener {
                settingsKeepCallsInPopUp.toggle()
                config.keepCallsInPopUp = settingsKeepCallsInPopUp.isChecked
            }
            settingsKeepCallsInPopUpFaq.imageTintList = ColorStateList.valueOf(getProperTextColor())
            settingsKeepCallsInPopUpFaq.setOnClickListener {
                ConfirmationDialog(this@SettingsActivity, messageId = R.string.keep_calls_in_popup_summary, positive = com.goodwy.commons.R.string.ok, negative = 0) {}
            }
        }
    }

    private fun setupBackPressedEndCall() {
        binding.apply {
            settingsBackPressedEndCall.isChecked = config.backPressedEndCall
            settingsBackPressedEndCallHolder.setOnClickListener {
                settingsBackPressedEndCall.toggle()
                config.backPressedEndCall = settingsBackPressedEndCall.isChecked
            }
        }
    }

    private fun setupQuickAnswers() {
        binding.apply {
            val getProperTextColor = getProperTextColor()
            settingsQuickAnswerOne.applyColorFilter(getProperTextColor)
            settingsQuickAnswerTwo.applyColorFilter(getProperTextColor)
            settingsQuickAnswerThree.applyColorFilter(getProperTextColor)

            settingsQuickAnswerOne.setOnClickListener {
                val index = 0
                ChangeTextDialog(
                    this@SettingsActivity,
                    currentText = config.quickAnswers[index],
                    showNeutralButton = true
                ) {
                    if (it == "") {
                        val text = getString(R.string.message_call_later)
                        addQuickAnswer(index, text)
                    } else {
                        addQuickAnswer(index, it)
                    }
                    toast(config.quickAnswers[index])
                }
            }
            settingsQuickAnswerTwo.setOnClickListener {
                val index = 1
                ChangeTextDialog(
                    this@SettingsActivity,
                    currentText = config.quickAnswers[index],
                    showNeutralButton = true
                ) {
                    if (it == "") {
                        val text = getString(R.string.message_on_my_way)
                        addQuickAnswer(index, text)
                    } else {
                        addQuickAnswer(index, it)
                    }
                    toast(config.quickAnswers[index])
                }
            }
            settingsQuickAnswerThree.setOnClickListener {
                val index = 2
                ChangeTextDialog(
                    this@SettingsActivity,
                    currentText = config.quickAnswers[index],
                    showNeutralButton = true
                ) {
                    if (it == "") {
                        val text = getString(R.string.message_cant_talk_right_now)
                        addQuickAnswer(index, text)
                    } else {
                        addQuickAnswer(index, it)
                    }
                    toast(config.quickAnswers[index])
                }
            }
        }
    }

    private fun addQuickAnswer(index: Int, text: String) {
        val quickAnswers = config.quickAnswers

        quickAnswers.removeAt(index)
        quickAnswers.add(index, text)

        config.quickAnswers = quickAnswers
    }

    private fun setupCallsExport() {
        binding.settingsExportCallsHolder.setOnClickListener {
            ExportCallHistoryDialog(this) { filename ->
                saveDocument.launch("$filename.json")
            }
        }
    }

    private fun setupCallsImport() {
        binding.settingsImportCallsHolder.setOnClickListener {
            getContent.launch(IMPORT_CALL_HISTORY_FILE_TYPES.toTypedArray())
        }
    }

    private fun importCallHistory(uri: Uri) {
        try {
            val jsonString = contentResolver.openInputStream(uri)!!.use { inputStream ->
                inputStream.bufferedReader().readText()
            }

            val objects = Json.decodeFromString<List<RecentCall>>(jsonString)

            if (objects.isEmpty()) {
                toast(R.string.no_entries_for_importing)
                return
            }

            RecentsHelper(this).restoreRecentCalls(this, objects) {
                toast(R.string.importing_successful)
            }
        } catch (_: SerializationException) {
            toast(R.string.invalid_file_format)
        } catch (_: IllegalArgumentException) {
            toast(R.string.invalid_file_format)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun exportCallHistory(recents: List<RecentCall>, uri: Uri) {
        if (recents.isEmpty()) {
            toast(R.string.no_entries_for_exporting)
        } else {
            try {
                val outputStream = contentResolver.openOutputStream(uri)!!

                val jsonString = Json.encodeToString(recents)
                outputStream.use {
                    it.write(jsonString.toByteArray())
                }
                toast(R.string.exporting_successful)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun setupFlashForAlerts() {
        binding.apply {
            settingsFlashForAlerts.isChecked = config.flashForAlerts
            settingsFlashForAlertsHolder.setOnClickListener {
                settingsFlashForAlerts.toggle()
                config.flashForAlerts = settingsFlashForAlerts.isChecked
            }
        }
    }

    private fun setupHideDialpadLetters() {
        binding.apply {
            settingsHideDialpadLetters.isChecked = config.hideDialpadLetters
            settingsHideDialpadLettersHolder.setOnClickListener {
                settingsHideDialpadLetters.toggle()
                config.hideDialpadLetters = settingsHideDialpadLetters.isChecked
            }
        }
    }

    private fun setupDialpadNumbers() {
        binding.apply {
            settingsHideDialpadNumbers.isChecked = config.hideDialpadNumbers
            settingsHideDialpadNumbersHolder.setOnClickListener {
                settingsHideDialpadNumbers.toggle()
                config.hideDialpadNumbers = settingsHideDialpadNumbers.isChecked
            }
        }
    }

    private fun setupGroupCalls() {
        binding.settingsGroupCalls.text = getGroupCallsText()
        binding.settingsGroupCallsHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(GROUP_CALLS_NO, getString(R.string.no)),
                RadioItem(GROUP_CALLS_SUBSEQUENT, getString(R.string.group_subsequent_calls)),
                RadioItem(GROUP_CALLS_ALL, getString(R.string.group_all_calls))
            )

            RadioGroupDialog(
                this@SettingsActivity,
                items,
                getGroupCallsCheckedItemId(),
                R.string.group_calls,
                defaultItemId = GROUP_CALLS_SUBSEQUENT
            ) {
                when(it) {
                    GROUP_CALLS_SUBSEQUENT -> {
                        config.groupSubsequentCalls = true
                        config.groupAllCalls = false
                    }
                    GROUP_CALLS_ALL -> {
                        config.groupSubsequentCalls = false
                        config.groupAllCalls = true
                    }
                    else -> {
                        config.groupSubsequentCalls = false
                        config.groupAllCalls = false
                    }
                }
                binding.settingsGroupCalls.text = getGroupCallsText()
            }
        }
    }

    private fun getGroupCallsText() = getString(
        when {
            config.groupSubsequentCalls -> R.string.group_subsequent_calls
            config.groupAllCalls -> R.string.group_all_calls
            else -> com.goodwy.commons.R.string.no
        }
    )

    private fun getGroupCallsCheckedItemId(): Int {
        return when {
            config.groupSubsequentCalls -> GROUP_CALLS_SUBSEQUENT
            config.groupAllCalls -> GROUP_CALLS_ALL
            else -> GROUP_CALLS_NO
        }
    }


    private fun setupGroupSubsequentCalls() {
        binding.apply {
            settingsGroupSubsequentCalls.isChecked = config.groupSubsequentCalls
            settingsGroupSubsequentCallsHolder.setOnClickListener {
                settingsGroupSubsequentCalls.toggle()
                config.groupSubsequentCalls = settingsGroupSubsequentCalls.isChecked
            }
        }
    }

//    private fun setupGroupCallsByDate() {
//        binding.apply {
//            settingsGroupCallsByDate.isChecked = config.groupCallsByDate
//            settingsGroupCallsByDateHolder.setOnClickListener {
//                settingsGroupCallsByDate.toggle()
//                config.groupCallsByDate = settingsGroupCallsByDate.isChecked
//            }
//        }
//    }

    private fun setupQueryLimitRecent() {
        binding.settingsQueryLimitRecent.text = getQueryLimitRecentText()
        binding.settingsQueryLimitRecentHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(QUERY_LIMIT_TINY_VALUE, QUERY_LIMIT_TINY_VALUE.toString()),
                RadioItem(QUERY_LIMIT_SMALL_VALUE, QUERY_LIMIT_SMALL_VALUE.toString()),
                RadioItem(QUERY_LIMIT_MEDIUM_VALUE, QUERY_LIMIT_MEDIUM_VALUE.toString()),
                RadioItem(QUERY_LIMIT_NORMAL_VALUE, QUERY_LIMIT_NORMAL_VALUE.toString()),
                RadioItem(QUERY_LIMIT_BIG_VALUE, QUERY_LIMIT_BIG_VALUE.toString()),
                RadioItem(QUERY_LIMIT_MAX_VALUE, "MAX"))

            RadioGroupDialog(
                this@SettingsActivity,
                items,
                config.queryLimitRecent,
                R.string.number_of_recent_calls_displays,
                defaultItemId = QUERY_LIMIT_MEDIUM_VALUE
            ) {
                config.queryLimitRecent = it as Int
                binding.settingsQueryLimitRecent.text = getQueryLimitRecentText()
            }
        }

        binding.settingsQueryLimitRecentFaq.imageTintList = ColorStateList.valueOf(getProperTextColor())
        binding.settingsQueryLimitRecentFaq.setOnClickListener {
            ConfirmationDialog(this@SettingsActivity, messageId = R.string.number_of_recent_calls_displays_summary, positive = com.goodwy.commons.R.string.ok, negative = 0) {}
        }
    }

    private fun getQueryLimitRecentText(): String {
        return if (config.queryLimitRecent == QUERY_LIMIT_MAX_VALUE) "MAX" else config.queryLimitRecent.toString()
    }

    private fun setupStartNameWithSurname() {
        binding.apply {
            settingsStartNameWithSurname.isChecked = config.startNameWithSurname
            settingsStartNameWithSurnameHolder.setOnClickListener {
                settingsStartNameWithSurname.toggle()
                config.startNameWithSurname = settingsStartNameWithSurname.isChecked
            }
        }
    }

    private fun setupShowNicknameInsteadNames() {
        binding.apply {
            settingsShowNicknameInsteadNames.isChecked = config.showNicknameInsteadNames
            settingsShowNicknameInsteadNamesHolder.setOnClickListener {
                settingsShowNicknameInsteadNames.toggle()
                config.showNicknameInsteadNames = settingsShowNicknameInsteadNames.isChecked
                config.needRestart = true
            }
        }
    }

    private fun setupFormatPhoneNumbers() {
        binding.settingsFormatPhoneNumbers.isChecked = config.formatPhoneNumbers
        binding.settingsFormatPhoneNumbersHolder.setOnClickListener {
            binding.settingsFormatPhoneNumbers.toggle()
            config.formatPhoneNumbers = binding.settingsFormatPhoneNumbers.isChecked
            config.needRestart = true
        }
    }

    private fun setupShowCallConfirmation() {
        binding.apply {
            settingsShowCallConfirmation.isChecked = config.showCallConfirmation
            settingsShowCallConfirmationHolder.setOnClickListener {
                settingsShowCallConfirmation.toggle()
                config.showCallConfirmation = settingsShowCallConfirmation.isChecked
            }
        }
    }

    private fun setupCallUsingSameSim() {
        binding.apply {
            settingsCallFromSameSimHolder.beVisibleIf(areMultipleSIMsAvailable())
            settingsCallFromSameSim.isChecked = config.callUsingSameSim
            settingsCallFromSameSimHolder.setOnClickListener {
                settingsCallFromSameSim.toggle()
                config.callUsingSameSim = settingsCallFromSameSim.isChecked
            }
        }
    }

    private fun setupCallBlockButton() {
        binding.apply {
            settingsCallBlockButton.isChecked = config.callBlockButton
            settingsCallBlockButtonHolder.setOnClickListener {
                settingsCallBlockButton.toggle()
                config.callBlockButton = settingsCallBlockButton.isChecked
            }
        }
    }

    private fun setupSimDialogStyle() {
        binding.settingsSimDialogStyleHolder.beGoneIf(!areMultipleSIMsAvailable())
        binding.settingsSimDialogStyle.text = getSimDialogStyleText()
        binding.settingsSimDialogStyleHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SIM_DIALOG_STYLE_LIST, getString(R.string.list)),
                RadioItem(SIM_DIALOG_STYLE_BUTTON, getString(R.string.buttons)))

            RadioGroupDialog(this@SettingsActivity, items, config.simDialogStyle, R.string.sim_card_selection_dialog_style) {
                config.simDialogStyle = it as Int
                binding.settingsSimDialogStyle.text = getSimDialogStyleText()
            }
        }
    }

    private fun getSimDialogStyleText() = getString(
        when (config.simDialogStyle) {
            SIM_DIALOG_STYLE_LIST -> R.string.list
            else -> R.string.buttons
        }
    )

    private fun setupOverflowIcon() {
        binding.apply {
            settingsOverflowIcon.applyColorFilter(getProperTextColor())
            settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
            settingsOverflowIconHolder.setOnClickListener {
                val items = arrayListOf(
                    com.goodwy.commons.R.drawable.ic_more_horiz,
                    com.goodwy.commons.R.drawable.ic_three_dots_vector,
                    com.goodwy.commons.R.drawable.ic_more_horiz_round
                )

                IconListDialog(
                    activity = this@SettingsActivity,
                    items = items,
                    checkedItemId = baseConfig.overflowIcon + 1,
                    defaultItemId = OVERFLOW_ICON_HORIZONTAL + 1,
                    titleId = com.goodwy.strings.R.string.overflow_icon,
                    size = pixels(com.goodwy.commons.R.dimen.normal_icon_size).toInt(),
                    color = getProperTextColor()
                ) { wasPositivePressed, newValue ->
                    if (wasPositivePressed) {
                        if (baseConfig.overflowIcon != newValue - 1) {
                            baseConfig.overflowIcon = newValue - 1
                            settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
                        }
                    }
                }
            }
        }
    }

    private fun setupFloatingButtonStyle() {
        binding.apply {
            settingsFloatingButtonStyle.applyColorFilter(getProperTextColor())
            settingsFloatingButtonStyle.setImageResource(
                if (baseConfig.materialDesign3) R.drawable.squircle_bg else R.drawable.ic_circle_filled
            )
            settingsFloatingButtonStyleHolder.setOnClickListener {
                val items = arrayListOf(
                    R.drawable.ic_circle_filled,
                    R.drawable.squircle_bg
                )

                IconListDialog(
                    activity = this@SettingsActivity,
                    items = items,
                    checkedItemId = if (baseConfig.materialDesign3) 2 else 1,
                    defaultItemId = 1,
                    titleId = com.goodwy.strings.R.string.floating_button_style,
                    size = pixels(com.goodwy.commons.R.dimen.normal_icon_size).toInt(),
                    color = getProperTextColor()
                ) { wasPositivePressed, newValue ->
                    if (wasPositivePressed) {
                        if (newValue != if (baseConfig.materialDesign3) 2 else 1) {
                            baseConfig.materialDesign3 = newValue == 2
                            settingsFloatingButtonStyle.setImageResource(
                                if (newValue == 2) R.drawable.squircle_bg else R.drawable.ic_circle_filled
                            )
                            config.needRestart = true
                        }
                    }
                }
            }
        }
    }

    private fun setupColorSimIcons() {
        binding.apply {
            settingsColorSimCardIconsHolder.beGoneIf(!areMultipleSIMsAvailable())
            settingsColorSimCardIcons.isChecked = config.colorSimIcons
            settingsColorSimCardIconsHolder.setOnClickListener {
                settingsColorSimCardIcons.toggle()
                config.colorSimIcons = settingsColorSimCardIcons.isChecked
            }
        }
    }

    private fun setupSimCardColorList() {
        binding.apply {
            initSimCardColor()

            val pro = checkPro()
            val simList = getAvailableSIMCardLabels()
            if (simList.isNotEmpty()) {
                if (simList.size == 1) {
                    val sim1 = simList[0].label
                    settingsSimCardColor1Label.text = if (pro) sim1 else sim1 + " (${getString(R.string.feature_locked)})"
                } else {
                    val sim1 = simList[0].label
                    val sim2 = simList[1].label
                    settingsSimCardColor1Label.text = if (pro) sim1 else sim1 + " (${getString(R.string.feature_locked)})"
                    settingsSimCardColor2Label.text = if (pro) sim2 else sim2 + " (${getString(R.string.feature_locked)})"
                }
            }

            if (pro) {
                settingsSimCardColor1Holder.setOnClickListener {
                    ColorPickerDialog(
                        this@SettingsActivity,
                        color = config.simIconsColors[1],
                        addDefaultColorButton = true,
                        colorDefault = resources.getColor(R.color.ic_dialer, theme),
                        title = resources.getString(R.string.color_sim_card_icons)
                    ) { wasPositivePressed, color, wasDefaultPressed ->
                        if (wasPositivePressed || wasDefaultPressed) {
                            if (hasColorChanged(config.simIconsColors[1], color)) {
                                addSimCardColor(1, color)
                                initSimCardColor()
                            }
                        }
                    }
                }
                settingsSimCardColor2Holder.setOnClickListener {
                    ColorPickerDialog(
                        this@SettingsActivity,
                        color = config.simIconsColors[2],
                        addDefaultColorButton = true,
                        colorDefault = resources.getColor(R.color.color_primary, theme),
                        title = resources.getString(R.string.color_sim_card_icons)
                    ) { wasPositivePressed, color, wasDefaultPressed ->
                        if (wasPositivePressed || wasDefaultPressed) {
                            if (hasColorChanged(config.simIconsColors[2], color)) {
                                addSimCardColor(2, color)
                                initSimCardColor()
                            }
                        }
                    }
                }
            } else {
                arrayOf(
                    settingsSimCardColor1Holder,
                    settingsSimCardColor2Holder
                ).forEach {
                    it.setOnClickListener { view ->
                        RxAnimation.from(binding.settingsPurchaseThankYouHolder)
                            .shake()
                            .subscribe()

                        RxAnimation.from(view)
                            .shake(shakeTranslation = 2f)
                            .subscribe()

                        showSnackbar(binding.root)
                    }
                }
            }
        }
    }

    private fun initSimCardColor() {
        binding.apply {
            val pro = checkPro()
            arrayOf(
                settingsSimCardColor1Holder,
                settingsSimCardColor2Holder
            ).forEach {
                it.alpha = if (pro) 1f else 0.4f
            }
            val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
            settingsSimCardColor2Holder.beVisibleIf(areMultipleSIMsAvailable)
            if (areMultipleSIMsAvailable) settingsSimCardColor1Icon.setImageResource(R.drawable.ic_phone_one_vector)
            settingsSimCardColor1Icon.background.setTint(config.simIconsColors[1])
            settingsSimCardColor2Icon.background.setTint(config.simIconsColors[2])
            settingsSimCardColor1Icon.setColorFilter(config.simIconsColors[1].getContrastColor())
            settingsSimCardColor2Icon.setColorFilter(config.simIconsColors[2].getContrastColor())
        }
    }

    private fun addSimCardColor(index: Int, color: Int) {
        val recentColors = config.simIconsColors

        recentColors.removeAt(index)
        recentColors.add(index, color)

        val needUpdate = baseConfig.simIconsColors != recentColors
        baseConfig.simIconsColors = recentColors

        if (needUpdate) {
            val recents = config.parseRecentCallsCache()
            val recentsNew = mutableListOf<RecentCall>()
            recents.forEach { recent ->
                val recentNew = if (recent.simID == index) recent.copy(simColor = color) else recent
                recentsNew.add(recentNew)
            }
            config.recentCallsCache = Gson().toJson(recentsNew.take(RECENT_CALL_CACHE_SIZE))
            config.needUpdateRecents = true
            config.needRestart = true
        }
    }

    private fun hasColorChanged(old: Int, new: Int) = abs(old - new) > 1

    private fun setupDialpadStyle() {
        binding.settingsDialpadStyleHolder.setOnClickListener {
            startActivity(Intent(applicationContext, SettingsDialpadActivity::class.java))
        }
    }

    private fun setupShowRecentCallsOnDialpad() {
        binding.apply {
            settingsShowRecentCallsOnDialpad.isChecked = config.showRecentCallsOnDialpad
            settingsShowRecentCallsOnDialpadHolder.setOnClickListener {
                settingsShowRecentCallsOnDialpad.toggle()
                config.showRecentCallsOnDialpad = settingsShowRecentCallsOnDialpad.isChecked
            }
        }
    }

    private fun setupOpenSearch() {
        binding.apply {
            settingsOpenSearch.isChecked = config.openSearch
            settingsOpenSearchHolder.setOnClickListener {
                settingsOpenSearch.toggle()
                config.openSearch = settingsOpenSearch.isChecked
            }
        }
    }

    private fun setupEndSearch() {
        binding.apply {
            settingsEndSearch.isChecked = config.closeSearch
            settingsEndSearchHolder.setOnClickListener {
                settingsEndSearch.toggle()
                config.closeSearch = settingsEndSearch.isChecked
            }
        }
    }

    private fun setupOnFavoriteClick() = binding.apply {
        settingsOnFavoriteClick.text = getOnRecentOrContactClickText(config.onFavoriteClick, true)
        settingsOnFavoriteClickHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SWIPE_ACTION_CALL, getString(R.string.call), icon = R.drawable.ic_phone_vector),
                RadioItem(SWIPE_ACTION_MESSAGE, getString(R.string.send_sms), icon = R.drawable.ic_messages),
                RadioItem(SWIPE_ACTION_OPEN, getString(R.string.view_contact_details), icon = R.drawable.ic_info),
                RadioItem(SWIPE_ACTION_EDIT, getString(R.string.edit), icon = com.goodwy.commons.R.drawable.ic_edit_vector),
                RadioItem(SWIPE_ACTION_NONE, getString(com.goodwy.commons.R.string.nothing)),
            )

            RadioGroupIconDialog(
                this@SettingsActivity,
                items,
                config.onFavoriteClick,
                R.string.on_favorite_click,
                defaultItemId = SWIPE_ACTION_CALL
            ) {
                config.onFavoriteClick = it as Int
                config.needRestart = true
                settingsOnFavoriteClick.text = getOnRecentOrContactClickText(config.onFavoriteClick, true)
            }
        }
    }

    private fun setupOnRecentClick() = binding.apply {
        settingsOnRecentClick.text = getOnRecentOrContactClickText(config.onRecentClick, false)
        settingsOnRecentClickHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SWIPE_ACTION_CALL, getString(R.string.call), icon = R.drawable.ic_phone_vector),
                RadioItem(SWIPE_ACTION_MESSAGE, getString(R.string.send_sms), icon = R.drawable.ic_messages),
                RadioItem(SWIPE_ACTION_OPEN, getString(R.string.show_call_details), icon = R.drawable.ic_info),
                RadioItem(SWIPE_ACTION_NONE, getString(com.goodwy.commons.R.string.nothing)),
            )

            RadioGroupIconDialog(
                this@SettingsActivity,
                items,
                config.onRecentClick,
                R.string.on_recent_click,
                defaultItemId = SWIPE_ACTION_CALL
            ) {
                config.onRecentClick = it as Int
                config.needRestart = true
                settingsOnRecentClick.text = getOnRecentOrContactClickText(config.onRecentClick, false)
            }
        }
    }

    private fun setupOnContactClick() = binding.apply {
        settingsOnContactClick.text = getOnRecentOrContactClickText(config.onContactClick, true)
        settingsOnContactClickHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SWIPE_ACTION_CALL, getString(R.string.call), icon = R.drawable.ic_phone_vector),
                RadioItem(SWIPE_ACTION_MESSAGE, getString(R.string.send_sms), icon = R.drawable.ic_messages),
                RadioItem(SWIPE_ACTION_OPEN, getString(R.string.view_contact_details), icon = R.drawable.ic_info),
                RadioItem(SWIPE_ACTION_EDIT, getString(R.string.edit), icon = com.goodwy.commons.R.drawable.ic_edit_vector),
                RadioItem(SWIPE_ACTION_NONE, getString(com.goodwy.commons.R.string.nothing)),
            )

            RadioGroupIconDialog(
                this@SettingsActivity,
                items,
                config.onContactClick,
                R.string.on_contact_click,
                defaultItemId = SWIPE_ACTION_CALL
            ) {
                config.onContactClick = it as Int
                config.needRestart = true
                settingsOnContactClick.text = getOnRecentOrContactClickText(config.onContactClick, true)
            }
        }
    }

    private fun getOnRecentOrContactClickText(currentConfig: Int, isContact: Boolean) = getString(
        when (currentConfig) {
            SWIPE_ACTION_CALL -> R.string.call
            SWIPE_ACTION_MESSAGE -> R.string.send_sms
            SWIPE_ACTION_OPEN -> if (isContact) R.string.view_contact_details else R.string.show_call_details
            SWIPE_ACTION_EDIT -> R.string.edit
            else -> com.goodwy.commons.R.string.nothing
        }
    )

    private fun setupUseSwipeToAction() {
        updateSwipeToActionVisible()
        binding.apply {
            settingsUseSwipeToAction.isChecked = config.useSwipeToAction
            settingsUseSwipeToActionHolder.setOnClickListener {
                settingsUseSwipeToAction.toggle()
                config.useSwipeToAction = settingsUseSwipeToAction.isChecked
                config.needRestart = true
                updateSwipeToActionVisible()
            }
        }
    }

    private fun updateSwipeToActionVisible() {
        binding.apply {
            settingsSwipeVibrationHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeRippleHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeRightActionHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeLeftActionHolder.beVisibleIf(config.useSwipeToAction)
            settingsSkipDeleteConfirmationHolder.beVisibleIf(config.useSwipeToAction &&(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE))
        }
    }

    private fun setupSwipeVibration() {
        binding.apply {
            settingsSwipeVibration.isChecked = config.swipeVibration
            settingsSwipeVibrationHolder.setOnClickListener {
                settingsSwipeVibration.toggle()
                config.swipeVibration = settingsSwipeVibration.isChecked
                config.needRestart = true
            }
        }
    }

    private fun setupSwipeRipple() {
        binding.apply {
            settingsSwipeRipple.isChecked = config.swipeRipple
            settingsSwipeRippleHolder.setOnClickListener {
                settingsSwipeRipple.toggle()
                config.swipeRipple = settingsSwipeRipple.isChecked
                config.needRestart = true
            }
        }
    }

    private fun setupSwipeRightAction() = binding.apply {
        if (isRTLLayout) settingsSwipeRightActionLabel.text = getString(R.string.swipe_left_action)
        settingsSwipeRightAction.text = getSwipeActionText(false)
        settingsSwipeRightActionHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SWIPE_ACTION_DELETE, getString(com.goodwy.commons.R.string.delete), icon = com.goodwy.commons.R.drawable.ic_delete_outline),
                RadioItem(SWIPE_ACTION_BLOCK, getString(R.string.block_number), icon = R.drawable.ic_block_vector),
                RadioItem(SWIPE_ACTION_CALL, getString(R.string.call), icon = R.drawable.ic_phone_vector),
                RadioItem(SWIPE_ACTION_MESSAGE, getString(R.string.send_sms), icon = R.drawable.ic_messages),
                RadioItem(SWIPE_ACTION_NONE, getString(com.goodwy.commons.R.string.nothing)),
            )

            val title =
                if (isRTLLayout) R.string.swipe_left_action else R.string.swipe_right_action
            RadioGroupIconDialog(
                this@SettingsActivity,
                items,
                config.swipeRightAction,
                title,
                defaultItemId = SWIPE_ACTION_MESSAGE
            ) {
                config.swipeRightAction = it as Int
                config.needRestart = true
                settingsSwipeRightAction.text = getSwipeActionText(false)
                settingsSkipDeleteConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE)
            }
        }
    }

    private fun setupSwipeLeftAction() = binding.apply {
        val pro = checkPro()
        settingsSwipeLeftActionHolder.alpha = if (pro) 1f else 0.4f
        val stringId = if (isRTLLayout) R.string.swipe_right_action else R.string.swipe_left_action
        settingsSwipeLeftActionLabel.text = addLockedLabelIfNeeded(stringId, pro)
        settingsSwipeLeftAction.text = getSwipeActionText(true)
        settingsSwipeLeftActionHolder.setOnClickListener {
            if (pro) {
                val items = arrayListOf(
                    RadioItem(SWIPE_ACTION_DELETE, getString(com.goodwy.commons.R.string.delete), icon = com.goodwy.commons.R.drawable.ic_delete_outline),
                    RadioItem(SWIPE_ACTION_BLOCK, getString(R.string.block_number), icon = R.drawable.ic_block_vector),
                    RadioItem(SWIPE_ACTION_CALL, getString(R.string.call), icon = R.drawable.ic_phone_vector),
                    RadioItem(SWIPE_ACTION_MESSAGE, getString(R.string.send_sms), icon = R.drawable.ic_messages),
                    RadioItem(SWIPE_ACTION_NONE, getString(com.goodwy.commons.R.string.nothing)),
                )

                val title =
                    if (isRTLLayout) R.string.swipe_right_action else R.string.swipe_left_action
                RadioGroupIconDialog(
                    this@SettingsActivity,
                    items,
                    config.swipeLeftAction,
                    title,
                    defaultItemId = SWIPE_ACTION_DELETE
                ) {
                    config.swipeLeftAction = it as Int
                    config.needRestart = true
                    settingsSwipeLeftAction.text = getSwipeActionText(true)
                    settingsSkipDeleteConfirmationHolder.beVisibleIf(
                        config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE
                    )
                }
            } else {
                RxAnimation.from(settingsSwipeLeftActionHolder)
                    .shake(shakeTranslation = 2f)
                    .subscribe()

                showSnackbar(binding.root)
            }
        }
    }

    private fun getSwipeActionText(left: Boolean) = getString(
        when (if (left) config.swipeLeftAction else config.swipeRightAction) {
            SWIPE_ACTION_DELETE -> com.goodwy.commons.R.string.delete
            SWIPE_ACTION_BLOCK -> R.string.block_number
            SWIPE_ACTION_CALL -> R.string.call
            SWIPE_ACTION_MESSAGE -> R.string.send_sms
            else -> com.goodwy.commons.R.string.nothing
        }
    )

    private fun setupDeleteConfirmation() {
        binding.apply {
            //settingsSkipDeleteConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE)
            settingsSkipDeleteConfirmation.isChecked = config.skipDeleteConfirmation
            settingsSkipDeleteConfirmationHolder.setOnClickListener {
                settingsSkipDeleteConfirmation.toggle()
                config.skipDeleteConfirmation = settingsSkipDeleteConfirmation.isChecked
            }
        }
    }

    private fun setupTipJar() = binding.apply {
        settingsTipJarHolder.apply {
            beVisibleIf(checkPro(false))
            background.applyColorFilter(getColoredMaterialStatusBarColor())
            setOnClickListener {
                launchPurchase()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupAbout() = binding.apply {
        settingsAboutVersion.text = "Version: " + BuildConfig.VERSION_NAME
        settingsAboutHolder.setOnClickListener {
            launchAbout()
        }
    }

    private fun setupDisableProximitySensor() {
        binding.apply {
            settingsDisableProximitySensor.isChecked = config.disableProximitySensor
            settingsDisableProximitySensorHolder.setOnClickListener {
                settingsDisableProximitySensor.toggle()
                config.disableProximitySensor = settingsDisableProximitySensor.isChecked
            }
        }
    }

    private fun setupDialpadVibrations() {
        binding.apply {
            settingsCallVibration.isChecked = config.callVibration
            settingsCallVibrationHolder.setOnClickListener {
                settingsCallVibration.toggle()
                config.callVibration = settingsCallVibration.isChecked
            }
        }
    }

    private fun setupCallStartEndVibrations() {
        binding.apply {
            settingsCallStartEndVibration.isChecked = config.callStartEndVibration
            settingsCallStartEndVibrationHolder.setOnClickListener {
                settingsCallStartEndVibration.toggle()
                config.callStartEndVibration = settingsCallStartEndVibration.isChecked
            }
        }
    }

    private fun setupDialpadBeeps() {
        binding.apply {
            settingsDialpadBeeps.isChecked = config.dialpadBeeps
            settingsDialpadBeepsHolder.setOnClickListener {
                settingsDialpadBeeps.toggle()
                config.dialpadBeeps = settingsDialpadBeeps.isChecked
            }
        }
    }

    private fun setupBlockCallFromAnotherApp() {
        binding.apply {
            settingsBlockCallFromAnotherApp.isChecked = config.blockCallFromAnotherApp
            settingsBlockCallFromAnotherAppHolder.setOnClickListener {
                settingsBlockCallFromAnotherApp.toggle()
                config.blockCallFromAnotherApp = settingsBlockCallFromAnotherApp.isChecked
            }
            settingsBlockCallFromAnotherAppFaq.imageTintList = ColorStateList.valueOf(getProperTextColor())
            settingsBlockCallFromAnotherAppFaq.setOnClickListener {
                ConfirmationDialog(this@SettingsActivity, messageId = R.string.open_dialpad_when_call_from_another_app_summary, positive = com.goodwy.commons.R.string.ok, negative = 0) {}
            }
        }
    }

    private fun setupDisableSwipeToAnswer() {
        binding.apply {
            settingsDisableSwipeToAnswer.isChecked = config.disableSwipeToAnswer
            settingsDisableSwipeToAnswerHolder.setOnClickListener {
                settingsDisableSwipeToAnswer.toggle()
                config.disableSwipeToAnswer = settingsDisableSwipeToAnswer.isChecked
            }
        }
    }

    private fun setupUseRelativeDate() {
        binding.apply {
            settingsRelativeDate.isChecked = config.useRelativeDate
            settingsRelativeDateHolder.setOnClickListener {
                settingsRelativeDate.toggle()
                config.useRelativeDate = settingsRelativeDate.isChecked
                config.needRestart = true
            }
        }
    }

    private fun checkPro(collection: Boolean = true) =
        if (collection) isOrWasThankYouInstalled() || isPro() || isCollection()
        else isOrWasThankYouInstalled() || isPro()
}
