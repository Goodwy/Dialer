package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import com.goodwy.commons.activities.ManageBlockedNumbersActivity
import com.goodwy.commons.dialogs.ChangeDateTimeFormatDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.dialogs.SettingsIconDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.dialer.App.Companion.isProVersion
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.dialogs.ManageVisibleTabsDialog
import com.goodwy.dialer.extensions.config
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        setupToolbar(settings_toolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupDefaultTab()
        setupManageShownTabs()
        setupBottomNavigationBar()
        setupUseIconTabs()
        setupScreenSlideAnimation()
        setupMaterialDesign3()
        setupSettingsIcon()
        setupManageBlockedNumbers()
        setupManageSpeedDial()
        setupChangeDateTimeFormat()
        setupFontSize()
        setupDialPadOpen()
        setupShowDividers()
        setupShowContactThumbnails()
        setupUseColoredContacts()
        setupTransparentCallScreen()
        setupAlwaysShowFullscreen()
        setupMissedCallNotifications()
        setupHideDialpadLetters()
        setupGroupSubsequentCalls()
        setupStartNameWithSurname()
        setupShowCallConfirmation()
        setupUseEnglish()
        setupLanguage()
        setupTipJar()
        setupAbout()
        setupDisableProximitySensor()
        setupDialpadVibrations()
        setupDialpadBeeps()
        setupDisableSwipeToAnswer()
        updateTextColors(settings_holder)

        arrayOf(divider_general, divider_calls, divider_list_view, divider_other).forEach {
            it.setBackgroundColor(getProperTextColor())
        }
        arrayOf(settings_appearance_label, settings_general_label, settings_calls_label, settings_list_view_label, settings_other_label).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled() || isProVersion())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchase() //launchPurchaseThankYouIntent()
        }
        moreButton.setOnClickListener {
            launchPurchase()
        }
        val appDrawable = resources.getColoredDrawableWithColor(R.drawable.ic_plus_support, getProperPrimaryColor())
        purchase_logo.setImageDrawable(appDrawable)
        val drawable = resources.getColoredDrawableWithColor(R.drawable.button_gray_bg, getProperPrimaryColor())
        moreButton.background = drawable
        moreButton.setTextColor(getProperBackgroundColor())
        moreButton.setPadding(2,2,2,2)
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_chevron.applyColorFilter(getProperTextColor())
        settings_customize_colors_label.text = if (isOrWasThankYouInstalled() || isProVersion()) {
            getString(R.string.customize_colors)
        } else {
            getString(R.string.customize_colors_locked)
        }
        settings_customize_colors_holder.setOnClickListener {
            //handleCustomizeColorsClick()
            if (isOrWasThankYouInstalled() || isProVersion()) {
                startCustomizationActivity()
            } else {
                launchPurchase()
            }
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())

        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    // support for device-wise blocking came on Android 7, rely only on that
    @TargetApi(Build.VERSION_CODES.N)
    private fun setupManageBlockedNumbers() {
        settings_manage_blocked_numbers_chevron.applyColorFilter(getProperTextColor())
        settings_manage_blocked_numbers_holder.beVisibleIf(isNougatPlus())
        settings_manage_blocked_numbers_holder.setOnClickListener {
            Intent(this, ManageBlockedNumbersActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupManageSpeedDial() {
        settings_manage_speed_dial_chevron.applyColorFilter(getProperTextColor())
        settings_manage_speed_dial_holder.setOnClickListener {
            Intent(this, ManageSpeedDialActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupChangeDateTimeFormat() {
        settings_change_date_time_format_chevron.applyColorFilter(getProperTextColor())
        settings_change_date_time_format_holder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupFontSize() {
        settings_font_size.text = getFontSizeText()
        settings_font_size_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large)))

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settings_font_size.text = getFontSizeText()
            }
        }
    }

    private fun setupDefaultTab() {
        settings_default_tab.text = getDefaultTabText()
        settings_default_tab_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_LAST_USED, getString(R.string.last_used_tab)),
                RadioItem(TAB_FAVORITES, getString(R.string.favorites_tab)),
                RadioItem(TAB_CALL_HISTORY, getString(R.string.recents)),
                RadioItem(TAB_CONTACTS, getString(R.string.contacts_tab)))

            RadioGroupDialog(this@SettingsActivity, items, config.defaultTab) {
                config.defaultTab = it as Int
                settings_default_tab.text = getDefaultTabText()
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

    private fun setupBottomNavigationBar() {
        settings_bottom_navigation_bar.isChecked = config.bottomNavigationBar
        settings_bottom_navigation_bar_holder.setOnClickListener {
            settings_bottom_navigation_bar.toggle()
            config.bottomNavigationBar = settings_bottom_navigation_bar.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupUseIconTabs() {
        settings_use_icon_tabs.isChecked = config.useIconTabs
        settings_use_icon_tabs_holder.setOnClickListener {
            settings_use_icon_tabs.toggle()
            config.useIconTabs = settings_use_icon_tabs.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupManageShownTabs() {
        settings_manage_shown_tabs_chevron.applyColorFilter(getProperTextColor())
        settings_manage_shown_tabs_holder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupScreenSlideAnimation() {
        settings_screen_slide_animation.text = getScreenSlideAnimationText()
        settings_screen_slide_animation_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, getString(R.string.no)),
                RadioItem(1, getString(R.string.screen_slide_animation_zoomout)),
                RadioItem(2, getString(R.string.screen_slide_animation_depth)))

            RadioGroupDialog(this@SettingsActivity, items, config.screenSlideAnimation) {
                config.screenSlideAnimation = it as Int
                config.tabsChanged = true
                settings_screen_slide_animation.text = getScreenSlideAnimationText()
            }
        }
    }

    private fun setupDialPadOpen() {
        settings_open_dialpad_at_launch.isChecked = config.openDialPadAtLaunch
        settings_open_dialpad_at_launch_holder.setOnClickListener {
            settings_open_dialpad_at_launch.toggle()
            config.openDialPadAtLaunch = settings_open_dialpad_at_launch.isChecked
        }
    }

    private fun setupShowDividers() {
        settings_show_dividers.isChecked = config.useDividers
        settings_show_dividers_holder.setOnClickListener {
            settings_show_dividers.toggle()
            config.useDividers = settings_show_dividers.isChecked
        }
    }

    private fun setupShowContactThumbnails() {
        settings_show_contact_thumbnails.isChecked = config.showContactThumbnails
        settings_show_contact_thumbnails_holder.setOnClickListener {
            settings_show_contact_thumbnails.toggle()
            config.showContactThumbnails = settings_show_contact_thumbnails.isChecked
        }
    }

    private fun setupUseColoredContacts() {
        settings_colored_contacts.isChecked = config.useColoredContacts
        settings_colored_contacts_holder.setOnClickListener {
            settings_colored_contacts.toggle()
            config.useColoredContacts = settings_colored_contacts.isChecked
        }
    }

    private fun setupTransparentCallScreen() {
        if (hasPermission(PERMISSION_READ_STORAGE)) {
            settings_transparent_call_screen.isChecked = config.transparentCallScreen
        } else settings_transparent_call_screen.isChecked = false
        settings_transparent_call_screen_holder.setOnClickListener {
            if (hasPermission(PERMISSION_READ_STORAGE)) {
                settings_transparent_call_screen.toggle()
                config.transparentCallScreen = settings_transparent_call_screen.isChecked
            } else {
                handlePermission(PERMISSION_READ_STORAGE) {
                    if (it) {
                        settings_transparent_call_screen.toggle()
                        config.transparentCallScreen = settings_transparent_call_screen.isChecked
                    }
                }
            }
        }
    }

    private fun setupAlwaysShowFullscreen() {
        settings_always_show_fullscreen.isChecked = config.showIncomingCallsFullScreen
        settings_always_show_fullscreen_holder.setOnClickListener {
            settings_always_show_fullscreen.toggle()
            config.showIncomingCallsFullScreen = settings_always_show_fullscreen.isChecked
        }
    }

    private fun setupMissedCallNotifications() {
        settings_missed_call_notifications.isChecked = config.missedCallNotifications
        settings_missed_call_notifications_holder.setOnClickListener {
            settings_missed_call_notifications.toggle()
            config.missedCallNotifications = settings_missed_call_notifications.isChecked
        }
    }

    private fun setupHideDialpadLetters() {
        settings_hide_dialpad_letters.isChecked = config.hideDialpadLetters
        settings_hide_dialpad_letters_holder.setOnClickListener {
            settings_hide_dialpad_letters.toggle()
            config.hideDialpadLetters = settings_hide_dialpad_letters.isChecked
        }
    }

    private fun setupGroupSubsequentCalls() {
        settings_group_subsequent_calls.isChecked = config.groupSubsequentCalls
        settings_group_subsequent_calls_holder.setOnClickListener {
            settings_group_subsequent_calls.toggle()
            config.groupSubsequentCalls = settings_group_subsequent_calls.isChecked
        }
    }

    private fun setupStartNameWithSurname() {
        settings_start_name_with_surname.isChecked = config.startNameWithSurname
        settings_start_name_with_surname_holder.setOnClickListener {
            settings_start_name_with_surname.toggle()
            config.startNameWithSurname = settings_start_name_with_surname.isChecked
        }
    }

    private fun setupShowCallConfirmation() {
        settings_show_call_confirmation.isChecked = config.showCallConfirmation
        settings_show_call_confirmation_holder.setOnClickListener {
            settings_show_call_confirmation.toggle()
            config.showCallConfirmation = settings_show_call_confirmation.isChecked
        }
    }

    private fun setupMaterialDesign3() {
        settings_material_design_3.isChecked = config.materialDesign3
        settings_material_design_3_holder.setOnClickListener {
            settings_material_design_3.toggle()
            config.materialDesign3 = settings_material_design_3.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupSettingsIcon() {
        settings_icon.applyColorFilter(getProperTextColor())
        settings_icon.setImageResource(getSettingsIcon(config.settingsIcon))
        settings_icon_holder.setOnClickListener {
            SettingsIconDialog(this) {
                config.settingsIcon = it as Int
                settings_icon.setImageResource(getSettingsIcon(it))
            }
        }
    }

    private fun setupTipJar() {
        settings_tip_jar_holder.beVisibleIf(isOrWasThankYouInstalled() || isProVersion())
        settings_tip_jar_chevron.applyColorFilter(getProperTextColor())
        settings_tip_jar_holder.setOnClickListener {
            launchPurchase()
        }
    }

    private fun setupAbout() {
        settings_about_chevron.applyColorFilter(getProperTextColor())
        settings_about_version.text = "Version: " + BuildConfig.VERSION_NAME
        settings_about_holder.setOnClickListener {
            launchAbout()
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons_g),
            //FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons),
            FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        startAboutActivity(R.string.app_name_g, licenses, BuildConfig.VERSION_NAME, faqItems, true, BuildConfig.GOOGLE_PLAY_LICENSING_KEY, BuildConfig.PRODUCT_ID_X1, BuildConfig.PRODUCT_ID_X2, BuildConfig.PRODUCT_ID_X3)
    }

    private fun launchPurchase() {
        startPurchaseActivity(R.string.app_name_g, BuildConfig.GOOGLE_PLAY_LICENSING_KEY, BuildConfig.PRODUCT_ID_X1, BuildConfig.PRODUCT_ID_X2, BuildConfig.PRODUCT_ID_X3)
    }

    private fun setupDisableProximitySensor() {
        settings_disable_proximity_sensor.isChecked = config.disableProximitySensor
        settings_disable_proximity_sensor_holder.setOnClickListener {
            settings_disable_proximity_sensor.toggle()
            config.disableProximitySensor = settings_disable_proximity_sensor.isChecked
        }
    }

    private fun setupDialpadVibrations() {
        settings_dialpad_vibration.isChecked = config.dialpadVibration
        settings_dialpad_vibration_holder.setOnClickListener {
            settings_dialpad_vibration.toggle()
            config.dialpadVibration = settings_dialpad_vibration.isChecked
        }
    }

    private fun setupDialpadBeeps() {
        settings_dialpad_beeps.isChecked = config.dialpadBeeps
        settings_dialpad_beeps_holder.setOnClickListener {
            settings_dialpad_beeps.toggle()
            config.dialpadBeeps = settings_dialpad_beeps.isChecked
        }
    }

    private fun setupDisableSwipeToAnswer() {
        settings_disable_swipe_to_answer.isChecked = config.disableSwipeToAnswer
        settings_disable_swipe_to_answer_holder.setOnClickListener {
            settings_disable_swipe_to_answer.toggle()
            config.disableSwipeToAnswer = settings_disable_swipe_to_answer.isChecked
        }
    }
}
