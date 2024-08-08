package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.database.Cursor
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.behaviorule.arturdumchev.library.pixels
import com.behaviorule.arturdumchev.library.setHeight
import com.goodwy.commons.dialogs.ColorPickerDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.helpers.rustore.RuStoreHelper
import com.goodwy.commons.helpers.rustore.model.StartPurchasesEvent
import com.goodwy.commons.models.RadioItem
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.databinding.ActivitySettingsDialpadBinding
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.SpeedDial
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import kotlinx.coroutines.launch
import ru.rustore.sdk.core.feature.model.FeatureAvailabilityResult
import java.util.*
import kotlin.math.abs


class SettingsDialpadActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySettingsDialpadBinding::inflate)
    private val purchaseHelper = PurchaseHelper(this)
    private val ruStoreHelper = RuStoreHelper(this)
    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    private val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    private val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    private val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3
    private var ruStoreIsConnected = false

    private var speedDialValues = ArrayList<SpeedDial>()
    private val russianCharsMap = HashMap<Char, Int>()
    private var hasRussianLocale = false
    private var privateCursor: Cursor? = null
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val hideDialpadHandler = Handler(Looper.getMainLooper())


    @SuppressLint("MissingSuperCall", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        hasRussianLocale = Locale.getDefault().language == "ru"

        binding.apply {
            updateMaterialActivityViews(dialpadCoordinator, dialpadHolder, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(dialpadNestedScrollview, dialpadToolbar)
        }

        updateDialpadSize()
        setupDialpadSize()

        if (config.dialpadStyle == DIALPAD_GRID || config.dialpadStyle == DIALPAD_ORIGINAL) updateCallButtonSize()

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
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        val properTextColor = getProperTextColor()
        val properBackgroundColor = getProperBackgroundColor()

        setupPurchaseThankYou()
        setupDialpadStyle()
        setupSimCardColorList()
        setupPrimarySimCard()
        setupHideDialpadLetters()
        setupDialpadVibrations()
        setupDialpadBeeps()
        setupToneVolume()
        setupButtonSize()

        binding.apply {

            arrayOf(
                styleHolder,
                dialpadSettingsCardHolder,
                dialpadSizeCardHolder,
                buttonSizeCardHolder
            ).forEach {
                it.setCardBackgroundColor(getBottomNavigationBackgroundColor())
            }

            speedDialValues = config.getSpeedDialValues()
            initStyle()
            updateTextColors(dialpadHolder)
            setupToolbar(dialpadToolbar, NavigationIcon.Arrow)

            arrayOf(dialpadClearWrapper.dialpadAsterisk, dialpadClearWrapper.dialpadHashtag,
                dialpadRoundWrapper.dialpadAsteriskIos, dialpadRoundWrapper.dialpadHashtagIos,
                toneVolumeMinus, toneVolumePlus,
                dialpadSizeMinus, dialpadSizePlus,
                buttonSizeMinus, buttonSizePlus,
                buttonSecondSizeMinus, buttonSecondSizePlus
            ).forEach {
                it.applyColorFilter(properTextColor)
            }
            dialpadClearWrapper.dialpadGridHolder.setBackgroundColor(properBackgroundColor)
            dialpadRectWrapper.dialpadGridHolder.setBackgroundColor(properBackgroundColor)
        }

        invalidateOptionsMenu()

        if (isPlayStoreInstalled()) {
            //PlayStore
            purchaseHelper.initBillingClient()
            val iapList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
            val subList: ArrayList<String> = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3, subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3)
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
        if (isRuStoreInstalled()) {
            //RuStore
            ruStoreHelper.checkPurchasesAvailability()

            lifecycleScope.launch {
                ruStoreHelper.eventStart
                    .flowWithLifecycle(lifecycle)
                    .collect { event ->
                        handleEventStart(event)
                    }
            }

            lifecycleScope.launch {
                ruStoreHelper.statePurchased
                    .flowWithLifecycle(lifecycle)
                    .collect { state ->
                        //update of purchased
                        if (!state.isLoading && ruStoreIsConnected) {
                            baseConfig.isProRuStore = state.purchases.firstOrNull() != null
                            updatePro()
                        }
                    }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        speedDialValues = config.getSpeedDialValues()
    }

    private fun initStyle() {
        binding.apply {
            when (config.dialpadStyle) {
                DIALPAD_IOS -> {
                    dialpadRoundWrapper.apply {
                        dialpadIosHolder.alpha = 0.4f
                        dialpadIosHolder.setBackgroundColor(getProperBackgroundColor())
                        dialpadCallButtonIosHolder.background.applyColorFilter(config.simIconsColors[1])
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
                    dialpadClearWrapper.apply {
                        dialpadGridHolder.alpha = 0.4f
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
                    dialpadRectWrapper.apply {
                        dialpadGridHolder.alpha = 0.4f
                        arrayOf(
                            dividerHorizontalZero, dividerHorizontalOne, dividerHorizontalTwo, dividerHorizontalThree,
                            dividerHorizontalFour, dividerVerticalOne, dividerVerticalTwo, dividerVerticalStart, dividerVerticalEnd
                        ).forEach {
                            it.beVisible()
                        }
                    }
                    initLettersConcept()
                }

                else -> {
                    dialpadClearWrapper.apply {
                        dialpadGridHolder.alpha = 0.4f
                        arrayOf(
                            dividerHorizontalZero, dividerHorizontalOne, dividerHorizontalTwo, dividerHorizontalThree,
                            dividerHorizontalFour, dividerVerticalOne, dividerVerticalTwo, dividerVerticalStart, dividerVerticalEnd
                        ).forEach {
                            it.beInvisible()
                        }
                    }
                    initLetters()
                }
            }
        }
    }

    private fun initLettersConcept() {
        val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
        val baseColor = baseConfig.backgroundColor
        val buttonColor = when {
            baseConfig.isUsingSystemTheme -> resources.getColor(R.color.you_status_bar_color, theme)
            baseColor == Color.WHITE -> resources.getColor(R.color.dark_grey, theme)
            baseColor == Color.BLACK -> resources.getColor(R.color.bottom_tabs_black_background, theme)
            else -> baseConfig.backgroundColor.lightenColor(4)
        }
        val textColor = buttonColor.getContrastColor()
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
                it.background.applyColorFilter(buttonColor)
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
            val downIcon = if (areMultipleSIMsAvailable) resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, drawableSecondary, dialpadIconColor)
            else resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, R.drawable.ic_dialpad_vector, dialpadIconColor)
            dialpadDown.setImageDrawable(downIcon)

            val simOneColor = if (simOnePrimary) config.simIconsColors[1] else config.simIconsColors[2]
            val drawablePrimary = if (simOnePrimary) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
            val callIconId = if (areMultipleSIMsAvailable) drawablePrimary else R.drawable.ic_phone_vector
            val callIconColor = if (simOneColor == Color.WHITE) simOneColor.getContrastColor() else textColor
            val callIcon = resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, callIconId, callIconColor)
            dialpadCallIcon.setImageDrawable(callIcon)
            dialpadCallButtonHolder.background.applyColorFilter(simOneColor)

            dialpadClearCharHolder.beVisible()
            dialpadClearCharHolder.background.applyColorFilter(getColor(R.color.red_call))
            dialpadClearChar.alpha = 1f
            dialpadClearChar.applyColorFilter(textColor)
        }
    }

    private fun initLetters() {
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

            val getProperBackgroundColor = getProperBackgroundColor()
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
                dialpadHashtagHolder,
                dialpadClearCharHolder
            ).forEach {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.button_dialpad_background, theme)
                it.background.applyColorFilter(getProperBackgroundColor)
                it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    val margin = pixels(R.dimen.one_dp).toInt()
                    setMargins(margin, margin, margin, margin)
                }
            }

            val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
            val simOnePrimary = config.currentSIMCardIndex == 0
            if (areMultipleSIMsAvailable) {
                dialpadCallTwoButton.beVisible()
                val simTwoColor = if (simOnePrimary) config.simIconsColors[2] else config.simIconsColors[1]
                val drawableSecondary = if (simOnePrimary) R.drawable.ic_phone_two_vector else R.drawable.ic_phone_one_vector
                val callIcon = resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, drawableSecondary, simTwoColor.getContrastColor())
                dialpadCallTwoButton.setImageDrawable(callIcon)
                dialpadCallTwoButton.background.applyColorFilter(simTwoColor)
                dialpadCallTwoButton.beVisible()
            } else {
                dialpadCallTwoButton.beGone()
            }

            val simOneColor = if (simOnePrimary) config.simIconsColors[1] else config.simIconsColors[2]
            val drawablePrimary = if (simOnePrimary) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
            val callIconId = if (areMultipleSIMsAvailable) drawablePrimary else R.drawable.ic_phone_vector
            val callIcon = resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, callIconId, simOneColor.getContrastColor())
            dialpadCallButton.setImageDrawable(callIcon)
            dialpadCallButton.background.applyColorFilter(simOneColor)

            dialpadClearCharHolder.beVisibleIf(areMultipleSIMsAvailable)
            dialpadClearChar.applyColorFilter(Color.GRAY)
            dialpadClearChar.alpha = 0.4f
            dialpadClearCharX.applyColorFilter(getProperTextColor())
        }
    }

    private fun initLettersIos() {
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
            val areMultipleSIMsAvailable = areMultipleSIMsAvailable()
            val getProperTextColor = getProperTextColor()
            if (areMultipleSIMsAvailable) {
                dialpadSimIosHolder.beVisible()
                dialpadSimIos.background.applyColorFilter(Color.GRAY)
                dialpadSimIos.background.alpha = 60
                dialpadSimIos.applyColorFilter(getProperTextColor)
                updateCallButton()
            } else {
                dialpadSimIosHolder.beGone()
                val color = config.simIconsColors[1]
                val callIcon = resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, R.drawable.ic_phone_vector, color.getContrastColor())
                dialpadCallButtonIosIcon.setImageDrawable(callIcon)
                dialpadCallButtonIosHolder.background.applyColorFilter(color)
            }

            dialpadClearCharIos.applyColorFilter(Color.GRAY)
            dialpadClearCharIos.alpha = 0.235f
            dialpadClearCharXIos.applyColorFilter(getProperTextColor)
            dialpadClearCharIosHolder.beVisibleIf(areMultipleSIMsAvailable)
        }
    }

    private fun updateCallButton() {
        val oneSim = config.currentSIMCardIndex == 0
        val simColor = if (oneSim) config.simIconsColors[1] else config.simIconsColors[2]
        val callIconId = if (oneSim) R.drawable.ic_phone_one_vector else R.drawable.ic_phone_two_vector
        val callIcon = resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, callIconId, simColor.getContrastColor())
        binding.dialpadRoundWrapper.dialpadCallButtonIosIcon.setImageDrawable(callIcon)
        binding.dialpadRoundWrapper.dialpadCallButtonIosHolder.background.applyColorFilter(simColor)
    }

    private fun setupDialpadSize() {
        binding.apply {
            val progress = config.dialpadSize
            dialpadSize.progress = progress
            val textProgress = "$progress %"
            dialpadSizeValue.text = textProgress

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialpadSize.min = 50
            }

            dialpadSizeMinus.setOnClickListener {
                dialpadSize.progress = dialpadSize.progress - 1
                showDialpad()
            }
            dialpadSizeValue.setOnClickListener {
                dialpadSize.progress = 100
                showDialpad()
            }
            dialpadSizePlus.setOnClickListener {
                dialpadSize.progress = dialpadSize.progress + 1
                showDialpad()
            }

            dialpadSize.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
                    val view = when (config.dialpadStyle) {
                        DIALPAD_IOS -> dialpadRoundWrapper.root
                        DIALPAD_CONCEPT -> dialpadRectWrapper.root
                        else -> dialpadClearWrapper.root
                    }
                    view.beVisible()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    val view = when (config.dialpadStyle) {
                        DIALPAD_IOS -> dialpadRoundWrapper.root
                        DIALPAD_CONCEPT -> dialpadRectWrapper.root
                        else -> dialpadClearWrapper.root
                    }
                    view.beGone()
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    updateDialpadSize(progress)
                    config.dialpadSize = progress
                }
            })
        }
    }

    private fun setupButtonSize() {
        binding.apply {
            buttonSizeWrapper.beVisibleIf(config.dialpadStyle == DIALPAD_GRID || config.dialpadStyle == DIALPAD_ORIGINAL)
            if (checkPro()) {
                arrayOf(
                    buttonSizeHolder, buttonSize, buttonSecondSizeHolder, buttonSecondSize
                ).forEach {
                    it.alpha = 1f
                }
                buttonSizeLabel.setText(R.string.button_primary)
                buttonSizeEmpty.beGone()
            } else {
                arrayOf(
                    buttonSizeHolder, buttonSize, buttonSecondSizeHolder, buttonSecondSize
                ).forEach {
                    it.alpha = 0.4f
                }
                val lockText = addLockedLabelIfNeeded(R.string.button_primary)
                buttonSizeLabel.text = lockText
                buttonSizeEmpty.beVisible()
                buttonSizeEmpty.setOnClickListener {
                    shakePurchase()

                    RxAnimation.from(buttonSizeWrapper)
                        .shake(shakeTranslation = 2f)
                        .subscribe()

                    showSnackbar(binding.root)
                }
            }

            val progress = config.callButtonPrimarySize
            buttonSize.progress = progress
            val textProgress = "$progress %"
            buttonSizeValue.text = textProgress

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                buttonSize.min = 50
            }

            buttonSizeMinus.setOnClickListener {
                buttonSize.progress = buttonSize.progress - 1
                showDialpad()
            }
            buttonSizeValue.setOnClickListener {
                buttonSize.progress = 100
                showDialpad()
            }
            buttonSizePlus.setOnClickListener {
                buttonSize.progress = buttonSize.progress + 1
                showDialpad()
            }

            buttonSize.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
                    dialpadClearWrapper.root.beVisible()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    dialpadClearWrapper.root.beGone()
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    updateCallButtonSize(progress)
                    config.callButtonPrimarySize = progress
                }
            })

            //second button
            if (areMultipleSIMsAvailable()) {
                buttonSecondSizeHolder.beVisible()
                buttonSecondSize.beVisible()
                val progressSecond = config.callButtonSecondarySize
                buttonSecondSize.progress = progressSecond
                val textProgressSecond = "$progressSecond %"
                buttonSecondSizeValue.text = textProgressSecond

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    buttonSecondSize.min = 50
                }

                buttonSecondSizeMinus.setOnClickListener {
                    buttonSecondSize.progress = buttonSecondSize.progress - 1
                    showDialpad()
                }
                buttonSecondSizeValue.setOnClickListener {
                    buttonSecondSize.progress = 100
                    showDialpad()
                }
                buttonSecondSizePlus.setOnClickListener {
                    buttonSecondSize.progress = buttonSecondSize.progress + 1
                    showDialpad()
                }

                buttonSecondSize.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
                        dialpadClearWrapper.root.beVisible()
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        dialpadClearWrapper.root.beGone()
                    }

                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        updateCallButtonSize(progress, false)
                        config.callButtonSecondarySize = progress
                    }
                })
            } else {
                buttonSecondSizeHolder.beGone()
                buttonSecondSize.beGone()
            }
        }
    }

    private fun updateDialpadSize(percent: Int = config.dialpadSize) {
        val view = when (config.dialpadStyle) {
            DIALPAD_IOS -> binding.dialpadRoundWrapper.dialpadIosWrapper
            DIALPAD_CONCEPT -> binding.dialpadRectWrapper.dialpadGridWrapper
            else -> binding.dialpadClearWrapper.dialpadGridWrapper
        }
        val dimens = if (config.dialpadStyle == DIALPAD_IOS) pixels(R.dimen.dialpad_ios_height) else pixels(R.dimen.dialpad_grid_height)
        view.setHeight((dimens * (percent / 100f)).toInt())
        val textPercent = "$percent %"
        binding.dialpadSizeValue.text = textPercent
    }

    private fun updateCallButtonSize(percent: Int, buttonOne: Boolean = true) {
        val view = if (buttonOne) binding.dialpadClearWrapper.dialpadCallButton else binding.dialpadClearWrapper.dialpadCallTwoButton
        val dimens = if (buttonOne) pixels(R.dimen.dialpad_phone_button_size) else pixels(R.dimen.dialpad_button_size_small)
        view.setHeightAndWidth((dimens * (percent / 100f)).toInt())
        view.setPadding((dimens * 0.1765 * (percent / 100f)).toInt())
        val textPercent = "$percent %"
        if (buttonOne) binding.buttonSizeValue.text = textPercent
        else  binding.buttonSecondSizeValue.text = textPercent
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

    private fun showDialpad() {
        val view = when (config.dialpadStyle) {
            DIALPAD_IOS -> binding.dialpadRoundWrapper.root
            DIALPAD_CONCEPT -> binding.dialpadRectWrapper.root
            else -> binding.dialpadClearWrapper.root
        }
        view.beVisible()
        hideDialpadHandler.removeCallbacks(updateHideDialpadTask)
        hideDialpadHandler.postDelayed(updateHideDialpadTask, 2000)
    }

    private val updateHideDialpadTask = Runnable {
        val view = when (config.dialpadStyle) {
            DIALPAD_IOS -> binding.dialpadRoundWrapper.root
            DIALPAD_CONCEPT -> binding.dialpadRectWrapper.root
            else -> binding.dialpadClearWrapper.root
        }
        view.beGone()
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

    private fun setupDialpadStyle() {
        val pro = checkPro()
        val iOS = addLockedLabelIfNeeded(R.string.ios_g, pro)
        binding.settingsDialpadStyle.text = getDialpadStyleText()
        binding.settingsDialpadStyleHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(DIALPAD_ORIGINAL, getString(R.string.clean_theme_g)),
                RadioItem(DIALPAD_GRID, getString(R.string.grid)),
                RadioItem(DIALPAD_IOS, iOS),
                RadioItem(DIALPAD_CONCEPT, getString(R.string.concept_theme_g))
            )

            RadioGroupDialog(this@SettingsDialpadActivity, items, config.dialpadStyle, R.string.theme) {
                if (it as Int == DIALPAD_IOS) {
                    if (pro) {
                        binding.dialpadClearWrapper.root.beGone()
                        binding.dialpadRectWrapper.root.beGone()
                        config.dialpadStyle = it
                        binding.settingsDialpadStyle.text = getDialpadStyleText()
                        initStyle()
                        updateDialpadSize()
                        showDialpad()
                    } else {
                        shakePurchase()

                        RxAnimation.from(binding.styleHolder)
                            .shake(shakeTranslation = 2f)
                            .subscribe()

                        showSnackbar(binding.root)
                    }
                } else if (it == DIALPAD_CONCEPT) {
                    binding.dialpadRoundWrapper.root.beGone()
                    binding.dialpadClearWrapper.root.beGone()
                    config.dialpadStyle = it
                    binding.settingsDialpadStyle.text = getDialpadStyleText()
                    initStyle()
                    updateDialpadSize()
                    showDialpad()
                } else {
                    binding.dialpadRoundWrapper.root.beGone()
                    binding.dialpadRectWrapper.root.beGone()
                    config.dialpadStyle = it
                    binding.settingsDialpadStyle.text = getDialpadStyleText()
                    initStyle()
                    updateDialpadSize()
                    updateCallButtonSize()
                    showDialpad()
                }
                binding.buttonSizeWrapper.beVisibleIf(config.dialpadStyle == DIALPAD_GRID || config.dialpadStyle == DIALPAD_ORIGINAL)
            }
        }
    }

    private fun getDialpadStyleText() = getString(
        when (config.dialpadStyle) {
            DIALPAD_GRID -> R.string.grid
            DIALPAD_IOS -> R.string.ios_g
            DIALPAD_CONCEPT -> R.string.concept_theme_g
            else -> R.string.clean_theme_g
        }
    )

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
                        this@SettingsDialpadActivity,
                        config.simIconsColors[1],
                        addDefaultColorButton = true,
                        colorDefault = resources.getColor(R.color.ic_dialer),
                        title = resources.getString(R.string.color_sim_card_icons)
                    ) { wasPositivePressed, color ->
                        if (wasPositivePressed) {
                            if (hasColorChanged(config.simIconsColors[1], color)) {
                                addSimCardColor(1, color)
                                initSimCardColor()
                                initStyle()
                                showDialpad()
                            }
                        }
                    }
                }
                settingsSimCardColor2Holder.setOnClickListener {
                    ColorPickerDialog(
                        this@SettingsDialpadActivity,
                        config.simIconsColors[2],
                        addDefaultColorButton = true,
                        colorDefault = resources.getColor(R.color.color_primary),
                        title = resources.getString(R.string.color_sim_card_icons)
                    ) { wasPositivePressed, color ->
                        if (wasPositivePressed) {
                            if (hasColorChanged(config.simIconsColors[2], color)) {
                                addSimCardColor(2, color)
                                initSimCardColor()
                                initStyle()
                                showDialpad()
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
                        shakePurchase()

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

        baseConfig.simIconsColors = recentColors
    }

    private fun hasColorChanged(old: Int, new: Int) = abs(old - new) > 1

    private fun setupPrimarySimCard() {
        val simList = getAvailableSIMCardLabels()
        if (simList.size > 1) {
            binding.settingsPrimarySimCardHolder.beVisibleIf(areMultipleSIMsAvailable())
            binding.settingsPrimarySimCard.text = if (config.currentSIMCardIndex == 0) simList[0].label else simList[1].label
            binding.settingsPrimarySimCardHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(0, simList[0].label),
                    RadioItem(1, simList[1].label)
                )

                RadioGroupDialog(this@SettingsDialpadActivity, items, config.currentSIMCardIndex, R.string.primary_sim_card) {
                    config.currentSIMCardIndex = it as Int
                    binding.settingsPrimarySimCard.text = if (config.currentSIMCardIndex == 0) simList[0].label else simList[1].label
                    initStyle()
                    showDialpad()
                }
            }
        } else binding.settingsPrimarySimCardHolder.beGone()
    }

    private fun setupHideDialpadLetters() {
        binding.apply {
            settingsHideDialpadLetters.isChecked = config.hideDialpadLetters
            settingsHideDialpadLettersHolder.setOnClickListener {
                settingsHideDialpadLetters.toggle()
                config.hideDialpadLetters = settingsHideDialpadLetters.isChecked
                initStyle()
                showDialpad()
            }
        }
    }

    private fun setupDialpadVibrations() {
        binding.apply {
            settingsDialpadVibration.isChecked = config.dialpadVibration
            settingsDialpadVibrationHolder.setOnClickListener {
                settingsDialpadVibration.toggle()
                config.dialpadVibration = settingsDialpadVibration.isChecked
            }
        }
    }

    private fun setupDialpadBeeps() {
        updateWrapperToneVolume()
        binding.apply {
            settingsDialpadBeeps.isChecked = config.dialpadBeeps
            settingsDialpadBeepsHolder.setOnClickListener {
                settingsDialpadBeeps.toggle()
                config.dialpadBeeps = settingsDialpadBeeps.isChecked
                toneVolumeWrapper.beVisibleIf(config.dialpadBeeps)
                updateWrapperToneVolume()
            }
        }
    }

    private fun updateWrapperToneVolume() {
        val getBottomNavigationBackgroundColor = getBottomNavigationBackgroundColor()
        val wrapperColor = if (config.dialpadBeeps) getBottomNavigationBackgroundColor.lightenColor(4) else getBottomNavigationBackgroundColor
        binding.settingsDialpadBeepsWrapper.background.applyColorFilter(wrapperColor)
    }

    private fun setupToneVolume() {
        binding.apply {
            toneVolumeWrapper.beVisibleIf(config.dialpadBeeps)

            val progress = config.toneVolume
            toneVolumeSeekBar.progress = progress
            val textProgress = "$progress %"
            toneVolumeValue.text = textProgress

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                toneVolumeSeekBar.min = 1
            }

            toneVolumeMinus.setOnClickListener {
                toneVolumeSeekBar.progress = toneVolumeSeekBar.progress - 1
            }
            toneVolumeValue.setOnClickListener {
                toneVolumeSeekBar.progress = 80
            }
            toneVolumePlus.setOnClickListener {
                toneVolumeSeekBar.progress = toneVolumeSeekBar.progress + 1
            }

            toneVolumeSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val textPercent = "$progress %"
                    binding.toneVolumeValue.text = textPercent
                    config.toneVolume = progress
                }
            })
        }
    }

    private fun setupPurchaseThankYou() {
        binding.apply {
            updatePro()
            dialpadPurchaseThankYouHolder.setOnClickListener {
                launchPurchase()
            }
            moreButton.setOnClickListener {
                launchPurchase()
            }
            val appDrawable = resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, R.drawable.ic_plus_support, getProperPrimaryColor())
            purchaseLogo.setImageDrawable(appDrawable)
            val drawable = resources.getColoredDrawableWithColor(this@SettingsDialpadActivity, R.drawable.button_gray_bg, getProperPrimaryColor())
            moreButton.background = drawable
            moreButton.setTextColor(getProperBackgroundColor())
            moreButton.setPadding(2, 2, 2, 2)
        }
    }

    private fun updatePro(isPro: Boolean = checkPro()) {
        binding.apply {
            dialpadPurchaseThankYouHolder.beGoneIf(isPro)

            arrayOf(
                settingsSimCardColor1Holder,
                settingsSimCardColor2Holder
            ).forEach {
                it.alpha = if (isPro) 1f else 0.4f
            }
        }
    }

    private fun shakePurchase() {
        RxAnimation.from(binding.dialpadPurchaseThankYouHolder)
            .shake()
            .subscribe()
    }

    private fun updateProducts() {
        val productList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3, subscriptionIdX1, subscriptionIdX2, subscriptionIdX3)
        ruStoreHelper.getProducts(productList)
    }

    private fun handleEventStart(event: StartPurchasesEvent) {
        when (event) {
            is StartPurchasesEvent.PurchasesAvailability -> {
                when (event.availability) {
                    is FeatureAvailabilityResult.Available -> {
                        //Process purchases available
                        updateProducts()
                        ruStoreIsConnected = true
                    }

                    is FeatureAvailabilityResult.Unavailable -> {
                        //toast(event.availability.cause.message ?: "Process purchases unavailable", Toast.LENGTH_LONG)
                    }

                    else -> {}
                }
            }

            is StartPurchasesEvent.Error -> {
                //toast(event.throwable.message ?: "Process unknown error", Toast.LENGTH_LONG)
            }
        }
    }

    private fun checkPro(collection: Boolean = true) = isOrWasThankYouInstalled() || isPro() || isCollection()
}
