package com.goodwy.dialer.activities

import android.animation.Animator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony.Sms.Intents.SECRET_CODE_ACTION
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.isVisible
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.dialer.R
import com.goodwy.dialer.adapters.ContactsAdapter
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.DIALPAD_TONE_LENGTH_MS
import com.goodwy.dialer.helpers.ToneGeneratorHelper
import com.goodwy.dialer.models.SpeedDial
import kotlinx.android.synthetic.main.activity_dialpad.*
import kotlinx.android.synthetic.main.activity_dialpad.dialpad_holder
import kotlinx.android.synthetic.main.dialpad.*
import java.util.*
import kotlin.math.roundToInt
import android.animation.AnimatorListenerAdapter
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.dialer.helpers.CallManager
import com.goodwy.dialer.helpers.NoCall
import com.google.gson.Gson
import me.grantland.widget.AutofitHelper

class DialpadActivity : SimpleActivity() {
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

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialpad)
        hasRussianLocale = Locale.getDefault().language == "ru"

        updateMaterialActivityViews(dialpad_coordinator, dialpad_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        //setupMaterialScrollListener(dialpad_list, dialpad_toolbar)
        updateNavigationBarColor(getProperBackgroundColor())

        if (checkAppSideloading()) {
            return
        }

        if (config.hideDialpadNumbers) {
            dialpad_1_holder.isVisible = false
            dialpad_2_holder.isVisible = false
            dialpad_3_holder.isVisible = false
            dialpad_4_holder.isVisible = false
            dialpad_5_holder.isVisible = false
            dialpad_6_holder.isVisible = false
            dialpad_7_holder.isVisible = false
            dialpad_8_holder.isVisible = false
            dialpad_9_holder.isVisible = false
            //dialpad_plus_holder.isVisible = true
            dialpad_0_holder.visibility = View.INVISIBLE
        }

        /*arrayOf(
            dialpad_0_holder,
            dialpad_1_holder,
            dialpad_2_holder,
            dialpad_3_holder,
            dialpad_4_holder,
            dialpad_5_holder,
            dialpad_6_holder,
            dialpad_7_holder,
            dialpad_8_holder,
            dialpad_9_holder,
            //dialpad_plus_holder,
            dialpad_asterisk_holder,
            dialpad_hashtag_holder
        ).forEach {
            it.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
            it.background?.alpha = LOWER_ALPHA_INT
        }*/

        speedDialValues = config.getSpeedDialValues()
        privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)

        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)

        if (hasRussianLocale) {
            initRussianChars()
            dialpad_2_letters.append("\nАБВГ")
            dialpad_3_letters.append("\nДЕЁЖЗ")
            dialpad_4_letters.append("\nИЙКЛ")
            dialpad_5_letters.append("\nМНОП")
            dialpad_6_letters.append("\nРСТУ")
            dialpad_7_letters.append("\nФХЦЧ")
            dialpad_8_letters.append("\nШЩЪЫ")
            dialpad_9_letters.append("\nЬЭЮЯ")

            val fontSizeRu = getTextSize() - 12f//resources.getDimension(R.dimen.small_text_size)
            arrayOf(
                dialpad_2_letters, dialpad_3_letters, dialpad_4_letters, dialpad_5_letters, dialpad_6_letters, dialpad_7_letters, dialpad_8_letters,
                dialpad_9_letters
            ).forEach {
                it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeRu)
            }
        }

        dialpad_input.onTextChangeListener { dialpadValueChanged(it) }
        dialpad_input.requestFocus()
        AutofitHelper.create(dialpad_input)
        //ContactsHelper(this).getContacts{ gotContacts(it) }
        dialpad_input.disableKeyboard()

