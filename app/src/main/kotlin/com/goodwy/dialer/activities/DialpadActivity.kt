package com.goodwy.dialer.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony.Sms.Intents.SECRET_CODE_ACTION
import android.telephony.PhoneNumberFormattingTextWatcher
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.*
import android.view.View.OnFocusChangeListener
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.behaviorule.arturdumchev.library.pixels
import com.behaviorule.arturdumchev.library.setHeight
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ContactsAdapter
import com.goodwy.dialer.adapters.RecentCallsAdapter
import com.goodwy.dialer.databinding.ActivityDialpadBinding
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.RecentCall
import com.goodwy.dialer.models.SpeedDial
import com.google.gson.Gson
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import me.grantland.widget.AutofitHelper
import java.io.InputStreamReader
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt
import androidx.core.view.isGone
import androidx.core.net.toUri

class DialpadActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDialpadBinding::inflate)

    var allContacts = ArrayList<Contact>()
    private var speedDialValues = ArrayList<SpeedDial>()
    private var privateCursor: Cursor? = null
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()
    private var hasBeenScrolled = false
    private var storedDialpadStyle = 0
    private var storedBackgroundColor = 0
    private var storedToneVolume = 0
    private var allRecentCalls = listOf<RecentCall>()
    private var recentsAdapter: RecentCallsAdapter? = null
    private var recentsHelper = RecentsHelper(this)
    private var isTalkBackOn = false

    @SuppressLint("MissingSuperCall", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            updateMaterialActivityViews(dialpadCoordinator, dialpadHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        }
        updateNavigationBarColor(getProperBackgroundColor())

        if (checkAppSideloading()) {
            return
        }

        if (config.hideDialpadNumbers) {
            binding.dialpadClearWrapper.apply {
                dialpad1Holder.isVisible = false
                dialpad2Holder.isVisible = false
                dialpad3Holder.isVisible = false
                dialpad4Holder.isVisible = false
                dialpad5Holder.isVisible = false
                dialpad6Holder.isVisible = false
                dialpad7Holder.isVisible = false
                dialpad8Holder.isVisible = false
                dialpad9Holder.isVisible = false
                //dialpadPlusHolder.isVisible = true
                dialpad0Holder.visibility = View.INVISIBLE
            }

            binding.dialpadRoundWrapper.apply {
                dialpad1IosHolder.isVisible = false
                dialpad2IosHolder.isVisible = false
                dialpad3IosHolder.isVisible = false
                dialpad4IosHolder.isVisible = false
                dialpad5IosHolder.isVisible = false
                dialpad6IosHolder.isVisible = false
                dialpad7IosHolder.isVisible = false
                dialpad8IosHolder.isVisible = false
                dialpad9IosHolder.isVisible = false
                //dialpadPlusIos.isVisible = true
                dialpad0IosHolder.visibility = View.INVISIBLE
            }
            binding.dialpadRectWrapper.apply {
                dialpad1Holder.isVisible = false
                dialpad2Holder.isVisible = false
                dialpad3Holder.isVisible = false
                dialpad4Holder.isVisible = false
                dialpad5Holder.isVisible = false
                dialpad6Holder.isVisible = false
                dialpad7Holder.isVisible = false
                dialpad8Holder.isVisible = false
                dialpad9Holder.isVisible = false
                //dialpadPlusHolder.isVisible = true
                dialpad0Holder.visibility = View.INVISIBLE
            }
        }

        speedDialValues = config.getSpeedDialValues()
        privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)

        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)

        binding.dialpadInput.apply {
            if (config.formatPhoneNumbers) addTextChangedListener(PhoneNumberFormattingTextWatcher(Locale.getDefault().country))
            onTextChangeListener { dialpadValueChanged(it) }
            requestFocus()
            AutofitHelper.create(this@apply)
            disableKeyboard()
        }

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { allContacts ->
            gotContacts(allContacts)
        }
        storedDialpadStyle = config.dialpadStyle
        storedToneVolume = config.toneVolume
        storedBackgroundColor = getProperBackgroundColor()
    }

    @SuppressLint("MissingSuperCall", "UnsafeIntentLaunch")
    override fun onResume() {
        super.onResume()
        if (storedDialpadStyle != config.dialpadStyle || config.tabsChanged ||storedBackgroundColor != getProperBackgroundColor()) {
            finish()
            startActivity(intent)
            return
        }

        if (storedToneVolume != config.toneVolume) {
            toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)
        }
        isTalkBackOn = isTalkBackOn()

        speedDialValues = config.getSpeedDialValues()
        initStyle()
        updateDialpadSize()
        if (config.dialpadStyle == DIALPAD_GRID || config.dialpadStyle == DIALPAD_ORIGINAL) updateCallButtonSize()
        setupOptionsMenu()
        refreshMenuItems()
        updateTextColors(binding.dialpadCoordinator)

        val properTextColor = getProperTextColor()
        val properBackgroundColor = getProperBackgroundColor()
        val properPrimaryColor = getProperPrimaryColor()
        setupToolbar(binding.dialpadToolbar, NavigationIcon.Arrow, statusBarColor = properBackgroundColor, navigationClick = false)

        arrayOf(binding.dialpadClearWrapper.dialpadAsterisk, binding.dialpadClearWrapper.dialpadHashtag,
            binding.dialpadRoundWrapper.dialpadAsteriskIos, binding.dialpadRoundWrapper.dialpadHashtagIos,
            binding.dialpadClearWrapper.dialpadVoicemail, binding.dialpadRoundWrapper.dialpadVoicemail
        ).forEach {
            it.applyColorFilter(properTextColor)
        }

        binding.dialpadClearWrapper.dialpadVoicemail.beVisibleIf(config.showVoicemailIcon)
        binding.dialpadRoundWrapper.dialpadVoicemail.beVisibleIf(config.showVoicemailIcon)

        binding.dialpadClearWrapper.root.setBackgroundColor(properBackgroundColor)
        binding.dialpadRectWrapper.root.setBackgroundColor(properBackgroundColor)
        binding.dialpadAddNumber.setTextColor(properPrimaryColor)
        binding.dialpadList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!hasBeenScrolled) {
                    hasBeenScrolled = true
                    slideDown(dialpadView())
                }
            }
        })
        binding.dialpadRecentsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!hasBeenScrolled) {
                    hasBeenScrolled = true
                    slideDown(dialpadView())
                }
            }
        })

        binding.dialpadRoundWrapperUp.setOnClickListener { dialpadHide() }
        val view = dialpadView()
        binding.dialpadInput.setOnClickListener {
            if (view.isGone) dialpadHide()
        }
        binding.dialpadInput.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (view.isGone) dialpadHide()
            }
        }

        binding.letterFastscroller.textColor = properTextColor.getColorStateList()
        binding.letterFastscroller.pressedTextColor = properPrimaryColor
        binding.letterFastscrollerThumb.setupWithFastScroller(binding.letterFastscroller)
        binding.letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        binding.letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()

        invalidateOptionsMenu()

        if (config.showRecentCallsOnDialpad) refreshItems {}
        binding.dialpadRecentsList.beVisibleIf(binding.dialpadInput.value.isEmpty() && config.showRecentCallsOnDialpad)
    }

    override fun onPause() {
        super.onPause()
        storedDialpadStyle = config.dialpadStyle
        storedToneVolume = config.toneVolume
        storedBackgroundColor = getProperBackgroundColor()
        config.tabsChanged = false
    }

    override fun onRestart() {
        super.onRestart()
        speedDialValues = config.getSpeedDialValues()
    }

    private fun initStyle() {
        if (!DialpadT9.Initialized) {
            val reader = InputStreamReader(resources.openRawResource(R.raw.t9languages))
            DialpadT9.readFromJson(reader.readText())
        }
        when (config.dialpadStyle) {
            DIALPAD_IOS -> {
                binding.dialpadClearWrapper.root.beGone()
                binding.dialpadRectWrapper.root.beGone()
                binding.dialpadRoundWrapper.apply {
                    dialpadIosHolder.beVisible()
                    dialpadIosHolder.setBackgroundColor(getProperBackgroundColor())
                    arrayOf(
                        dialpad0IosHolder, dialpad1IosHolder, dialpad2IosHolder, dialpad3IosHolder, dialpad4IosHolder,
                        dialpad5IosHolder, dialpad6IosHolder, dialpad7IosHolder, dialpad8IosHolder, dialpad9IosHolder,
                        dialpadAsteriskIosHolder, dialpadHashtagIosHolder
                    ).forEach {
                        it.foreground.applyColorFilter(Color.GRAY)
                        it.foreground.alpha = 60
                    }
                }
                initLettersIos()
            }

            DIALPAD_GRID -> {
                binding.dialpadRoundWrapper.root.beGone()
                binding.dialpadRectWrapper.root.beGone()
                binding.dialpadClearWrapper.apply {
                    dialpadGridHolder.beVisible()
                    dialpadGridHolder.setBackgroundColor(getProperBackgroundColor())
                    if (isPiePlus()) {
                        val textColor = getProperTextColor()
                        dialpadGridHolder.outlineAmbientShadowColor = textColor
                        dialpadGridHolder.outlineSpotShadowColor = textColor
                    }

                    arrayOf(
                        dividerHorizontalZero, dividerHorizontalOne, dividerHorizontalTwo, dividerHorizontalThree,
                        dividerHorizontalFour, dividerVerticalOne, dividerVerticalTwo, dividerVerticalStart, dividerVerticalEnd
                    ).forEach {
                        it.beVisible()
                    }
                }
                initLetters()
            }

            DIALPAD_CONCEPT -> {
                binding.dialpadRectWrapper.apply {
                    arrayOf(
                        dividerHorizontalZero, dividerHorizontalOne, dividerHorizontalTwo, dividerHorizontalThree,
                        dividerHorizontalFour, dividerVerticalOne, dividerVerticalTwo, dividerVerticalStart, dividerVerticalEnd
                    ).forEach {
                        it.beInvisible()
                    }
                }
                binding.dialpadRoundWrapper.root.beGone()
                binding.dialpadClearWrapper.root.beGone()
                initLettersConcept()
            }

            else -> {
                binding.dialpadClearWrapper.apply {
                    if (isPiePlus()) {
                        val textColor = getProperTextColor()
                        dialpadGridHolder.outlineAmbientShadowColor = textColor
                        dialpadGridHolder.outlineSpotShadowColor = textColor
                    }

                    arrayOf(
                        dividerHorizontalZero, dividerHorizontalOne, dividerHorizontalTwo, dividerHorizontalThree,
                        dividerHorizontalFour, dividerVerticalOne, dividerVerticalTwo, dividerVerticalStart, dividerVerticalEnd
                    ).forEach {
                        it.beInvisible()
                    }
                    dialpadGridHolder.beVisible()
                }
                binding.dialpadRoundWrapper.root.beGone()
                binding.dialpadRectWrapper.root.beGone()
                initLetters()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initLettersConcept() {
        val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
        val baseColor = baseConfig.backgroundColor
        val buttonsColor = when {
            isDynamicTheme() -> resources.getColor(R.color.you_status_bar_color, theme)
            baseColor == Color.WHITE -> resources.getColor(R.color.dark_grey, theme)
            baseColor == Color.BLACK -> resources.getColor(R.color.bottom_tabs_black_background, theme)
            else -> baseConfig.backgroundColor.lightenColor(4)
        }
        val textColor = buttonsColor.getContrastColor()
        binding.dialpadRectWrapper.apply {
            if (config.hideDialpadLetters) {
                arrayOf(
                    dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.beGone()
                }
            } else {
                dialpad1Letters.apply {
                    beInvisible()
                    setTypeface(null, config.dialpadSecondaryTypeface)
                }
                arrayOf(
                    dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters, dialpad6Letters,
                    dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.beVisible()
                    it.setTypeface(null, config.dialpadSecondaryTypeface)
                }

                val langPref = config.dialpadSecondaryLanguage
                val langLocale = Locale.getDefault().language
                val isAutoLang = DialpadT9.getSupportedSecondaryLanguages().contains(langLocale) && langPref == LANGUAGE_SYSTEM
                if (langPref!! != LANGUAGE_NONE && langPref != LANGUAGE_SYSTEM || isAutoLang) {
                    val lang = if (isAutoLang) langLocale else langPref
                    dialpad1Letters.text = DialpadT9.getLettersForNumber(2, lang) + "\nABC"
                    dialpad2Letters.text = DialpadT9.getLettersForNumber(2, lang) + "\nABC"
                    dialpad3Letters.text = DialpadT9.getLettersForNumber(3, lang) + "\nDEF"
                    dialpad4Letters.text = DialpadT9.getLettersForNumber(4, lang) + "\nGHI"
                    dialpad5Letters.text = DialpadT9.getLettersForNumber(5, lang) + "\nJKL"
                    dialpad6Letters.text = DialpadT9.getLettersForNumber(6, lang) + "\nMNO"
                    dialpad7Letters.text = DialpadT9.getLettersForNumber(7, lang) + "\nPQRS"
                    dialpad8Letters.text = DialpadT9.getLettersForNumber(8, lang) + "\nTUV"
                    dialpad9Letters.text = DialpadT9.getLettersForNumber(9, lang) + "\nWXYZ"

                    val fontSizeRu = getTextSize() - 16f
                    arrayOf(
                        dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                        dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                    ).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeRu)
                    }
                } else {
                    val fontSize = getTextSize() - 8f
                    arrayOf(
                        dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                        dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                    ).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                    }
                }
            }

            arrayOf(
                dialpad1, dialpad2, dialpad3, dialpad4, dialpad5,
                dialpad6, dialpad7, dialpad8, dialpad9, dialpad0,
                dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters, dialpad6Letters,
                dialpad7Letters, dialpad8Letters, dialpad9Letters, dialpadPlus
            ).forEach {
                it.setTextColor(textColor)
            }

            arrayOf(
                dialpad0Holder,
                dialpad1Holder,
                dialpad2Holder,
                dialpad3Holder,
                dialpad4Holder,
                dialpad5Holder,
                dialpad6Holder,
                dialpad7Holder,
                dialpad8Holder,
                dialpad9Holder,
                dialpadAsteriskHolder,
                dialpadHashtagHolder
            ).forEach {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.button_dialpad_background, theme)
                it.background.applyColorFilter(buttonsColor)
                it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    val margin = pixels(R.dimen.one_dp).toInt()
                    setMargins(margin, margin, margin, margin)
                }
            }

            //reduce the size of unnecessary buttons so that they don't look bigger than the others
            arrayOf(
                dialpadDownHolder,
                dialpadCallButtonHolder,
                dialpadClearCharHolder
            ).forEach {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.button_dialpad_background, theme)
                it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    val margin = pixels(R.dimen.one_dp).toInt()
                    val marginBottom = pixels(R.dimen.tiny_margin).toInt()
                    setMargins(margin, margin, margin, marginBottom)
                }
            }

            dialpadGridWrapper.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val margin = pixels(R.dimen.tiny_margin).toInt()
                setMargins(margin, margin, margin, margin)
            }

            arrayOf(
                binding.dialpadRectWrapper.dialpadAsterisk, binding.dialpadRectWrapper.dialpadHashtag,
                binding.dialpadRectWrapper.dialpadVoicemail
            ).forEach {
                it.applyColorFilter(textColor)
            }
            binding.dialpadRectWrapper.dialpadVoicemail.beVisibleIf(config.showVoicemailIcon)

            val simOnePrimary = config.currentSIMCardIndex == 0
            val simTwoColor = if (areMultipleSIMsAvailable) {
                if (simOnePrimary) config.simIconsColors[2] else config.simIconsColors[1]
            } else getProperPrimaryColor()
            val drawableSecondary = if (simOnePrimary) R.drawable.ic_phone_two_vector else R.drawable.ic_phone_one_vector
            val dialpadIconColor = if (simTwoColor == Color.WHITE) simTwoColor.getContrastColor() else textColor
            val downIcon = if (areMultipleSIMsAvailable) resources.getColoredDrawableWithColor(this@DialpadActivity, drawableSecondary, dialpadIconColor)
            else resources.getColoredDrawableWithColor(this@DialpadActivity, R.drawable.ic_dialpad_vector, dialpadIconColor)
            dialpadDown.setImageDrawable(downIcon)
            dialpadDownHolder.apply {
                background.applyColorFilter(simTwoColor)
                setOnClickListener {
                    if (areMultipleSIMsAvailable) {initCall(binding.dialpadInput.value, handleIndex = if (simOnePrimary) 1 else 0)} else dialpadHide()
                    maybePerformDialpadHapticFeedback(dialpadDownHolder)
                }
                contentDescription = getString(
                    if (areMultipleSIMsAvailable) {
                        if (simOnePrimary) R.string.call_from_sim_2 else R.string.call_from_sim_1
                    } else {
                        val view = dialpadView()
                        if (view.isVisible) R.string.hide_dialpad else R.string.show_dialpad
                    }
                )
            }

            val simOneColor = if (simOnePrimary) config.simIconsColors[1] else config.simIconsColors[2]
            val drawablePrimary = if (simOnePrimary) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
            val callIconId = if (areMultipleSIMsAvailable) drawablePrimary else R.drawable.ic_phone_vector
            val callIconColor = if (simOneColor == Color.WHITE) simOneColor.getContrastColor() else textColor
            val callIcon = resources.getColoredDrawableWithColor(this@DialpadActivity, callIconId, callIconColor)
            dialpadCallIcon.setImageDrawable(callIcon)
            dialpadCallButtonHolder.apply {
                background.applyColorFilter(simOneColor)
                setOnClickListener {
                    initCall(binding.dialpadInput.value, handleIndex = if (simOnePrimary || !areMultipleSIMsAvailable) 0 else 1)
                    maybePerformDialpadHapticFeedback(this)
                }
                setOnLongClickListener {
                    if (binding.dialpadInput.value.isEmpty()) {
                        val text = getTextFromClipboard()
                        binding.dialpadInput.setText(text)
                        if (text != null && text != "") {
                            binding.dialpadInput.setSelection(text.length)
                            binding.dialpadInput.requestFocusFromTouch()
                        }; true
                    } else {
                        copyNumber(); true
                    }
                }
                contentDescription = getString(
                    if (areMultipleSIMsAvailable) {
                        if (simOnePrimary) R.string.call_from_sim_1 else R.string.call_from_sim_2
                    } else R.string.call
                )
            }

            dialpadClearCharHolder.beVisible()
            dialpadClearCharHolder.background.applyColorFilter(getColor(R.color.red_call))
            dialpadClearCharHolder.setOnClickListener { clearChar(it) }
            dialpadClearCharHolder.setOnLongClickListener { clearInput(); true }
            dialpadClearChar.alpha = 1f
            dialpadClearChar.applyColorFilter(textColor)

            //dialpadHolder.setOnClickListener { binding.dialpadInput.setText(getTextFromClipboard()); true }
            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            //setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*')
            setupCharClick(dialpadHashtagHolder, '#')
            dialpadGridHolder.setOnClickListener { } //Do not press between the buttons
        }
        binding.dialpadAddNumber.setOnClickListener { addNumberToContact() }

        binding.dialpadRoundWrapperUp.background.applyColorFilter(config.simIconsColors[1])
        binding.dialpadRoundWrapperUp.setColorFilter(textColor)
    }

    @SuppressLint("SetTextI18n")
    private fun initLettersIos() {
        val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
        val getProperTextColor = getProperTextColor()
        binding.dialpadRoundWrapper.apply {
            if (config.hideDialpadLetters) {
                arrayOf(
                    dialpad2IosLetters, dialpad3IosLetters, dialpad4IosLetters, dialpad5IosLetters,
                    dialpad6IosLetters, dialpad7IosLetters, dialpad8IosLetters, dialpad9IosLetters,
                    dialpad1IosLetters
                ).forEach {
                    it.beGone()
                }
            } else {
                dialpad1IosLetters.apply {
                    beInvisible()
                    setTypeface(null, config.dialpadSecondaryTypeface)
                }
                arrayOf(
                    dialpad2IosLetters, dialpad3IosLetters, dialpad4IosLetters, dialpad5IosLetters,
                    dialpad6IosLetters, dialpad7IosLetters, dialpad8IosLetters, dialpad9IosLetters
                ).forEach {
                    it.beVisible()
                    it.setTypeface(null, config.dialpadSecondaryTypeface)
                }

                val langPref = config.dialpadSecondaryLanguage
                val langLocale = Locale.getDefault().language
                val isAutoLang = DialpadT9.getSupportedSecondaryLanguages().contains(langLocale) && langPref == LANGUAGE_SYSTEM
                if (langPref!! != LANGUAGE_NONE && langPref != LANGUAGE_SYSTEM || isAutoLang) {
                    val lang = if (isAutoLang) langLocale else langPref
                    dialpad1IosLetters.text = DialpadT9.getLettersForNumber(2, lang) + "\nABC"
                    dialpad2IosLetters.text = DialpadT9.getLettersForNumber(2, lang) + "\nABC"
                    dialpad3IosLetters.text = DialpadT9.getLettersForNumber(3, lang) + "\nDEF"
                    dialpad4IosLetters.text = DialpadT9.getLettersForNumber(4, lang) + "\nGHI"
                    dialpad5IosLetters.text = DialpadT9.getLettersForNumber(5, lang) + "\nJKL"
                    dialpad6IosLetters.text = DialpadT9.getLettersForNumber(6, lang) + "\nMNO"
                    dialpad7IosLetters.text = DialpadT9.getLettersForNumber(7, lang) + "\nPQRS"
                    dialpad8IosLetters.text = DialpadT9.getLettersForNumber(8, lang) + "\nTUV"
                    dialpad9IosLetters.text = DialpadT9.getLettersForNumber(9, lang) + "\nWXYZ"

                    val fontSizeRu = getTextSize() - 16f
                    arrayOf(
                        dialpad1IosLetters, dialpad2IosLetters, dialpad3IosLetters, dialpad4IosLetters, dialpad5IosLetters,
                        dialpad6IosLetters, dialpad7IosLetters, dialpad8IosLetters, dialpad9IosLetters
                    ).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeRu)
                    }
                } else {
                    val fontSize = getTextSize() - 8f
                    arrayOf(
                        dialpad1IosLetters, dialpad2IosLetters, dialpad3IosLetters, dialpad4IosLetters, dialpad5IosLetters,
                        dialpad6IosLetters, dialpad7IosLetters, dialpad8IosLetters, dialpad9IosLetters
                    ).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                    }
                }
            }

            if (areMultipleSIMsAvailable) {
                dialpadSimIosHolder.beVisible()
                dialpadSimIos.background.applyColorFilter(Color.GRAY)
                dialpadSimIos.background.alpha = 60
                dialpadSimIos.applyColorFilter(getProperTextColor)
                dialpadSimIosHolder.setOnClickListener {
                    if (config.currentSIMCardIndex == 0) config.currentSIMCardIndex = 1 else config.currentSIMCardIndex = 0
                    updateCallButtonIos()
                    maybePerformDialpadHapticFeedback(dialpadSimIosHolder)
                    RxAnimation.from(dialpadCallButtonIosHolder)
                        .shake()
                        .subscribe()
                }
                updateCallButtonIos()
                dialpadCallButtonIosHolder.setOnClickListener {
                    initCall(binding.dialpadInput.value, config.currentSIMCardIndex)
                    maybePerformDialpadHapticFeedback(dialpadCallButtonIosHolder)
                }
                dialpadCallButtonIosHolder.contentDescription = getString(if (config.currentSIMCardIndex == 0) R.string.call_from_sim_1 else R.string.call_from_sim_2 )
            } else {
                dialpadSimIosHolder.beGone()
                val color = config.simIconsColors[1]
                val callIcon = resources.getColoredDrawableWithColor(this@DialpadActivity, R.drawable.ic_phone_vector, color.getContrastColor())
                dialpadCallButtonIosIcon.setImageDrawable(callIcon)
                dialpadCallButtonIosHolder.background.applyColorFilter(color)
                dialpadCallButtonIosHolder.setOnClickListener {
                    initCall(binding.dialpadInput.value, 0)
                    maybePerformDialpadHapticFeedback(dialpadCallButtonIosHolder)
                }
                dialpadCallButtonIosHolder.contentDescription = getString(R.string.call)
            }

            dialpadCallButtonIosHolder.setOnLongClickListener {
                if (binding.dialpadInput.value.isEmpty()) {
                    val text = getTextFromClipboard()
                    binding.dialpadInput.setText(text)
                    if (text != null && text != "") {
                        binding.dialpadInput.setSelection(text.length)
                        binding.dialpadInput.requestFocusFromTouch()
                    }; true
                } else {
                    copyNumber(); true
                }
            }

            dialpadClearCharIos.applyColorFilter(Color.GRAY)
            dialpadClearCharIos.alpha = 0.235f
            dialpadClearCharXIos.applyColorFilter(getProperTextColor)
            dialpadClearCharIosHolder.beVisibleIf(binding.dialpadInput.value.isNotEmpty() || areMultipleSIMsAvailable)
            dialpadClearCharIosHolder.setOnClickListener { clearChar(it) }
            dialpadClearCharIosHolder.setOnLongClickListener { clearInput(); true }

            //dialpadHolder.setOnClickListener { binding.dialpadInput.setText(getTextFromClipboard()); true }
            setupCharClick(dialpad1IosHolder, '1')
            setupCharClick(dialpad2IosHolder, '2')
            setupCharClick(dialpad3IosHolder, '3')
            setupCharClick(dialpad4IosHolder, '4')
            setupCharClick(dialpad5IosHolder, '5')
            setupCharClick(dialpad6IosHolder, '6')
            setupCharClick(dialpad7IosHolder, '7')
            setupCharClick(dialpad8IosHolder, '8')
            setupCharClick(dialpad9IosHolder, '9')
            setupCharClick(dialpad0IosHolder, '0')
            //setupCharClick(dialpadPlusIosHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskIosHolder, '*')
            setupCharClick(dialpadHashtagIosHolder, '#')
            dialpadIosHolder.setOnClickListener { } //Do not press between the buttons
        }
        binding.dialpadAddNumber.setOnClickListener { addNumberToContact() }

        val simOneColor = config.simIconsColors[1]
        binding.dialpadRoundWrapperUp.background.applyColorFilter(simOneColor)
        binding.dialpadRoundWrapperUp.setColorFilter(simOneColor.getContrastColor())
    }

    private fun updateCallButtonIos() {
        val oneSim = config.currentSIMCardIndex == 0
        val simColor = if (oneSim) config.simIconsColors[1] else config.simIconsColors[2]
        val callIconId = if (oneSim) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
        val callIcon = resources.getColoredDrawableWithColor(this@DialpadActivity, callIconId, simColor.getContrastColor())
        binding.dialpadRoundWrapper.dialpadCallButtonIosIcon.setImageDrawable(callIcon)
        binding.dialpadRoundWrapper.dialpadCallButtonIosHolder.background.applyColorFilter(simColor)
    }

    @SuppressLint("SetTextI18n")
    private fun initLetters() {
        val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
        val getProperTextColor = getProperTextColor()
        binding.dialpadClearWrapper.apply {
            if (config.hideDialpadLetters) {
                arrayOf(
                    dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.beGone()
                }
            } else {
                dialpad1Letters.apply {
                    beInvisible()
                    setTypeface(null, config.dialpadSecondaryTypeface)
                }
                arrayOf(
                    dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.beVisible()
                    it.setTypeface(null, config.dialpadSecondaryTypeface)
                }

                val langPref = config.dialpadSecondaryLanguage
                val langLocale = Locale.getDefault().language
                val isAutoLang = DialpadT9.getSupportedSecondaryLanguages().contains(langLocale) && langPref == LANGUAGE_SYSTEM
                if (langPref!! != LANGUAGE_NONE && langPref != LANGUAGE_SYSTEM || isAutoLang) {
                    val lang = if (isAutoLang) langLocale else langPref
                    dialpad1Letters.text = DialpadT9.getLettersForNumber(2, lang) + "\nABC"
                    dialpad2Letters.text = DialpadT9.getLettersForNumber(2, lang) + "\nABC"
                    dialpad3Letters.text = DialpadT9.getLettersForNumber(3, lang) + "\nDEF"
                    dialpad4Letters.text = DialpadT9.getLettersForNumber(4, lang) + "\nGHI"
                    dialpad5Letters.text = DialpadT9.getLettersForNumber(5, lang) + "\nJKL"
                    dialpad6Letters.text = DialpadT9.getLettersForNumber(6, lang) + "\nMNO"
                    dialpad7Letters.text = DialpadT9.getLettersForNumber(7, lang) + "\nPQRS"
                    dialpad8Letters.text = DialpadT9.getLettersForNumber(8, lang) + "\nTUV"
                    dialpad9Letters.text = DialpadT9.getLettersForNumber(9, lang) + "\nWXYZ"

                    val fontSizeRu = getTextSize() - 16f
                    arrayOf(
                        dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                        dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                    ).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeRu)
                    }
                } else {
                    val fontSize = getTextSize() - 8f
                    arrayOf(
                        dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                        dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                    ).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                    }
                }
            }

            val simOnePrimary = config.currentSIMCardIndex == 0
            dialpadCallTwoButton.apply {
                if (areMultipleSIMsAvailable) {
                    beVisible()
                    val simTwoColor = if (simOnePrimary) config.simIconsColors[2] else config.simIconsColors[1]
                    val drawableSecondary = if (simOnePrimary) R.drawable.ic_phone_two_vector else R.drawable.ic_phone_one_vector
                    val callIcon = resources.getColoredDrawableWithColor(this@DialpadActivity, drawableSecondary, simTwoColor.getContrastColor())
                    setImageDrawable(callIcon)
                    background.applyColorFilter(simTwoColor)
                    beVisible()
                    setOnClickListener {
                        initCall(binding.dialpadInput.value, handleIndex = if (simOnePrimary) 1 else 0)
                        maybePerformDialpadHapticFeedback(this)
                    }
                    contentDescription = getString(if (simOnePrimary) R.string.call_from_sim_2 else R.string.call_from_sim_1 )
                } else {
                    beGone()
                } }

            val simOneColor = if (simOnePrimary) config.simIconsColors[1] else config.simIconsColors[2]
            val drawablePrimary = if (simOnePrimary) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
            val callIconId = if (areMultipleSIMsAvailable) drawablePrimary else R.drawable.ic_phone_vector
            val callIcon = resources.getColoredDrawableWithColor(this@DialpadActivity, callIconId, simOneColor.getContrastColor())
            dialpadCallButton.apply {
                setImageDrawable(callIcon)
                background.applyColorFilter(simOneColor)
                setOnClickListener {
                    initCall(binding.dialpadInput.value, handleIndex = if (simOnePrimary || !areMultipleSIMsAvailable) 0 else 1)
                    maybePerformDialpadHapticFeedback(this)
                }
                setOnLongClickListener {
                    if (binding.dialpadInput.value.isEmpty()) {
                        val text = getTextFromClipboard()
                        binding.dialpadInput.setText(text)
                        if (text != null && text != "") {
                            binding.dialpadInput.setSelection(text.length)
                            binding.dialpadInput.requestFocusFromTouch()
                        }; true
                    } else {
                        copyNumber(); true
                    }
                }
                contentDescription = getString(
                    if (areMultipleSIMsAvailable) {
                        if (simOnePrimary) R.string.call_from_sim_1 else R.string.call_from_sim_2
                    } else R.string.call
                )
            }

            dialpadClearCharHolder.beVisibleIf(binding.dialpadInput.value.isNotEmpty() || areMultipleSIMsAvailable)
            dialpadClearChar.applyColorFilter(Color.GRAY)
            dialpadClearChar.alpha = 0.4f
            dialpadClearCharX.applyColorFilter(getProperTextColor)
            dialpadClearCharHolder.setOnClickListener { clearChar(it) }
            dialpadClearCharHolder.setOnLongClickListener { clearInput(); true }

            //dialpadHolder.setOnClickListener { binding.dialpadInput.setText(getTextFromClipboard()); true }
            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            //setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*')
            setupCharClick(dialpadHashtagHolder, '#')
            dialpadGridHolder.setOnClickListener { } //Do not press between the buttons
        }
        binding.dialpadAddNumber.setOnClickListener { addNumberToContact() }

        val simOneColor = config.simIconsColors[1]
        binding.dialpadRoundWrapperUp.background.applyColorFilter(simOneColor)
        binding.dialpadRoundWrapperUp.setColorFilter(simOneColor.getContrastColor())
    }

    private fun dialpadView() = when (config.dialpadStyle) {
        DIALPAD_IOS -> binding.dialpadRoundWrapper.root
        DIALPAD_CONCEPT -> binding.dialpadRectWrapper.root
        else -> binding.dialpadClearWrapper.root
    }

    private fun updateDialpadSize() {
        val size = config.dialpadSize
        val view = when (config.dialpadStyle) {
            DIALPAD_IOS -> binding.dialpadRoundWrapper.dialpadIosWrapper
            DIALPAD_CONCEPT -> binding.dialpadRectWrapper.dialpadGridWrapper
            else -> binding.dialpadClearWrapper.dialpadGridWrapper
        }
        val dimens = if (config.dialpadStyle == DIALPAD_IOS) pixels(R.dimen.dialpad_ios_height) else pixels(R.dimen.dialpad_grid_height)
        view.setHeight((dimens * (size / 100f)).toInt())
    }

    private fun updateCallButtonSize() {
        val size = config.callButtonPrimarySize
        val view = binding.dialpadClearWrapper.dialpadCallButton
        val dimens = pixels(R.dimen.dialpad_phone_button_size)
        view.setHeightAndWidth((dimens * (size / 100f)).toInt())
        view.setPadding((dimens * 0.1765 * (size / 100f)).toInt())

        if (areMultipleSIMsAvailable()) {
            val sizeSecondary = config.callButtonSecondarySize
            val viewSecondary = binding.dialpadClearWrapper.dialpadCallTwoButton
            val dimensSecondary = pixels(R.dimen.dialpad_button_size_small)
            viewSecondary.setHeightAndWidth((dimensSecondary * (sizeSecondary / 100f)).toInt())
            viewSecondary.setPadding((dimens * 0.1765 * (sizeSecondary / 100f)).toInt())
        }
    }

    private fun refreshMenuItems() {
        binding.dialpadToolbar.menu.apply {
            findItem(R.id.copy_number).isVisible = binding.dialpadInput.value.isNotEmpty()
            findItem(R.id.web_search).isVisible = binding.dialpadInput.value.isNotEmpty()
            findItem(R.id.cab_call_anonymously).isVisible = binding.dialpadInput.value.isNotEmpty()
            findItem(R.id.clear_call_history).isVisible = config.showRecentCallsOnDialpad
            findItem(R.id.show_blocked_numbers).isVisible = config.showRecentCallsOnDialpad
            findItem(R.id.show_blocked_numbers).title =
                if (config.showBlockedNumbers) getString(R.string.hide_blocked_numbers) else getString(R.string.show_blocked_numbers)
        }
    }

    private fun setupOptionsMenu() {
        binding.dialpadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.paste_number -> {
                    val text = getTextFromClipboard()
                    binding.dialpadInput.setText(text)
                    if (text != null && text != "") {
                        binding.dialpadInput.setSelection(text.length)
                        binding.dialpadInput.requestFocusFromTouch()
                    }
                }
                R.id.copy_number -> copyNumber()
                R.id.web_search -> webSearch()
                R.id.cab_call_anonymously -> initCallAnonymous()
                R.id.show_blocked_numbers -> showBlockedNumbers()
                R.id.clear_call_history -> clearCallHistory()
                R.id.settings_dialpad -> startActivity(Intent(applicationContext, SettingsDialpadActivity::class.java))
                R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
                //R.id.add_number_to_contact -> addNumberToContact()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
        binding.dialpadToolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (CallManager.getPhoneState()) {
            NoCall -> {
                if (intent.getBooleanExtra(SHOW_RECENT_CALLS_ON_DIALPAD, false)) finishAffinity()
                else finish()
            }
            else -> {
                startActivity(Intent(this, CallActivity::class.java))
                super.onBackPressed()
            }
        }
    }

    private fun copyNumber() {
        val clip = binding.dialpadInput.value
        copyToClipboard(clip)
    }

    private fun webSearch() {
        val text = binding.dialpadInput.value
        launchInternetSearch(text)
    }

    private fun checkDialIntent(): Boolean {
        return if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) && intent.data != null && intent.dataString?.contains("tel:") == true) {
            val number = Uri.decode(intent.dataString).substringAfter("tel:")
            binding.dialpadInput.setText(number)
            binding.dialpadInput.setSelection(number.length)
            true
        } else {
            false
        }
    }

    private fun addNumberToContact() {
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, binding.dialpadInput.value)
            launchActivityIntent(this)
        }
    }

    private fun dialpadPressed(char: Char, view: View?) {
        binding.dialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun dialpadHide() {
        val view = dialpadView()
        if (view.isVisible) {
            slideDown(view)
        } else {
            slideUp(view)
        }
    }

    private fun slideDown(view: View) {
        view.animate()
            .translationY(view.height.toFloat())
            //.alpha(0f)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // superfluous restoration
                    view.visibility = View.GONE
                    //view.alpha = 1f
                    view.translationY = 0f
                }
            })
        hasBeenScrolled = false
        if ((view == binding.dialpadRoundWrapper.root ||
                view == binding.dialpadClearWrapper.root ||
                view == binding.dialpadRectWrapper.root) &&
            binding.dialpadRoundWrapperUp.isGone
        ) slideUp(
            binding.dialpadRoundWrapperUp
        )
    }

    private fun slideUp(view: View) {
        view.visibility = View.VISIBLE
        //view.alpha = 0f
        if (view.height > 0) {
            slideUpNow(view)
        } else {
            // wait till height is measured
            view.post { slideUpNow(view) }
        }
        if (view == binding.dialpadRoundWrapper.root ||
            view == binding.dialpadClearWrapper.root ||
            view == binding.dialpadRectWrapper.root
        ) slideDown(binding.dialpadRoundWrapperUp)
    }

    private fun slideUpNow(view: View) {
        view.translationY = view.height.toFloat()
        view.animate()
            .translationY(0f)
            //.alpha(1f)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.VISIBLE
                    //view.alpha = 1f
                }
            })
    }

    private fun clearChar(view: View) {
        binding.dialpadInput.dispatchKeyEvent(binding.dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInput() {
        binding.dialpadInput.setText("")
    }

    private fun gotContacts(newContacts: ArrayList<Contact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        runOnUiThread {
            if (!checkDialIntent() && binding.dialpadInput.value.isEmpty()) {
                dialpadValueChanged("")
            }
        }
    }

    private fun dialpadValueChanged(textFormat: String) {
        val len = textFormat.length
        val view = dialpadView()
        if (len == 0 && view.isGone) {
            slideUp(view)
        }
        if (textFormat.length > 8 && textFormat.startsWith("*#*#") && textFormat.endsWith("#*#*")) {
            val secretCode = textFormat.substring(4, textFormat.length - 4)
            if (isOreoPlus()) {
                if (isDefaultDialer()) {
                    getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
                } else {
                    launchSetDefaultDialerIntent()
                }
            } else {
                val intent = Intent(SECRET_CODE_ACTION, "android_secret_code://$secretCode".toUri())
                sendBroadcast(intent)
            }
            return
        }

        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()
        (binding.dialpadRecentsList.adapter as? RecentCallsAdapter)?.finishActMode()

        val text = if (config.formatPhoneNumbers) textFormat.removeNumberFormatting() else textFormat
        val collator = Collator.getInstance(sysLocale())
        val filtered = allContacts.filter { contact ->
            val langPref = config.dialpadSecondaryLanguage ?: ""
            val langLocale = Locale.getDefault().language
            val isAutoLang = DialpadT9.getSupportedSecondaryLanguages().contains(langLocale) && langPref == LANGUAGE_SYSTEM
            val lang = if (isAutoLang) langLocale else langPref

            val convertedName = DialpadT9.convertLettersToNumbers(
                contact.name.normalizeString().uppercase(), lang)
            val convertedNameWithoutSpaces = convertedName.filterNot { it.isWhitespace() }
            val convertedNickname = DialpadT9.convertLettersToNumbers(
                contact.nickname.normalizeString().uppercase(), lang)
            val convertedCompany = DialpadT9.convertLettersToNumbers(
                contact.organization.company.normalizeString().uppercase(), lang)
            val convertedNameToDisplay = DialpadT9.convertLettersToNumbers(
                contact.getNameToDisplay().normalizeString().uppercase(), lang)
            val convertedNameToDisplayWithoutSpaces = convertedNameToDisplay.filterNot { it.isWhitespace() }

            contact.doesContainPhoneNumber(text, convertLetters = true, search = true)
                || (convertedName.contains(text, true))
                || (convertedNameWithoutSpaces.contains(text, true))
                || (convertedNameToDisplay.contains(text, true))
                || (convertedNameToDisplayWithoutSpaces.contains(text, true))
                || (convertedNickname.contains(text, true))
                || (convertedCompany.contains(text, true))
        }.sortedWith(compareBy(collator) {
            it.getNameToDisplay()
        }).toMutableList() as ArrayList<Contact>

//        binding.letterFastscroller.setupWithContacts(binding.dialpadList, filtered)

        ContactsAdapter(
            activity = this,
            contacts = filtered,
            recyclerView = binding.dialpadList,
            highlightText = text,
            refreshItemsListener = null,
            showNumber = true,
            allowLongClick = false,
            itemClick = {
                val contact = it as Contact
                startCallWithConfirmationCheck(contact.getPrimaryNumber() ?: return@ContactsAdapter, contact.getNameToDisplay())
            },
            profileIconClick = {
                startContactDetailsIntent(it as Contact)
            }).apply {
            binding.dialpadList.adapter = this
        }

        binding.dialpadAddNumber.beVisibleIf(binding.dialpadInput.value.isNotEmpty())
        binding.dialpadAddNumber.setTextColor(getProperPrimaryColor())
        binding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
        binding.dialpadList.beVisibleIf(filtered.isNotEmpty())
        val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
        binding.dialpadClearWrapper.dialpadClearCharHolder.beVisibleIf((binding.dialpadInput.value.isNotEmpty() && config.dialpadStyle != DIALPAD_IOS && config.dialpadStyle != DIALPAD_CONCEPT) || areMultipleSIMsAvailable)
        binding.dialpadRectWrapper.dialpadClearCharHolder.beVisibleIf(config.dialpadStyle == DIALPAD_CONCEPT)
        binding.dialpadRoundWrapper.dialpadClearCharIosHolder.beVisibleIf((binding.dialpadInput.value.isNotEmpty() && config.dialpadStyle == DIALPAD_IOS) || areMultipleSIMsAvailable)
        binding.dialpadInput.beVisibleIf(binding.dialpadInput.value.isNotEmpty())
        binding.dialpadList.beVisibleIf(binding.dialpadInput.value.isNotEmpty())
        binding.dialpadRecentsList.beVisibleIf(binding.dialpadInput.value.isEmpty() && config.showRecentCallsOnDialpad)
        refreshMenuItems()
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && isDefaultDialer()) {
            dialpadValueChanged(binding.dialpadInput.value)
        }
    }

    private fun initCall(number: String = binding.dialpadInput.value, handleIndex: Int, displayName: String = "") {
        if (number.isNotEmpty()) {
            val nameToDisplay = if (displayName != "") displayName else number
            if (handleIndex != -1 && areMultipleSIMsAvailable()) {
                //callContactWithSimWithConfirmationCheck(number, nameToDisplay, handleIndex == 0)
                if (config.showCallConfirmation) {
                    CallConfirmationDialog(this, nameToDisplay) {
                        callContactWithSim(number, handleIndex == 0)
                        if (config.dialpadClearWhenStartCall) binding.dialpadInput.setText("")
                    }
                } else {
                    callContactWithSim(number, handleIndex == 0)
                    if (config.dialpadClearWhenStartCall) binding.dialpadInput.setText("")
                }
            } else {
                //startCallWithConfirmationCheck(number, nameToDisplay)
                if (config.showCallConfirmation) {
                    CallConfirmationDialog(this, nameToDisplay) {
                        startCallIntent(number)
                        if (config.dialpadClearWhenStartCall) binding.dialpadInput.setText("")
                    }
                } else {
                    startCallIntent(number)
                    if (config.dialpadClearWhenStartCall) binding.dialpadInput.setText("")
                }
            }

//            Handler().postDelayed({
//                binding.dialpadInput.setText("")
//            }, 1000)
        } else {
            RecentsHelper(this).getRecentCalls(queryLimit = 1) {
                val mostRecentNumber = it.firstOrNull()?.phoneNumber
                if (!mostRecentNumber.isNullOrEmpty()) {
                    runOnUiThread {
                        binding.dialpadInput.setText(mostRecentNumber)
                        binding.dialpadInput.setSelection(mostRecentNumber.length)
                    }
                }
            }
        }
    }

    private fun speedDial(id: Int): Boolean {
        if (binding.dialpadInput.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, -1, speedDial.displayName)
                return true
            } else {
                ConfirmationDialog(this, getString(R.string.open_speed_dial_manage)) {
                    startActivity(Intent(applicationContext, ManageSpeedDialActivity::class.java))
                }
            }
        }
        return false
    }

    private fun startDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else if (char == '*') {
            clearChar(view)
            dialpadPressed(',', view)
        } else if (char == '#') {
            clearChar(view)
            if (config.dialpadHashtagLongClick == DIALPAD_LONG_CLICK_WAIT) dialpadPressed(';', view)
            else startActivity(Intent(applicationContext, SettingsDialpadActivity::class.java))
        } else {
            val result = speedDial(char.digitToInt())
            if (result) {
                stopDialpadTone(char)
                clearChar(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        if (isTalkBackOn) {
            view.setOnClickListener { dialpadPressed(char, view) }
            view.setOnLongClickListener { performLongClick(view, char); true}
        }
        else view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed({
                            performLongClick(view, char)
                        }, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }

    private fun initCallAnonymous() {
        val dialpadValue = binding.dialpadInput.value
        if (config.showWarningAnonymousCall) {
            val text = String.format(getString(R.string.call_anonymously_warning), dialpadValue)
            ConfirmationAdvancedDialog(
                this,
                text,
                R.string.call_anonymously_warning,
                R.string.ok,
                R.string.do_not_show_again,
                fromHtml = true
            ) {
                if (it) {
                    initCall("#31#$dialpadValue", 0)
                } else {
                    config.showWarningAnonymousCall = false
                    initCall("#31#$dialpadValue", 0)
                }
            }
        } else {
            initCall("#31#$dialpadValue", 0)
        }
    }

    private fun refreshItems(callback: (() -> Unit)?) {
        gotRecents()
        refreshCallLog(loadAll = true) {
//            refreshCallLog(loadAll = true)
        }
    }

    private fun refreshCallLog(loadAll: Boolean = false, callback: (() -> Unit)? = null) {
        getRecentCalls(loadAll) {
            allRecentCalls = it
            config.recentCallsCache = Gson().toJson(it.take(300))
            runOnUiThread { gotRecents(it) }

            callback?.invoke()
        }
    }

    private fun getRecentCalls(loadAll: Boolean, callback: (List<RecentCall>) -> Unit) {
        val queryCount = if (loadAll) config.queryLimitRecent else RecentsHelper.QUERY_LIMIT
        val existingRecentCalls = allRecentCalls

        with(recentsHelper) {
            if (config.groupSubsequentCalls) {
                getGroupedRecentCalls(existingRecentCalls, queryCount, true) {
                    prepareCallLog(it, callback)
                }
            } else {
                getRecentCalls(existingRecentCalls, queryCount, isDialpad = true, updateCallsCache = true) { it ->
                    val calls = if (config.groupAllCalls) it.distinctBy { it.phoneNumber } else it
                    prepareCallLog(calls, callback)
                }
            }
        }
    }

    private fun prepareCallLog(calls: List<RecentCall>, callback: (List<RecentCall>) -> Unit) {
        if (calls.isEmpty()) {
            callback(emptyList())
            return
        }

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            ensureBackgroundThread {
                val privateContacts = getPrivateContacts()
                val updatedCalls = updateNamesIfEmpty(
                    calls = maybeFilterPrivateCalls(calls, privateContacts),
                    contacts = contacts,
                    privateContacts = privateContacts
                )

                callback(
                    updatedCalls
                )
            }
        }
    }

    private fun getPrivateContacts(): ArrayList<Contact> {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        return MyContactsContentProvider.getContacts(this, privateCursor)
    }

    private fun maybeFilterPrivateCalls(calls: List<RecentCall>, privateContacts: List<Contact>): List<RecentCall> {
        val ignoredSources = baseConfig.ignoredContactSources
        return if (SMT_PRIVATE in ignoredSources) {
            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
            calls.filterNot { it.phoneNumber in privateNumbers }
        } else {
            calls
        }
    }

    private fun updateNamesIfEmpty(calls: List<RecentCall>, contacts: List<Contact>, privateContacts: List<Contact>): List<RecentCall> {
        if (calls.isEmpty()) return mutableListOf()

        val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
        return calls.map { call ->
            if (call.phoneNumber == call.name) {
                val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(call.phoneNumber) }
                val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == call.phoneNumber }

                when {
                    privateContact != null -> withUpdatedName(call = call, name = privateContact.getNameToDisplay())
                    contact != null -> withUpdatedName(call = call, name = contact.getNameToDisplay())
                    else -> call
                }
            } else {
                call
            }
        }
    }

    private fun withUpdatedName(call: RecentCall, name: String): RecentCall {
        return call.copy(
            name = name,
            groupedCalls = call.groupedCalls
                ?.map { it.copy(name = name) }
                ?.toMutableList()
                ?.ifEmpty { null }
        )
    }

    private fun gotRecents(recents: List<RecentCall> = config.parseRecentCallsCache()) {
        val currAdapter = binding.dialpadRecentsList.adapter
        if (currAdapter == null) {
            recentsAdapter = RecentCallsAdapter(
                activity = this,
                recyclerView = binding.dialpadRecentsList,
                refreshItemsListener = null,
                showOverflowMenu = true,
                hideTimeAtOtherDays = true,
                isDialpad = true,
                itemDelete = { deleted ->
                    allRecentCalls = allRecentCalls.filter { it !in deleted }
                },
                itemClick = {
                    val recentCall = it as RecentCall
                    if (config.showCallConfirmation) {
                        CallConfirmationDialog(this, recentCall.name) {
                            launchCallIntent(recentCall.phoneNumber, key = BuildConfig.RIGHT_APP_KEY)
                        }
                    } else {
                        launchCallIntent(recentCall.phoneNumber, key = BuildConfig.RIGHT_APP_KEY)
                    }
                }
            )

            binding.dialpadRecentsList.adapter = recentsAdapter
            recentsAdapter?.updateItems(recents)

            if (this.areSystemAnimationsEnabled) {
                binding.dialpadRecentsList.scheduleLayoutAnimation()
            }
        } else {
            recentsAdapter?.updateItems(recents)
        }
    }

    private fun showBlockedNumbers() {
        config.showBlockedNumbers = !config.showBlockedNumbers
        binding.dialpadToolbar.menu.findItem(R.id.show_blocked_numbers).title = if (config.showBlockedNumbers) getString(R.string.hide_blocked_numbers) else getString(R.string.show_blocked_numbers)
        config.needUpdateRecents = true
        runOnUiThread {
            refreshItems {}
        }
    }

    private fun clearCallHistory() {
        val confirmationText = "${getString(R.string.clear_history_confirmation)}\n\n${getString(R.string.cannot_be_undone)}"
        ConfirmationDialog(this, confirmationText) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                allRecentCalls = emptyList()
                runOnUiThread {
                    refreshItems {}
                }
            }
        }
    }
}
