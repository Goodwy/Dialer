package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import com.goodwy.commons.activities.ManageBlockedNumbersActivity
import com.goodwy.commons.dialogs.*
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.Release
import com.goodwy.commons.models.SimpleListItem
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.databinding.ActivitySettingsBinding
import com.goodwy.dialer.dialogs.ExportCallHistoryDialog
import com.goodwy.dialer.dialogs.ManageVisibleTabsDialog
import com.goodwy.dialer.extensions.areMultipleSIMsAvailable
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getAvailableSIMCardLabels
import com.goodwy.dialer.helpers.RecentsHelper
import com.goodwy.dialer.models.RecentCall
import com.goodwy.dialer.helpers.*
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.math.abs
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    companion object {
        private const val CALL_HISTORY_FILE_TYPE = "application/json"
    }

    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3

    private val purchaseHelper = PurchaseHelper(this)
    private val binding by viewBinding(ActivitySettingsBinding::inflate)
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            toast(R.string.importing)
            importCallHistory(uri)
        }
    }

    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument(CALL_HISTORY_FILE_TYPE)) { uri ->
        if (uri != null) {
            toast(R.string.exporting)
            RecentsHelper(this).getRecentCalls(false, Int.MAX_VALUE) { recents ->
                exportCallHistory(recents, uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            updateMaterialActivityViews(settingsCoordinator, settingsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(settingsNestedScrollview, settingsToolbar)
//            // TODO TRANSPARENT Navigation Bar
//            if (config.transparentNavigationBar) {
//                setWindowTransparency(true) { _, _, leftNavigationBarSize, rightNavigationBarSize ->
//                    settingsCoordinator.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
//                    updateNavigationBarColor(getProperBackgroundColor())
//                }
//            }
        }

        if (isPlayStoreInstalled()) {
            //PlayStore
            purchaseHelper.initBillingClient()
            val iapList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
            val subList: ArrayList<String> = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3)
            purchaseHelper.retrieveDonation(iapList, subList)

            purchaseHelper.isIapPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isPro = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isPro = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }

            purchaseHelper.isSupPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isProSubs = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isProSubs = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()

        setupCustomizeColors()
        setupDialPadOpen()
        setupMaterialDesign3()
        setupOverflowIcon()
        setupUseColoredContacts()
        setupContactsColorList()
        setupColorSimIcons()
        setupSimCardColorList()
        setupDialpadStyle()

        setupDefaultTab()
        setupManageShownTabs()
        setupNavigationBarStyle()
        setupUseIconTabs()
        setupScreenSlideAnimation()
        setupOpenSearch()

        setupCallsExport()
        setupCallsImport()
        setupManageBlockedNumbers()
        setupManageSpeedDial()
        setupChangeDateTimeFormat()
        setupFontSize()
        setupBackgroundCallScreen()
        setupTransparentCallScreen()
        setupAnswerStyle()
        setupCallerDescription()
        setupAlwaysShowFullscreen()
        setupMissedCallNotifications()
        setupFlashForAlerts()
        setupHideDialpadLetters()
        setupDialpadNumbers()
        setupGroupSubsequentCalls()

        setupShowDividers()
        setupShowContactThumbnails()
        setupShowPhoneNumbers()
        setupStartNameWithSurname()

        setupShowCallConfirmation()
        setupUseEnglish()
        setupLanguage()
        setupDisableProximitySensor()
        setupDialpadVibrations()
        setupCallStartEndVibrations()
        setupDialpadBeeps()
        setupDisableSwipeToAnswer()
        setupUseRelativeDate()

        setupTipJar()
        setupAbout()

        setupOptionsMenu()

        updateTextColors(binding.settingsHolder)

        binding.apply {
            arrayOf(
                settingsAppearanceLabel,
                settingsTabsLabel,
                settingsGeneralLabel,
                settingsDialpadLabel,
                settingsCallsLabel,
                settingsListViewLabel,
                settingsOtherLabel).forEach {
                it.setTextColor(getProperPrimaryColor())
            }

            arrayOf(
                settingsColorCustomizationHolder,
                settingsTabsHolder,
                settingsGeneralHolder,
                settingsDialpadHolder,
                settingsCallsHolder,
                settingsListViewHolder,
                settingsOtherHolder
            ).forEach {
                it.background.applyColorFilter(getBottomNavigationBackgroundColor())
            }

            arrayOf(
                settingsCustomizeColorsChevron,
                settingsManageShownTabsChevron,
                settingsExportCallsChevron,
                settingsImportCallsChevron,
                settingsManageBlockedNumbersChevron,
                settingsManageSpeedDialChevron,
                settingsChangeDateTimeFormatChevron,
                settingsTipJarChevron,
                settingsAboutChevron,
                settingsDialpadStyleChevron
            ).forEach {
                it.applyColorFilter(getProperTextColor())
            }
        }
    }

    private fun updatePro(isPro: Boolean = isPro() || isOrWasThankYouInstalled() || isCollection()) {
        binding.apply {
            settingsPurchaseThankYouHolder.beGoneIf(isPro)
            settingsCustomizeColorsLabel.text = if (isPro) {
                getString(R.string.customize_colors)
            } else {
                getString(R.string.customize_colors_locked)
            }
            settingsTipJarHolder.beVisibleIf(isPro)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupPurchaseThankYou() {
        binding.apply {
            settingsPurchaseThankYouHolder.beGoneIf(isOrWasThankYouInstalled() || isPro())
            settingsPurchaseThankYouHolder.setOnClickListener {
                launchPurchase()
            }
            moreButton.setOnClickListener {
                launchPurchase()
            }
            val appDrawable = resources.getColoredDrawableWithColor(this@SettingsActivity, R.drawable.ic_plus_support, getProperPrimaryColor())
            purchaseLogo.setImageDrawable(appDrawable)
            val drawable = resources.getColoredDrawableWithColor(this@SettingsActivity, R.drawable.button_gray_bg, getProperPrimaryColor())
            moreButton.background = drawable
            moreButton.setTextColor(getProperBackgroundColor())
            moreButton.setPadding(2, 2, 2, 2)
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsCustomizeColorsLabel.text = if (isOrWasThankYouInstalled() || isPro() || isCollection()) {
            getString(R.string.customize_colors)
        } else {
            getString(R.string.customize_colors_locked)
        }
        binding.settingsCustomizeColorsHolder.setOnClickListener {
            startCustomizationActivity(
                true,
                isCollection = isOrWasThankYouInstalled() || isCollection(),
                licensingKey = BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
                productIdX1 = productIdX1,
                productIdX2 = productIdX2,
                productIdX3 = productIdX3,
                subscriptionIdX1 = subscriptionIdX1,
                subscriptionIdX2 = subscriptionIdX2,
                subscriptionIdX3 = subscriptionIdX3,
                playStoreInstalled = isPlayStoreInstalled()
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

    private fun setupLanguage() {
        binding.apply {
            settingsLanguage.text = Locale.getDefault().displayLanguage
            settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
            settingsLanguageHolder.setOnClickListener {
                launchChangeAppLanguageIntent()
            }
        }
    }

    // support for device-wise blocking came on Android 7, rely only on that
    @TargetApi(Build.VERSION_CODES.N)
    private fun setupManageBlockedNumbers() {
        binding.settingsManageBlockedNumbersHolder.beVisibleIf(isNougatPlus())
        binding.settingsManageBlockedNumbersCount.text = getBlockedNumbers().size.toString()

        val getProperTextColor = getProperTextColor()
        val red = resources.getColor(R.color.red_missed)
        val colorUnknown = if (baseConfig.blockUnknownNumbers) red else getProperTextColor
        val alphaUnknown = if (baseConfig.blockUnknownNumbers) 1f else 0.6f
        binding.settingsManageBlockedNumbersIconUnknown.apply {
            applyColorFilter(colorUnknown)
            alpha = alphaUnknown
        }

        val colorHidden = if (baseConfig.blockHiddenNumbers) red else getProperTextColor
        val alphaHidden = if (baseConfig.blockHiddenNumbers) 1f else 0.6f
        binding.settingsManageBlockedNumbersIconHidden.apply {
            applyColorFilter(colorHidden)
            alpha = alphaHidden
        }

        binding.settingsManageBlockedNumbersHolder.setOnClickListener {
            Intent(this@SettingsActivity, ManageBlockedNumbersActivity::class.java).apply {
                startActivity(this)
            }
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
        binding.settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupFontSize() {
        binding.settingsFontSize.text = getFontSizeText()
        binding.settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large)))

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                binding.settingsFontSize.text = getFontSizeText()
                config.tabsChanged = true
            }
        }
    }

    private fun setupDefaultTab() {
        binding.settingsDefaultTab.text = getDefaultTabText()
        binding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_LAST_USED, getString(R.string.last_used_tab)),
                RadioItem(TAB_FAVORITES, getString(R.string.favorites_tab)),
                RadioItem(TAB_CALL_HISTORY, getString(R.string.recents)),
                RadioItem(TAB_CONTACTS, getString(R.string.contacts_tab)))

            RadioGroupDialog(this@SettingsActivity, items, config.defaultTab) {
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
        binding.settingsNavigationBarStyle.text = getNavigationBarStyleText()
        binding.settingsNavigationBarStyleHolder.setOnClickListener {
            launchNavigationBarStyleDialog()
        }
    }

    private fun launchNavigationBarStyleDialog() {
        BottomSheetChooserDialog.createChooser(
            fragmentManager = supportFragmentManager,
            title = R.string.tab_navigation,
            items = arrayOf(
                SimpleListItem(0, R.string.top, imageRes = R.drawable.ic_tab_top, selected = !config.bottomNavigationBar),
                SimpleListItem(1, R.string.bottom, imageRes = R.drawable.ic_tab_bottom, selected = config.bottomNavigationBar)
            )
        ) {
            config.bottomNavigationBar = it.id == 1
            config.tabsChanged = true
            binding.settingsNavigationBarStyle.text = getNavigationBarStyleText()
        }
    }

    private fun setupUseIconTabs() {
        binding.apply {
            settingsUseIconTabs.isChecked = config.useIconTabs
            settingsUseIconTabsHolder.setOnClickListener {
                settingsUseIconTabs.toggle()
                config.useIconTabs = settingsUseIconTabs.isChecked
                config.tabsChanged = true
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
                RadioItem(0, getString(R.string.no)),
                RadioItem(1, getString(R.string.screen_slide_animation_zoomout)),
                RadioItem(2, getString(R.string.screen_slide_animation_depth)))

            RadioGroupDialog(this@SettingsActivity, items, config.screenSlideAnimation) {
                config.screenSlideAnimation = it as Int
                config.tabsChanged = true
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

    private fun setupShowDividers() {
        binding.apply {
            settingsShowDividers.isChecked = config.useDividers
            settingsShowDividersHolder.setOnClickListener {
                settingsShowDividers.toggle()
                config.useDividers = settingsShowDividers.isChecked
            }
        }
    }

    private fun setupShowContactThumbnails() {
        binding.apply {
            settingsShowContactThumbnails.isChecked = config.showContactThumbnails
            settingsShowContactThumbnailsHolder.setOnClickListener {
                settingsShowContactThumbnails.toggle()
                config.showContactThumbnails = settingsShowContactThumbnails.isChecked
            }
        }
    }

    private fun setupShowPhoneNumbers() {
        binding.apply {
            settingsShowPhoneNumbers.isChecked = config.showPhoneNumbers
            settingsShowPhoneNumbersHolder.setOnClickListener {
                settingsShowPhoneNumbers.toggle()
                config.showPhoneNumbers = settingsShowPhoneNumbers.isChecked
            }
        }
    }

    private fun setupUseColoredContacts() {
        binding.apply {
            settingsColoredContacts.isChecked = config.useColoredContacts
            settingsColoredContactsHolder.setOnClickListener {
                settingsColoredContacts.toggle()
                config.useColoredContacts = settingsColoredContacts.isChecked
                settingsContactColorListHolder.beVisibleIf(config.useColoredContacts)
            }
        }
    }

    private fun setupContactsColorList() {
        binding.apply {
            settingsContactColorListHolder.beVisibleIf(config.useColoredContacts)
            settingsContactColorListIcon.setImageResource(getContactsColorListIcon(config.contactColorList))
            settingsContactColorListHolder.setOnClickListener {
                ColorListDialog(this@SettingsActivity) {
                    config.contactColorList = it as Int
                    settingsContactColorListIcon.setImageResource(getContactsColorListIcon(it))
                }
            }
        }
    }

    private fun setupBackgroundCallScreen() {
        val pro = isOrWasThankYouInstalled() || isPro() || isCollection()
        val black = if (pro) getString(R.string.black) else getString(R.string.black_locked)
        binding.settingsBackgroundCallScreen.text = getBackgroundCallScreenText()
        binding.settingsBackgroundCallScreenHolder.setOnClickListener {
            val items = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayListOf(
                    RadioItem(THEME_BACKGROUND, getString(R.string.theme)),
                    RadioItem(BLUR_AVATAR, getString(R.string.blurry_contact_photo)),
                    RadioItem(AVATAR, getString(R.string.contact_photo)),
                    RadioItem(BLACK_BACKGROUND, black)
                )
            } else {
                arrayListOf(
                    RadioItem(THEME_BACKGROUND, getString(R.string.theme)),
                    RadioItem(BLUR_AVATAR, getString(R.string.blurry_contact_photo)),
                    RadioItem(AVATAR, getString(R.string.contact_photo)),
                    RadioItem(TRANSPARENT_BACKGROUND, getString(R.string.blurry_wallpaper)),
                    RadioItem(BLACK_BACKGROUND, black)
                )
            }

            RadioGroupDialog(this@SettingsActivity, items, config.backgroundCallScreen) {
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
                        launchPurchase()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) settingsTransparentCallScreenHolder.beGone()
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
        val pro = isOrWasThankYouInstalled() || isPro() || isCollection()
        val sliderOutline = addLockedLabelIfNeeded(R.string.answer_slider_outline, pro)
        val sliderVertical = addLockedLabelIfNeeded(R.string.answer_slider_vertical, pro)
        BottomSheetChooserDialog.createChooser(
            fragmentManager = supportFragmentManager,
            title = R.string.answer_style,
            items = arrayOf(
                SimpleListItem(0, text = getString(R.string.buttons), imageRes = R.drawable.ic_answer_buttons, selected = config.answerStyle == ANSWER_BUTTON),
                SimpleListItem(1, text = getString(R.string.answer_slider), imageRes = R.drawable.ic_slider, selected = config.answerStyle == ANSWER_SLIDER),
                SimpleListItem(2, text = sliderOutline, imageRes = R.drawable.ic_slider_outline, selected = config.answerStyle == ANSWER_SLIDER_OUTLINE),
                SimpleListItem(3, text = sliderVertical, imageRes = R.drawable.ic_slider_vertical, selected = config.answerStyle == ANSWER_SLIDER_VERTICAL)
            )
        ) {
            if (it.id == ANSWER_SLIDER_OUTLINE || it.id == ANSWER_SLIDER_VERTICAL) {
                if (pro) {
                    config.answerStyle = it.id
                    binding.settingsAnswerStyle.text = getAnswerStyleText()
                } else {
                    launchPurchase()
                }
            } else {
                config.answerStyle = it.id
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

    private fun setupCallerDescription() {
        binding.settingsShowCallerDescription.text = getCallerDescriptionText()
        binding.settingsShowCallerDescriptionHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SHOW_CALLER_NOTHING, getString(R.string.nothing)),
                RadioItem(SHOW_CALLER_COMPANY, getString(R.string.company)),
                RadioItem(SHOW_CALLER_NICKNAME, getString(R.string.nickname)))

            RadioGroupDialog(this@SettingsActivity, items, config.showCallerDescription) {
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
            }
        }
    }

    private fun setupCallsExport() {
        binding.settingsExportCallsHolder.setOnClickListener {
            ExportCallHistoryDialog(this) { filename ->
                saveDocument.launch(filename)
            }
        }
    }

    private fun setupCallsImport() {
        binding.settingsImportCallsHolder.setOnClickListener {
            getContent.launch(CALL_HISTORY_FILE_TYPE)
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

    private fun setupMissedCallNotifications() {
        binding.apply {
            settingsMissedCallNotifications.isChecked = config.missedCallNotifications
            settingsMissedCallNotificationsHolder.setOnClickListener {
                settingsMissedCallNotifications.toggle()
                config.missedCallNotifications = settingsMissedCallNotifications.isChecked
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

    private fun setupGroupSubsequentCalls() {
        binding.apply {
            settingsGroupSubsequentCalls.isChecked = config.groupSubsequentCalls
            settingsGroupSubsequentCallsHolder.setOnClickListener {
                settingsGroupSubsequentCalls.toggle()
                config.groupSubsequentCalls = settingsGroupSubsequentCalls.isChecked
            }
        }
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

    private fun setupShowCallConfirmation() {
        binding.apply {
            settingsShowCallConfirmation.isChecked = config.showCallConfirmation
            settingsShowCallConfirmationHolder.setOnClickListener {
                settingsShowCallConfirmation.toggle()
                config.showCallConfirmation = settingsShowCallConfirmation.isChecked
            }
        }
    }

    private fun setupMaterialDesign3() {
        binding.apply {
            settingsMaterialDesign3.isChecked = config.materialDesign3
            settingsMaterialDesign3Holder.setOnClickListener {
                settingsMaterialDesign3.toggle()
                config.materialDesign3 = settingsMaterialDesign3.isChecked
                config.tabsChanged = true
            }
        }
    }

    private fun setupOverflowIcon() {
        binding.apply {
            settingsOverflowIcon.applyColorFilter(getProperTextColor())
            settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
            settingsOverflowIconHolder.setOnClickListener {
                OverflowIconDialog(this@SettingsActivity) {
                    settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
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

            val pro = isOrWasThankYouInstalled() || isPro() || isCollection()
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
                        config.simIconsColors[1],
                        addDefaultColorButton = true,
                        colorDefault = resources.getColor(R.color.ic_dialer),
                        title = resources.getString(R.string.color_sim_card_icons)
                    ) { wasPositivePressed, color ->
                        if (wasPositivePressed) {
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
                        config.simIconsColors[2],
                        addDefaultColorButton = true,
                        colorDefault = resources.getColor(R.color.color_primary),
                        title = resources.getString(R.string.color_sim_card_icons)
                    ) { wasPositivePressed, color ->
                        if (wasPositivePressed) {
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
                    it.setOnClickListener {
                        RxAnimation.from(binding.settingsPurchaseThankYouHolder)
                            .shake()
                            .subscribe()
                    }
                }
            }
        }
    }

    private fun initSimCardColor() {
        binding.apply {
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

        baseConfig.simIconsColors = recentColors
    }

    private fun hasColorChanged(old: Int, new: Int) = abs(old - new) > 1

    private fun setupDialpadStyle() {
        binding.settingsDialpadStyleHolder.setOnClickListener {
            startActivity(Intent(applicationContext, SettingsDialpadActivity::class.java))
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

    private fun setupTipJar() {
        binding.settingsTipJarHolder.apply {
            beVisibleIf(isOrWasThankYouInstalled() || isPro())
            background.applyColorFilter(getBottomNavigationBackgroundColor().lightenColor(4))
            setOnClickListener {
                launchPurchase()
            }
        }
    }

    private fun setupAbout() {
        val version = "Version: " + BuildConfig.VERSION_NAME
        binding.settingsAboutVersion.text = version
        binding.settingsAboutHolder.setOnClickListener {
            launchAbout()
        }
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

    private fun launchPurchase() {
        startPurchaseActivity(
            R.string.app_name_g,
            BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
            productIdX1, productIdX2, productIdX3,
            subscriptionIdX1, subscriptionIdX2, subscriptionIdX3,
            playStoreInstalled = isPlayStoreInstalled()
        )
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
            }
        }
    }

    private fun setupOptionsMenu() {
        val id = 487 //TODO changelog
        binding.settingsToolbar.menu.apply {
            findItem(R.id.whats_new).isVisible = BuildConfig.VERSION_CODE == id
        }
        binding.settingsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.whats_new -> {
                    showWhatsNewDialog(id)
                    true
                }
                else -> false
            }
        }
    }

    private fun showWhatsNewDialog(id: Int) {
        arrayListOf<Release>().apply {
            add(Release(id, R.string.release_487)) //TODO changelog
            WhatsNewDialog(this@SettingsActivity, this)
        }
    }
}