//        ContactsHelper(this).getContacts { allContacts ->
//            val contactsWithNumber = allContacts.filter { it.phoneNumbers.isNotEmpty() }.toMutableList() as ArrayList<Contact>
//            gotContacts(contactsWithNumber)
//        }
        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { allContacts ->
            gotContacts(allContacts)
        }
    }

    override fun onRestart() {
        super.onRestart()
        speedDialValues = config.getSpeedDialValues()
    }

    private fun initLetters() {
        if (config.hideDialpadLetters) {
            arrayOf(
                dialpad_2_letters, dialpad_3_letters, dialpad_4_letters, dialpad_5_letters, dialpad_6_letters, dialpad_7_letters, dialpad_8_letters,
                dialpad_9_letters
            ).forEach {
                it.beGone()
            }
        } else {
            arrayOf(
                dialpad_2_letters, dialpad_3_letters, dialpad_4_letters, dialpad_5_letters, dialpad_6_letters, dialpad_7_letters, dialpad_8_letters,
                dialpad_9_letters
            ).forEach {
                it.beVisible()
            }

            hasRussianLocale = Locale.getDefault().language == "ru"
            if (!hasRussianLocale) {
                val fontSize = getTextSize() - 8f//resources.getDimension(R.dimen.small_text_size)
                arrayOf(
                    dialpad_2_letters, dialpad_3_letters, dialpad_4_letters, dialpad_5_letters, dialpad_6_letters, dialpad_7_letters, dialpad_8_letters,
                    dialpad_9_letters
                ).forEach {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                }
            }
        }

        //dialpad_holder.setOnClickListener { dialpad_input.setText(getTextFromClipboard()); true }
        setupCharClick(dialpad_1_holder, '1')
        setupCharClick(dialpad_2_holder, '2')
        setupCharClick(dialpad_3_holder, '3')
        setupCharClick(dialpad_4_holder, '4')
        setupCharClick(dialpad_5_holder, '5')
        setupCharClick(dialpad_6_holder, '6')
        setupCharClick(dialpad_7_holder, '7')
        setupCharClick(dialpad_8_holder, '8')
        setupCharClick(dialpad_9_holder, '9')
        setupCharClick(dialpad_0_holder, '0')
        //setupCharClick(dialpad_plus_holder, '+', longClickable = false)
        setupCharClick(dialpad_asterisk_holder, '*', longClickable = false)
        setupCharClick(dialpad_hashtag_holder, '#', longClickable = false)
        dialpad_down_holder.setOnClickListener { dialpadHide() }
        dialpad_add_number.setOnClickListener { addNumberToContact() }
        dialpad_clear_char_holder.setOnClickListener { clearChar(it) }
        dialpad_clear_char_holder.setOnLongClickListener { clearInput(); true }
        dialpad_call_button.setOnClickListener { initCall(dialpad_input.value, 0) }
        dialpad_call_button.setOnLongClickListener {
            if (dialpad_input.value.isEmpty()) {dialpad_input.setText(getTextFromClipboard()); true}
            else {copyNumber(); true}
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        super.onResume()
        speedDialValues = config.getSpeedDialValues()
        initLetters()
        setupOptionsMenu()
        updateTextColors(dialpad_holder)
        dialpad_clear_char.applyColorFilter(getProperTextColor())
        //updateNavigationBarColor(getProperBackgroundColor())
        setupToolbar(dialpad_toolbar, NavigationIcon.Arrow, navigationClick = false)

        arrayOf(dialpad_asterisk, dialpad_hashtag).forEach {
            it.applyColorFilter(getProperTextColor())
        }
        dialpad_wrapper.setBackgroundColor(getProperBackgroundColor())
        dialpad_buttom_bg.setBackgroundColor(getProperBackgroundColor())
        dialpad_add_number.setTextColor(getProperPrimaryColor())
        //dialpad_holder.setBackgroundColor(getProperBackgroundColor())
        dialpad_down.applyColorFilter(getProperTextColor())
        dialpad_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!hasBeenScrolled) {
                    hasBeenScrolled = true
                    slideDown(dialpad_wrapper)
                }
            }
        })

        val properPrimaryColor = getProperPrimaryColor()
        val callIconId = if (areMultipleSIMsAvailable()) {
            val callIcon = resources.getColoredDrawableWithColor(R.drawable.ic_phone_two_vector, properPrimaryColor.getContrastColor())
            dialpad_call_two_button.setImageDrawable(callIcon)
            dialpad_call_two_button.background.applyColorFilter(properPrimaryColor)
            dialpad_call_two_button.beVisible()
            dialpad_call_two_button.setOnClickListener {
                initCall(dialpad_input.value, 1)
            }

            R.drawable.ic_phone_one_vector
        } else {
            R.drawable.ic_phone_vector
        }

        val callIcon = resources.getColoredDrawableWithColor(callIconId, properPrimaryColor.getContrastColor())
        dialpad_call_button.setImageDrawable(callIcon)
        dialpad_call_button.background.applyColorFilter(config.accentColor)

        letter_fastscroller.textColor = getProperTextColor().getColorStateList()
        letter_fastscroller.pressedTextColor = properPrimaryColor
        letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
        letter_fastscroller_thumb.textColor = properPrimaryColor.getContrastColor()
        letter_fastscroller_thumb.thumbColor = properPrimaryColor.getColorStateList()

        invalidateOptionsMenu()
    }

    private fun setupOptionsMenu() {
        dialpad_toolbar.menu.findItem(R.id.settings).setIcon(getSettingsIcon(config.settingsIcon))
        dialpad_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
                R.id.add_number_to_contact -> addNumberToContact()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
        dialpad_toolbar.setNavigationOnClickListener {
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
        val clip = dialpad_input.value
        copyToClipboard(clip)
    }

    private fun checkDialIntent(): Boolean {
        return if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) && intent.data != null && intent.dataString?.contains("tel:") == true) {
            val number = Uri.decode(intent.dataString).substringAfter("tel:")
            dialpad_input.setText(number)
            dialpad_input.setSelection(number.length)
            true
        } else {
            false
        }
    }

    private fun addNumberToContact() {
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, dialpad_input.value)
            launchActivityIntent(this)
        }
    }

    private fun dialpadPressed(char: Char, view: View?) {
        dialpad_input.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun dialpadHide() {
        if (dialpad_wrapper.visibility == View.VISIBLE) {
            slideDown(dialpad_wrapper)
        } else {
            slideUp(dialpad_wrapper)
        }
    }

    fun slideDown(view: View) {
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
        dialpad_down.setImageDrawable(resources.getDrawable(R.drawable.ic_chevron_up_vector))
    }

    fun slideUp(view: View) {
        view.visibility = View.VISIBLE
        //view.alpha = 0f
        if (view.height > 0) {
            slideUpNow(view)
        } else {
            // wait till height is measured
            view.post { slideUpNow(view) }
        }
        dialpad_down.setImageDrawable(resources.getDrawable(R.drawable.ic_chevron_down_vector))
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
        dialpad_input.dispatchKeyEvent(dialpad_input.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInput() {
        dialpad_input.setText("")
    }

    private fun gotContacts(newContacts: ArrayList<Contact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        runOnUiThread {
            if (!checkDialIntent() && dialpad_input.value.isEmpty()) {
                dialpadValueChanged("")
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun dialpadValueChanged(text: String) {
        val len = text.length
        if (len == 0 && dialpad_wrapper.visibility == View.GONE) {
            slideUp(dialpad_wrapper)
            dialpad_down.setImageDrawable(resources.getDrawable(R.drawable.ic_chevron_down_vector))
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

        (dialpad_list.adapter as? ContactsAdapter)?.finishActMode()

        val filtered = allContacts.filter {
            var convertedName = PhoneNumberUtils.convertKeypadLettersToDigits(it.name.normalizeString())

            if (hasRussianLocale) {
                var currConvertedName = ""
                convertedName.toLowerCase().forEach { char ->
                    val convertedChar = russianCharsMap.getOrElse(char) { char }
                    currConvertedName += convertedChar
                }
                convertedName = currConvertedName
            }

            it.doesContainPhoneNumber(text, true, true) || (convertedName.contains(text, true))
        }.sortedWith(compareBy {
            !it.doesContainPhoneNumber(text, true, true)
        }).toMutableList() as ArrayList<Contact>

        letter_fastscroller.setupWithRecyclerView(dialpad_list, { position ->
            try {
                val name = filtered[position].getNameToDisplay()
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.toUpperCase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })

        ContactsAdapter(this, filtered, dialpad_list, null, text, showNumber = true, allowLongClick = false) {
            val contact = it as Contact
//            if (contact.phoneNumbers.size <= 1) {
//                if (config.showCallConfirmation) {
//                    CallConfirmationDialog(this@DialpadActivity, contact.getNameToDisplay()) {
//                        startCallIntent(contact.phoneNumbers.first().normalizedNumber)
//                    }
//                }else{
//                    startCallIntent(contact.phoneNumbers.first().normalizedNumber)
//                }
//            } else {
//                val items = ArrayList<RadioItem>()
//                contact.phoneNumbers.forEachIndexed { index, phoneNumber ->
//                    items.add(RadioItem(index, phoneNumber.normalizedNumber))
//                }
//
//                RadioGroupDialog(this, items) { item ->
//                    startCallIntent(contact.phoneNumbers[item as Int].normalizedNumber)
//                }
//            }
            if (config.showCallConfirmation) {
                CallConfirmationDialog(this@DialpadActivity, contact.getNameToDisplay()) {
                    startCallIntent(contact.getPrimaryNumber() ?: return@CallConfirmationDialog)
                }
            } else {
                startCallIntent(contact.getPrimaryNumber() ?: return@ContactsAdapter)
            }
        }.apply {
            dialpad_list.adapter = this
        }

        dialpad_add_number.beVisibleIf(dialpad_input.value.isNotEmpty())
        dialpad_add_number.setTextColor(getProperPrimaryColor())
        dialpad_placeholder.beVisibleIf(filtered.isEmpty())
        dialpad_list.beVisibleIf(filtered.isNotEmpty())
        dialpad_down_holder.beVisibleIf(dialpad_input.value.isNotEmpty())
        dialpad_clear_char_holder.beVisibleIf(dialpad_input.value.isNotEmpty())
        dialpad_input.beVisibleIf(dialpad_input.value.isNotEmpty())
        //dialpad_wrapper.beVisibleIf(dialpad_input.value.isEmpty())
        dialpad_list.beVisibleIf(dialpad_input.value.isNotEmpty())
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && isDefaultDialer()) {
            dialpadValueChanged(dialpad_input.value)
        }
    }

    private fun initCall(number: String = dialpad_input.value, handleIndex: Int) {
        if (number.isNotEmpty()) {
            if (handleIndex != -1 && areMultipleSIMsAvailable()) {
                if (config.showCallConfirmation) {
                    CallConfirmationDialog(this, number) {
                        callContactWithSim(number, handleIndex == 0)
                    }
                } else {
                    callContactWithSim(number, handleIndex == 0)
                }
            } else {
                if (config.showCallConfirmation) {
                    CallConfirmationDialog(this, number) {
                        startCallIntent(number)
                    }
                } else {
                    startCallIntent(number)
                }
            }
        }
    }

    private fun speedDial(id: Int): Boolean {
        if (dialpad_input.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, -1)
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
}
