package com.goodwy.dialer.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony.Sms.Intents.SECRET_CODE_ACTION
import android.telephony.PhoneNumberUtils
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
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ContactsAdapter
import com.goodwy.dialer.databinding.ActivityDialpadBinding
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.SpeedDial
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import me.grantland.widget.AutofitHelper
import java.util.*
import kotlin.math.roundToInt

class DialpadActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDialpadBinding::inflate)

    private var allContacts = ArrayList<Contact>()
    private var speedDialValues = ArrayList<SpeedDial>()
    private val russianCharsMap = HashMap<Char, Int>()
    private var hasRussianLocale = false
    private var privateCursor: Cursor? = null
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()
    private var hasBeenScrolled = false
    private var storedDialpadStyle = 0

    @SuppressLint("MissingSuperCall", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        hasRussianLocale = Locale.getDefault().language == "ru"

        binding.apply {
            updateMaterialActivityViews(dialpadCoordinator, dialpadHolder, useTransparentNavigation = true, useTopSearchMenu = false)
//            setupMaterialScrollListener(dialpadList, dialpadToolbar)
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

        if (hasRussianLocale) {
            initRussianChars()
            val fontSizeRu = getTextSize() - 16f//resources.getDimension(R.dimen.small_text_size)
            binding.dialpadClearWrapper.apply {
                dialpad2Letters.text = "АБВГ\nABC"
                dialpad3Letters.text = "ДЕЁЖЗ\nDEF"
                dialpad4Letters.text = "ИЙКЛ\nGHI"
                dialpad5Letters.text = "МНОП\nJKL"
                dialpad6Letters.text = "РСТУ\nMNO"
                dialpad7Letters.text = "ФХЦЧ\nPQRS"
                dialpad8Letters.text = "ШЩЪЫ\nTUV"
                dialpad9Letters.text = "ЬЭЮЯ\nWXYZ"

                arrayOf(
                    dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeRu)
                }
            }

            binding.dialpadRoundWrapper.apply {
                dialpad2IosLetters.text = "АБВГ\nABC"
                dialpad3IosLetters.text = "ДЕЁЖЗ\nDEF"
                dialpad4IosLetters.text = "ИЙКЛ\nGHI"
                dialpad5IosLetters.text = "МНОП\nJKL"
                dialpad6IosLetters.text = "РСТУ\nMNO"
                dialpad7IosLetters.text = "ФХЦЧ\nPQRS"
                dialpad8IosLetters.text = "ШЩЪЫ\nTUV"
                dialpad9IosLetters.text = "ЬЭЮЯ\nWXYZ"

                arrayOf(
                    dialpad2IosLetters, dialpad3IosLetters, dialpad4IosLetters, dialpad5IosLetters,
                    dialpad6IosLetters, dialpad7IosLetters, dialpad8IosLetters, dialpad9IosLetters
                ).forEach {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeRu)
                }
            }

            binding.dialpadRectWrapper.apply {
                dialpad2Letters.text = "АБВГ\nABC"
                dialpad3Letters.text = "ДЕЁЖЗ\nDEF"
                dialpad4Letters.text = "ИЙКЛ\nGHI"
                dialpad5Letters.text = "МНОП\nJKL"
                dialpad6Letters.text = "РСТУ\nMNO"
                dialpad7Letters.text = "ФХЦЧ\nPQRS"
                dialpad8Letters.text = "ШЩЪЫ\nTUV"
                dialpad9Letters.text = "ЬЭЮЯ\nWXYZ"

                arrayOf(
                    dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeRu)
                }
            }
        }

        binding.apply {
            dialpadInput.onTextChangeListener { dialpadValueChanged(it) }
            dialpadInput.requestFocus()
            AutofitHelper.create(dialpadInput)
            dialpadInput.disableKeyboard()
        }

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { allContacts ->
            gotContacts(allContacts)
        }
        storedDialpadStyle = config.dialpadStyle
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        if (storedDialpadStyle != config.dialpadStyle) {
            finish()
            startActivity(intent)
            return
        }
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
            binding.dialpadRoundWrapper.dialpadAsteriskIos, binding.dialpadRoundWrapper.dialpadHashtagIos
        ).forEach {
            it.applyColorFilter(properTextColor)
        }
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

        binding.dialpadRoundWrapperUp.setOnClickListener { dialpadHide() }
        val view = dialpadView()
        binding.dialpadInput.setOnClickListener {
            if (view.visibility == View.GONE) dialpadHide()
        }
        binding.dialpadInput.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (view.visibility == View.GONE) dialpadHide()
            }
        }

        binding.letterFastscroller.textColor = properTextColor.getColorStateList()
        binding.letterFastscroller.pressedTextColor = properPrimaryColor
        binding.letterFastscrollerThumb.setupWithFastScroller(binding.letterFastscroller)
        binding.letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        binding.letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()

        invalidateOptionsMenu()
    }

    @SuppressLint("MissingSuperCall")
    override fun onDestroy() {
        super.onDestroy()
        storedDialpadStyle = config.dialpadStyle
    }

    override fun onPause() {
        super.onPause()
        storedDialpadStyle = config.dialpadStyle
    }

    override fun onRestart() {
        super.onRestart()
        speedDialValues = config.getSpeedDialValues()
    }

    private fun initStyle() {
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
                binding.dialpadClearWrapper.apply {
                    arrayOf(
                        dividerHorizontalZero, dividerHorizontalOne, dividerHorizontalTwo, dividerHorizontalThree,
                        dividerHorizontalFour, dividerVerticalOne, dividerVerticalTwo, dividerVerticalStart, dividerVerticalEnd
                    ).forEach {
                        it.beVisible()
                    }
                    dialpadGridHolder.beVisible()
                }
                binding.dialpadRoundWrapper.root.beGone()
                binding.dialpadRectWrapper.root.beGone()
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

    private fun initLettersConcept() {
        val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
        val baseColor = baseConfig.backgroundColor
        val buttonsColor = when {
            baseConfig.isUsingSystemTheme -> resources.getColor(R.color.you_status_bar_color, theme)
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
                dialpad1Letters.beInvisible()
                arrayOf(
                    dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters, dialpad6Letters,
                    dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.beVisible()
                }

                hasRussianLocale = Locale.getDefault().language == "ru"
                if (!hasRussianLocale) {
                    val fontSize = getTextSize() - 8f//resources.getDimension(R.dimen.small_text_size)
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
                binding.dialpadRectWrapper.dialpadAsterisk, binding.dialpadRectWrapper.dialpadHashtag
            ).forEach {
                it.applyColorFilter(textColor)
            }

            val simOnePrimary = config.currentSIMCardIndex == 0
            val simTwoColor = if (areMultipleSIMsAvailable) {
                if (simOnePrimary) config.simIconsColors[2] else config.simIconsColors[1]
            } else getProperPrimaryColor()
            dialpadDownHolder.background.applyColorFilter(simTwoColor)
            val drawableSecondary = if (simOnePrimary) R.drawable.ic_phone_two_vector else R.drawable.ic_phone_one_vector
            val dialpadIconColor = if (simTwoColor == Color.WHITE) simTwoColor.getContrastColor() else textColor
            val downIcon = if (areMultipleSIMsAvailable) resources.getColoredDrawableWithColor(this@DialpadActivity, drawableSecondary, dialpadIconColor)
            else resources.getColoredDrawableWithColor(this@DialpadActivity, R.drawable.ic_dialpad_vector, dialpadIconColor)
            dialpadDown.setImageDrawable(downIcon)
            dialpadDownHolder.setOnClickListener {
                maybePerformDialpadHapticFeedback(dialpadDownHolder)
                if (areMultipleSIMsAvailable) {initCall(binding.dialpadInput.value, handleIndex = if (simOnePrimary) 1 else 0)} else dialpadHide()
            }

            val simOneColor = if (simOnePrimary) config.simIconsColors[1] else config.simIconsColors[2]
            val drawablePrimary = if (simOnePrimary) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
            val callIconId = if (areMultipleSIMsAvailable) drawablePrimary else R.drawable.ic_phone_vector
            val callIconColor = if (simOneColor == Color.WHITE) simOneColor.getContrastColor() else textColor
            val callIcon = resources.getColoredDrawableWithColor(this@DialpadActivity, callIconId, callIconColor)
            dialpadCallIcon.setImageDrawable(callIcon)
            dialpadCallButtonHolder.background.applyColorFilter(simOneColor)
            dialpadCallButtonHolder.setOnClickListener {
                maybePerformDialpadHapticFeedback(dialpadCallButtonHolder)
                initCall(binding.dialpadInput.value, handleIndex = if (simOnePrimary || !areMultipleSIMsAvailable) 0 else 1)
            }
            dialpadCallButtonHolder.setOnLongClickListener {
                if (binding.dialpadInput.value.isEmpty()) {
                    binding.dialpadInput.setText(getTextFromClipboard()); true
                } else {
                    copyNumber(); true
                }
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
        }
        binding.dialpadAddNumber.setOnClickListener { addNumberToContact() }

        binding.dialpadRoundWrapperUp.background.applyColorFilter(config.simIconsColors[1])
        binding.dialpadRoundWrapperUp.setColorFilter(textColor)
    }

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
                dialpad1IosLetters.beInvisible()
                arrayOf(
                    dialpad2IosLetters, dialpad3IosLetters, dialpad4IosLetters, dialpad5IosLetters,
                    dialpad6IosLetters, dialpad7IosLetters, dialpad8IosLetters, dialpad9IosLetters
                ).forEach {
                    it.beVisible()
                }

                hasRussianLocale = Locale.getDefault().language == "ru"
                if (!hasRussianLocale) {
                    val fontSize = getTextSize() - 8f//resources.getDimension(R.dimen.small_text_size)
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
                    maybePerformDialpadHapticFeedback(dialpadSimIosHolder)
                    updateCallButtonIos()
                    RxAnimation.from(dialpadCallButtonIosHolder)
                        .shake()
                        .subscribe()
                }
                updateCallButtonIos()
                dialpadCallButtonIosHolder.setOnClickListener {
                    maybePerformDialpadHapticFeedback(dialpadCallButtonIosHolder)
                    initCall(binding.dialpadInput.value, config.currentSIMCardIndex)
                }
            } else {
                dialpadSimIosHolder.beGone()
                val color = config.simIconsColors[1]
                val callIcon = resources.getColoredDrawableWithColor(this@DialpadActivity, R.drawable.ic_phone_vector, color.getContrastColor())
                dialpadCallButtonIosIcon.setImageDrawable(callIcon)
                dialpadCallButtonIosHolder.background.applyColorFilter(color)
                dialpadCallButtonIosHolder.setOnClickListener {
                    maybePerformDialpadHapticFeedback(dialpadCallButtonIosHolder)
                    initCall(binding.dialpadInput.value, 0)
                }
            }

            dialpadCallButtonIosHolder.setOnLongClickListener {
                if (binding.dialpadInput.value.isEmpty()) {
                    binding.dialpadInput.setText(getTextFromClipboard()); true
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
                dialpad1Letters.beInvisible()
                arrayOf(
                    dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                    dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                ).forEach {
                    it.beVisible()
                }

                hasRussianLocale = Locale.getDefault().language == "ru"
                if (!hasRussianLocale) {
                    val fontSize = getTextSize() - 8f//resources.getDimension(R.dimen.small_text_size)
                    arrayOf(
                        dialpad1Letters, dialpad2Letters, dialpad3Letters, dialpad4Letters, dialpad5Letters,
                        dialpad6Letters, dialpad7Letters, dialpad8Letters, dialpad9Letters
                    ).forEach {
                        it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                    }
                }
            }

            val simOnePrimary = config.currentSIMCardIndex == 0
            if (areMultipleSIMsAvailable) {
                dialpadCallTwoButton.beVisible()
                val simTwoColor = if (simOnePrimary) config.simIconsColors[2] else config.simIconsColors[1]
                val drawableSecondary = if (simOnePrimary) R.drawable.ic_phone_two_vector else R.drawable.ic_phone_one_vector
                val callIcon = resources.getColoredDrawableWithColor(this@DialpadActivity, drawableSecondary, simTwoColor.getContrastColor())
                dialpadCallTwoButton.setImageDrawable(callIcon)
                dialpadCallTwoButton.background.applyColorFilter(simTwoColor)
                dialpadCallTwoButton.beVisible()
                dialpadCallTwoButton.setOnClickListener {
                    maybePerformDialpadHapticFeedback(dialpadCallTwoButton)
                    initCall(binding.dialpadInput.value, handleIndex = if (simOnePrimary) 1 else 0)
                }
            } else {
                dialpadCallTwoButton.beGone()
            }

            val simOneColor = if (simOnePrimary) config.simIconsColors[1] else config.simIconsColors[2]
            val drawablePrimary = if (simOnePrimary) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
            val callIconId = if (areMultipleSIMsAvailable) drawablePrimary else R.drawable.ic_phone_vector
            val callIcon = resources.getColoredDrawableWithColor(this@DialpadActivity, callIconId, simOneColor.getContrastColor())
            dialpadCallButton.setImageDrawable(callIcon)
            dialpadCallButton.background.applyColorFilter(simOneColor)
            dialpadCallButton.setOnClickListener {
                maybePerformDialpadHapticFeedback(dialpadCallButton)
                initCall(binding.dialpadInput.value, handleIndex = if (simOnePrimary || !areMultipleSIMsAvailable) 0 else 1)
            }
            dialpadCallButton.setOnLongClickListener {
                if (binding.dialpadInput.value.isEmpty()) {
                    binding.dialpadInput.setText(getTextFromClipboard()); true
                } else {
                    copyNumber(); true
                }
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
        }
        binding.dialpadAddNumber.setOnClickListener { addNumberToContact() }

        val simOneColor = config.simIconsColors[1]
        binding.dialpadRoundWrapperUp.background.applyColorFilter(simOneColor)
        binding.dialpadRoundWrapperUp.setColorFilter(simOneColor.getContrastColor())
    }

    private fun dialpadView() = if (config.dialpadStyle == DIALPAD_IOS) binding.dialpadRoundWrapper.root
        else if (config.dialpadStyle == DIALPAD_CONCEPT) binding.dialpadRectWrapper.root
        else binding.dialpadClearWrapper.root

    private fun updateDialpadSize() {
        val size = config.dialpadSize
        val view = if (config.dialpadStyle == DIALPAD_IOS) binding.dialpadRoundWrapper.dialpadIosWrapper
            else if (config.dialpadStyle == DIALPAD_CONCEPT) binding.dialpadRectWrapper.dialpadGridWrapper
            else binding.dialpadClearWrapper.dialpadGridWrapper
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
            findItem(R.id.cab_call_anonymously).isVisible = binding.dialpadInput.value.isNotEmpty()
        }
    }

    private fun setupOptionsMenu() {
        binding.dialpadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.paste_number -> binding.dialpadInput.setText(getTextFromClipboard())
                R.id.copy_number -> copyNumber()
                R.id.cab_call_anonymously -> initCallAnonymous()
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

    override fun onBackPressed() {
        when (CallManager.getPhoneState()) {
            NoCall -> {
                finish()
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
        if (view.visibility == View.VISIBLE) {
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
        if ((view == binding.dialpadRoundWrapper.root || view == binding.dialpadClearWrapper.root || view == binding.dialpadRectWrapper.root) && binding.dialpadRoundWrapperUp.visibility == View.GONE) slideUp(binding.dialpadRoundWrapperUp)
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
        if (view == binding.dialpadRoundWrapper.root || view == binding.dialpadClearWrapper.root || view == binding.dialpadRectWrapper.root) slideDown(binding.dialpadRoundWrapperUp)
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

    @TargetApi(Build.VERSION_CODES.O)
    private fun dialpadValueChanged(text: String) {
        val len = text.length
        val view = dialpadView()
        if (len == 0 && view.visibility == View.GONE) {
            slideUp(view)
        }
        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            val secretCode = text.substring(4, text.length - 4)
            if (isOreoPlus()) {
                if (isDefaultDialer()) {
                    getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
                } else {
                    launchSetDefaultDialerIntent()
                }
            } else {
                val intent = Intent(SECRET_CODE_ACTION, Uri.parse("android_secret_code://$secretCode"))
                sendBroadcast(intent)
            }
            return
        }

        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

        val filtered = allContacts.filter {
            var convertedName = PhoneNumberUtils.convertKeypadLettersToDigits(it.name.normalizeString())
            var convertedNickname = PhoneNumberUtils.convertKeypadLettersToDigits(it.nickname.normalizeString())
            var convertedCompany = PhoneNumberUtils.convertKeypadLettersToDigits(it.organization.company.normalizeString())

            if (hasRussianLocale) {
                var currConvertedName = ""
                convertedName.lowercase(Locale.getDefault()).forEach { char ->
                    val convertedChar = russianCharsMap.getOrElse(char) { char }
                    currConvertedName += convertedChar
                }
                convertedName = currConvertedName

                var currConvertedNickname = ""
                convertedNickname.lowercase(Locale.getDefault()).forEach { char ->
                    val convertedChar = russianCharsMap.getOrElse(char) { char }
                    currConvertedNickname += convertedChar
                }
                convertedNickname = currConvertedNickname

                var currConvertedCompany = ""
                convertedCompany.lowercase(Locale.getDefault()).forEach { char ->
                    val convertedChar = russianCharsMap.getOrElse(char) { char }
                    currConvertedCompany += convertedChar
                }
                convertedCompany = currConvertedCompany
            }

            it.doesContainPhoneNumber(text, true, true) || (convertedName.contains(text, true))
                || (convertedNickname.contains(text, true)) || (convertedCompany.contains(text, true))
        }.sortedWith(compareBy {
            !it.doesContainPhoneNumber(text, true, true)
        }).toMutableList() as ArrayList<Contact>

        binding.letterFastscroller.setupWithRecyclerView(binding.dialpadList, { position ->
            try {
                val name = filtered[position].getNameToDisplay()
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.uppercase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })

        ContactsAdapter(
            activity = this,
            contacts = filtered,
            recyclerView = binding.dialpadList,
            highlightText = text,
            refreshItemsListener = null,
            showNumber = true,
            allowLongClick = false
        ) {
            val contact = it as Contact
            if (config.showCallConfirmation) {
                CallConfirmationDialog(this@DialpadActivity, contact.getNameToDisplay()) {
                    startCallIntent(contact.getPrimaryNumber() ?: return@CallConfirmationDialog)
                }
            } else {
                startCallIntent(contact.getPrimaryNumber() ?: return@ContactsAdapter)
            }
        }.apply {
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
                if (config.showCallConfirmation) {
                    CallConfirmationDialog(this, nameToDisplay) {
                        callContactWithSim(number, handleIndex == 0)
                    }
                } else {
                    callContactWithSim(number, handleIndex == 0)
                }
            } else {
                if (config.showCallConfirmation) {
                    CallConfirmationDialog(this, nameToDisplay) {
                        startCallIntent(number)
                    }
                } else {
                    startCallIntent(number)
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

    private fun initRussianChars() {
        russianCharsMap['а'] = 2; russianCharsMap['б'] = 2; russianCharsMap['в'] = 2; russianCharsMap['г'] = 2
        russianCharsMap['д'] = 3; russianCharsMap['е'] = 3; russianCharsMap['ё'] = 3; russianCharsMap['ж'] = 3; russianCharsMap['з'] = 3
        russianCharsMap['и'] = 4; russianCharsMap['й'] = 4; russianCharsMap['к'] = 4; russianCharsMap['л'] = 4
        russianCharsMap['м'] = 5; russianCharsMap['н'] = 5; russianCharsMap['о'] = 5; russianCharsMap['п'] = 5
        russianCharsMap['р'] = 6; russianCharsMap['с'] = 6; russianCharsMap['т'] = 6; russianCharsMap['у'] = 6
        russianCharsMap['ф'] = 7; russianCharsMap['х'] = 7; russianCharsMap['ц'] = 7; russianCharsMap['ч'] = 7
        russianCharsMap['ш'] = 8; russianCharsMap['щ'] = 8; russianCharsMap['ъ'] = 8; russianCharsMap['ы'] = 8
        russianCharsMap['ь'] = 9; russianCharsMap['э'] = 9; russianCharsMap['ю'] = 9; russianCharsMap['я'] = 9
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
            dialpadPressed(';', view)
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
        view.setOnTouchListener { _, event ->
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
                com.goodwy.commons.R.string.ok,
                com.goodwy.commons.R.string.do_not_show_again,
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
}
