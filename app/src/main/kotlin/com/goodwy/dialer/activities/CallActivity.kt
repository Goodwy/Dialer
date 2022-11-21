package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.os.*
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.helpers.setWindowTransparency
import com.goodwy.commons.models.SimpleListItem
import com.goodwy.dialer.R
import com.goodwy.dialer.dialogs.DynamicBottomSheetChooserDialog
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.AudioRoute
import com.goodwy.dialer.models.CallContact
import com.mikhaellopez.rxanimation.*
import com.mikhaellopez.rxanimation.fadeIn
import com.mikhaellopez.rxanimation.fadeOut
import kotlinx.android.synthetic.main.activity_call.*
import kotlinx.android.synthetic.main.activity_call.dialpad_input
import kotlinx.android.synthetic.main.activity_call.dialpad_wrapper
import kotlinx.android.synthetic.main.dialpad.*
import kotlin.math.max
import kotlin.math.min

class CallActivity : SimpleActivity() {
    companion object {
        fun getStartIntent(context: Context): Intent {
            val openAppIntent = Intent(context, CallActivity::class.java)
            openAppIntent.flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            return openAppIntent
        }
    }

    private var isSpeakerOn = false
    private var isMicrophoneOff = false
    private var isCallEnded = false
    private var callContact: CallContact? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var screenOnWakeLock: PowerManager.WakeLock? = null
    private var callDuration = 0
    private val callContactAvatarHelper by lazy { CallContactAvatarHelper(this) }
    private val callDurationHandler = Handler(Looper.getMainLooper())
    private var dragDownX = 0f
    private var stopAnimation = false
    private var viewsUnderDialpad = arrayListOf<Pair<View, Float>>()
    private var dialpadHeight = 0f

    private var audioRouteChooserDialog: DynamicBottomSheetChooserDialog? = null

    @SuppressLint("MissingSuperCall", "MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        if (CallManager.getPhoneState() == NoCall) {
            finish()
            return
        }

        updateTextColors(call_holder)
        initButtons()
        audioManager.mode = AudioManager.MODE_IN_CALL
        addLockScreenFlags()
        CallManager.addListener(callCallback)
        updateCallContactInfo(CallManager.getPrimaryCall())

        if (config.transparentCallScreen && hasPermission(PERMISSION_READ_STORAGE)) {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val wallpaperBlur = BlurFactory.fileToBlurBitmap(wallpaperManager.getDrawable(), this, 0.2f, 25f)
            if (wallpaperBlur != null) {
                val drawable: Drawable = BitmapDrawable(resources, wallpaperBlur)
                call_holder.background = drawable
                call_holder.background.alpha = 60
                if (isQPlus()) {
                    call_holder.background.colorFilter = BlendModeColorFilter(Color.DKGRAY, BlendMode.SOFT_LIGHT)
                } else {
                    call_holder.background.setColorFilter(Color.DKGRAY, PorterDuff.Mode.DARKEN)
                }

                arrayOf(caller_name_label, caller_number, call_status_label, call_decline_label, call_accept_label,
                    dialpad_1, dialpad_2, dialpad_3, dialpad_4, dialpad_5, dialpad_6, dialpad_7, dialpad_8, dialpad_9,
                    dialpad_0, dialpad_plus, dialpad_input,
                    dialpad_2_letters, dialpad_3_letters, dialpad_4_letters, dialpad_5_letters,
                    dialpad_6_letters, dialpad_7_letters, dialpad_8_letters, dialpad_9_letters,
                    on_hold_caller_name, on_hold_label
                ).forEach {
                    it.setTextColor(Color.WHITE)
                }

                arrayOf(call_toggle_microphone, call_toggle_speaker, call_dialpad, dialpad_close, call_sim_image,
                    call_toggle_hold, call_add_contact, call_add, call_swap, call_merge, call_manage, imageView,
                    dialpad_asterisk, dialpad_hashtag
                ).forEach {
                    it.applyColorFilter(Color.WHITE)
                }

                call_sim_id.setTextColor(Color.WHITE.getContrastColor())
            }
            // Прозрачные статусбар и навибар
            setWindowTransparency(false) { statusBarSize, bottomNavigationBarSize, leftNavigationBarSize, rightNavigationBarSize ->
                call_holder.setPadding(leftNavigationBarSize, statusBarSize, rightNavigationBarSize, bottomNavigationBarSize)
                updateStatusbarColor(Color.BLACK)
                updateNavigationBarColor(Color.BLACK)
            }
        } else {
            arrayOf(call_toggle_microphone, call_toggle_speaker, call_dialpad, dialpad_close, call_sim_image,
                call_toggle_hold, call_add_contact, call_add, call_swap, call_merge, call_manage, imageView,
                dialpad_asterisk, dialpad_hashtag
            ).forEach {
                it.applyColorFilter(getProperTextColor())
            }

            call_sim_id.setTextColor(getProperTextColor().getContrastColor())
            dialpad_input.disableKeyboard()

            dialpad_wrapper.onGlobalLayout {
                dialpadHeight = dialpad_wrapper.height.toFloat()
            }
        }

        call_toggle_microphone.background.alpha = 60
        call_toggle_microphone.background.applyColorFilter(Color.GRAY)
        call_dialpad_holder.foreground.alpha = 60
        call_dialpad_holder.foreground.applyColorFilter(Color.GRAY)
        call_toggle_speaker.background.alpha = 60
        call_toggle_speaker.background.applyColorFilter(Color.GRAY)
        call_toggle_hold.background.alpha = 60
        call_toggle_hold.background.applyColorFilter(Color.GRAY)
        call_add_contact_holder.foreground.alpha = 60
        call_add_contact_holder.foreground.applyColorFilter(Color.GRAY)
        call_add_holder.foreground.alpha = 60
        call_add_holder.foreground.applyColorFilter(Color.GRAY)
        call_swap_holder.foreground.alpha = 60
        call_swap_holder.foreground.applyColorFilter(Color.GRAY)
        call_merge_holder.foreground.alpha = 60
        call_merge_holder.foreground.applyColorFilter(Color.GRAY)
        on_hold_status_holder.background.alpha = 60
        on_hold_status_holder.background.applyColorFilter(Color.GRAY)
        arrayOf(dialpad_0_holder, dialpad_1_holder, dialpad_2_holder, dialpad_3_holder, dialpad_4_holder,
            dialpad_5_holder, dialpad_6_holder, dialpad_7_holder, dialpad_8_holder, dialpad_9_holder,
            dialpad_asterisk_holder, dialpad_hashtag_holder
        ).forEach {
            it.foreground.applyColorFilter(Color.GRAY)
            it.foreground.alpha = 60
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        updateState()
    }

    override fun onResume() {
        super.onResume()
        updateState()
        if (config.transparentCallScreen && hasPermission(PERMISSION_READ_STORAGE)) {
            updateStatusbarColor(Color.BLACK)
            updateNavigationBarColor(Color.BLACK)
        }
        //updateNavigationBarColor(getBottomNavigationBackgroundColor())
    }

    @SuppressLint("MissingSuperCall")
    override fun onDestroy() {
        super.onDestroy()
        CallManager.removeListener(callCallback)
        disableProximitySensor()

        if (screenOnWakeLock?.isHeld == true) {
            screenOnWakeLock!!.release()
        }
    }

    override fun onBackPressed() {
        if (dialpad_wrapper.isVisible()) {
            hideDialpad()
            return
        } else {
            super.onBackPressed()
        }

        val callState = CallManager.getState()
        if (callState == Call.STATE_CONNECTING || callState == Call.STATE_DIALING) {
            endCall()
        }
    }

    private fun initButtons() {
        if (config.disableSwipeToAnswer) {
            call_draggable.beGone()
            call_draggable_background.beGone()
            call_left_arrow.beGone()
            call_right_arrow.beGone()

            call_decline.setOnClickListener {
                endCall()
            }

            call_accept.setOnClickListener {
                acceptCall()
            }
        } else {
            call_decline.beGone()
            call_decline_label.beGone()
            call_accept.beGone()
            call_accept_label.beGone()
            handleSwipe()
        }

        call_toggle_microphone.setOnClickListener {
            toggleMicrophone()
        }

        call_toggle_speaker.setOnClickListener {
            //toggleSpeaker()
            changeCallAudioRoute()
        }

        call_dialpad_holder.setOnClickListener {
            toggleDialpadVisibility()
        }

        dialpad_close.setOnClickListener {
            hideDialpad()
        }

        call_add_holder.setOnClickListener {
            Intent(applicationContext, DialpadActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(this)
            }
        }

        call_swap_holder.setOnClickListener {
            CallManager.swap()
        }

        call_merge_holder.setOnClickListener {
            CallManager.merge()
        }

        call_manage.setOnClickListener {
            startActivity(Intent(this, ConferenceActivity::class.java))
        }

        call_toggle_hold.setOnClickListener {
            toggleHold()
        }

        call_add_contact_holder.setOnClickListener {
            addContact()
        }

        call_end.setOnClickListener {
            endCall()
        }

        dialpad_0_holder.setOnClickListener { dialpadPressed('0') }
        dialpad_1_holder.setOnClickListener { dialpadPressed('1') }
        dialpad_2_holder.setOnClickListener { dialpadPressed('2') }
        dialpad_3_holder.setOnClickListener { dialpadPressed('3') }
        dialpad_4_holder.setOnClickListener { dialpadPressed('4') }
        dialpad_5_holder.setOnClickListener { dialpadPressed('5') }
        dialpad_6_holder.setOnClickListener { dialpadPressed('6') }
        dialpad_7_holder.setOnClickListener { dialpadPressed('7') }
        dialpad_8_holder.setOnClickListener { dialpadPressed('8') }
        dialpad_9_holder.setOnClickListener { dialpadPressed('9') }

        dialpad_0_holder.setOnLongClickListener { dialpadPressed('+'); true }
        dialpad_asterisk_holder.setOnClickListener { dialpadPressed('*') }
        dialpad_hashtag_holder.setOnClickListener { dialpadPressed('#') }
        //dialpad_wrapper.setBackgroundColor(getProperBackgroundColor())
        //dialpad_include.setBackgroundColor(getProperBackgroundColor())

        arrayOf(
            call_toggle_microphone, call_toggle_speaker, call_dialpad_holder, call_toggle_hold,
            call_add_holder, call_swap_holder, call_merge_holder, call_manage, call_add_contact_holder
        ).forEach { imageView ->
            imageView.setOnLongClickListener {
                if (!imageView.contentDescription.isNullOrEmpty()) {
                    toast(imageView.contentDescription.toString())
                }
                true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleSwipe() {
        var minDragX = 0f
        var maxDragX = 0f
        var initialDraggableX = 0f
        var initialLeftArrowX = 0f
        var initialRightArrowX = 0f
        var initialLeftArrowScaleX = 0f
        var initialLeftArrowScaleY = 0f
        var initialRightArrowScaleX = 0f
        var initialRightArrowScaleY = 0f
        var leftArrowTranslation = 0f
        var rightArrowTranslation = 0f

        val isRtl = isRTLLayout
        call_accept.onGlobalLayout {
            minDragX = call_draggable_background.left.toFloat() + resources.getDimension(R.dimen.three_dp)
            maxDragX = call_draggable_background.right.toFloat() - call_draggable.height.toFloat() - resources.getDimension(R.dimen.three_dp)
            initialDraggableX = call_draggable.left.toFloat()
            initialLeftArrowX = call_left_arrow.x
            initialRightArrowX = call_right_arrow.x
            initialLeftArrowScaleX = call_left_arrow.scaleX
            initialLeftArrowScaleY = call_left_arrow.scaleY
            initialRightArrowScaleX = call_right_arrow.scaleX
            initialRightArrowScaleY = call_right_arrow.scaleY
            leftArrowTranslation = -call_draggable_background.x
            rightArrowTranslation = call_draggable_background.x

            call_left_arrow.applyColorFilter(getColor(R.color.red_call))
            call_right_arrow.applyColorFilter(getColor(R.color.green_call))

            startArrowAnimation(call_left_arrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
            startArrowAnimation(call_right_arrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
        }

        call_draggable.drawable.mutate().setTint(getColor(R.color.green_call))
        call_draggable_background.background.mutate().setTint(getProperTextColor())

        var lock = false
        call_draggable.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    //call_draggable_background.animate().alpha(0f)
                    stopAnimation = true
                    call_left_arrow.animate().alpha(0f)
                    call_right_arrow.animate().alpha(0f)
                    lock = false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
                    call_draggable.animate().x(initialDraggableX).withEndAction {
                        call_draggable_background.animate().alpha(0.2f)
                    }
                    call_draggable.setImageDrawable(getDrawable(R.drawable.ic_phone_down_vector))
                    call_draggable.drawable.mutate().setTint(getColor(R.color.green_call))
                    call_left_arrow.animate().alpha(1f)
                    call_right_arrow.animate().alpha(1f)
                    stopAnimation = false
                    startArrowAnimation(call_left_arrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
                    startArrowAnimation(call_right_arrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
                }
                MotionEvent.ACTION_MOVE -> {
                    call_draggable.x = min(maxDragX, max(minDragX, event.rawX - dragDownX))
                    when {
                        call_draggable.x >= maxDragX - 50f -> {
                            if (!lock) {
                                lock = true
                                call_draggable.performHapticFeedback()
                                if (isRtl) {
                                    endCall()
                                } else {
                                    acceptCall()
                                }
                            }
                        }
                        call_draggable.x <= minDragX + 50f -> {
                            if (!lock) {
                                lock = true
                                call_draggable.performHapticFeedback()
                                if (isRtl) {
                                    acceptCall()
                                } else {
                                    endCall()
                                }
                            }
                        }
                        call_draggable.x > initialDraggableX -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_down_red_vector
                            } else {
                                R.drawable.ic_phone_green_vector
                            }
                            call_draggable.setImageDrawable(getDrawable(drawableRes))
                        }
                        call_draggable.x <= initialDraggableX -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_green_vector
                            } else {
                                R.drawable.ic_phone_down_red_vector
                            }
                            call_draggable.setImageDrawable(getDrawable(drawableRes))
                        }
                    }
                }
            }
            true
        }
    }

    private fun startArrowAnimation(arrow: ImageView, initialX: Float, initialScaleX: Float, initialScaleY: Float, translation: Float) {
        arrow.apply {
            alpha = 1f
            x = initialX
            scaleX = initialScaleX
            scaleY = initialScaleY
            animate()
                .alpha(0f)
                .translationX(translation)
                .scaleXBy(-0.5f)
                .scaleYBy(-0.5f)
                .setDuration(1000)
                .withEndAction {
                    if (!stopAnimation) {
                        startArrowAnimation(this, initialX, initialScaleX, initialScaleY, translation)
                    }
                }
        }
    }

    private fun dialpadPressed(char: Char) {
        CallManager.keypad(this, char)
        dialpad_input.addCharacter(char)
    }

    private fun changeCallAudioRoute() {
        val supportAudioRoutes = CallManager.getSupportedAudioRoutes()
        if (supportAudioRoutes.contains(AudioRoute.BLUETOOTH)) {
            createOrUpdateAudioRouteChooser(supportAudioRoutes)
        } else {
            val isSpeakerOn = !isSpeakerOn
            val newRoute = if (isSpeakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            CallManager.setAudioRoute(newRoute)
        }
    }

    private fun createOrUpdateAudioRouteChooser(routes: Array<AudioRoute>, create: Boolean = true) {
        val callAudioRoute = CallManager.getCallAudioRoute()
        val items = routes
            .sortedByDescending { it.route }
            .map {
                SimpleListItem(id = it.route, textRes = it.stringRes, imageRes = it.iconRes, selected = it == callAudioRoute)
            }
            .toTypedArray()

        if (audioRouteChooserDialog?.isVisible == true) {
            audioRouteChooserDialog?.updateChooserItems(items)
        } else if (create) {
            audioRouteChooserDialog = DynamicBottomSheetChooserDialog.createChooser(
                fragmentManager = supportFragmentManager,
                title = R.string.choose_audio_route,
                items = items
            ) {
                audioRouteChooserDialog = null
                CallManager.setAudioRoute(it.id)
            }
        }
    }

    private fun updateCallAudioState(route: AudioRoute?) {
        if (route != null) {
            isSpeakerOn = route == AudioRoute.SPEAKER
            val supportedAudioRoutes = CallManager.getSupportedAudioRoutes()
            call_toggle_speaker.apply {
                val bluetoothConnected = supportedAudioRoutes.contains(AudioRoute.BLUETOOTH)
                contentDescription = if (bluetoothConnected) {
                    getString(R.string.choose_audio_route)
                } else {
                    getString(if (isSpeakerOn) R.string.turn_speaker_off else R.string.turn_speaker_on)
                }
                // show speaker icon when a headset is connected, a headset icon maybe confusing to some
                if (route == AudioRoute.WIRED_HEADSET || route == AudioRoute.EARPIECE) {
                    setImageResource(R.drawable.ic_volume_down_vector)
                } else {
                    setImageResource(route.iconRes)
                }
            }
            toggleButtonColor(call_toggle_speaker, enabled = route != AudioRoute.EARPIECE && route != AudioRoute.WIRED_HEADSET)
            createOrUpdateAudioRouteChooser(supportedAudioRoutes, create = false)

            if (isSpeakerOn) {
                disableProximitySensor()
            } else {
                enableProximitySensor()
            }
        }
    }

    // OLD
    /*private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        val drawable = if (isSpeakerOn) R.drawable.ic_volume_up_vector else R.drawable.ic_volume_down_vector
        call_toggle_speaker.setImageDrawable(getDrawable(drawable))
        audioManager.isSpeakerphoneOn = isSpeakerOn

        if (config.transparentCallScreen && hasPermission(PERMISSION_READ_STORAGE)) {
            val color = if (isSpeakerOn) Color.WHITE else Color.GRAY
            call_toggle_speaker.background.applyColorFilter(color)
            val colorIcon = if (isSpeakerOn) Color.BLACK else Color.WHITE
            call_toggle_speaker.applyColorFilter(colorIcon)
        }
        call_toggle_speaker.background.alpha = if (isSpeakerOn) 255 else 60

        val newRoute = if (isSpeakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        CallManager.inCallService?.setAudioRoute(newRoute)
        call_toggle_speaker.contentDescription = getString(if (isSpeakerOn) R.string.turn_speaker_off else R.string.turn_speaker_on)

        if (isSpeakerOn) {
            disableProximitySensor()
        } else {
            enableProximitySensor()
        }
    }*/

    private fun toggleMicrophone() {
        isMicrophoneOff = !isMicrophoneOff
        val drawable = if (!isMicrophoneOff) R.drawable.ic_microphone_vector else R.drawable.ic_microphone_off_vector
        call_toggle_microphone.setImageDrawable(getDrawable(drawable))

        if (config.transparentCallScreen && hasPermission(PERMISSION_READ_STORAGE)) {
            val color = if (isMicrophoneOff) Color.WHITE else Color.GRAY
            call_toggle_microphone.background.applyColorFilter(color)
            val colorIcon = if (isMicrophoneOff) Color.BLACK else Color.WHITE
            call_toggle_microphone.applyColorFilter(colorIcon)
        }
        call_toggle_microphone.background.alpha = if (isMicrophoneOff) 255 else 60

        audioManager.isMicrophoneMute = isMicrophoneOff
        CallManager.inCallService?.setMuted(isMicrophoneOff)
        call_toggle_microphone.contentDescription = getString(if (isMicrophoneOff) R.string.turn_microphone_on else R.string.turn_microphone_off)
    }

    private fun toggleDialpadVisibility() {
        if (dialpad_wrapper.isVisible()) hideDialpad() else showDialpad()
    }

    private fun showDialpad() {
        dialpad_wrapper.beVisible()
        dialpad_close.beVisible()
        arrayOf(caller_avatar, caller_name_label, caller_number, call_status_label,
            call_sim_image, call_sim_id, call_toggle_microphone, call_dialpad_holder,
            call_toggle_speaker, call_add_contact_holder, call_manage).forEach {
            it.beGone()
        }
        controls_single_call.beGone()
        controls_two_calls.beGone()

        RxAnimation.together(
            dialpad_wrapper.scale(1f),
            dialpad_wrapper.fadeIn(),
            dialpad_close.fadeIn()
        ).doAfterTerminate {
        }.subscribe()
    }

    @SuppressLint("MissingPermission")
    private fun hideDialpad() {
        RxAnimation.together(
            dialpad_wrapper.scale(0.7f),
            dialpad_wrapper.fadeOut(),
            dialpad_close.fadeOut()
        ).doAfterTerminate {
            dialpad_wrapper.beGone()
            dialpad_close.beGone()
            arrayOf(caller_avatar, caller_name_label, caller_number, call_status_label,
                call_toggle_microphone, call_dialpad_holder,
                call_toggle_speaker, call_add_contact_holder).forEach {
                it.beVisible()
            }
            val accounts = telecomManager.callCapablePhoneAccounts
            call_sim_image.beVisibleIf(accounts.size > 1)
            call_sim_id.beVisibleIf(accounts.size > 1)
            updateState()
        }.subscribe()
    }

    private fun toggleHold() {
        val isOnHold = CallManager.toggleHold()
        val drawable = if (isOnHold) R.drawable.ic_pause_crossed_vector else R.drawable.ic_pause_vector
        call_toggle_hold.setImageDrawable(getDrawable(drawable))
        call_toggle_hold.contentDescription = getString(if (isOnHold) R.string.resume_call else R.string.hold_call)
        hold_status_label.beVisibleIf(isOnHold)
        RxAnimation.from(hold_status_label)
            .shake()
            .subscribe()

        if (config.transparentCallScreen && hasPermission(PERMISSION_READ_STORAGE)) {
            val color = if (isOnHold) Color.WHITE else Color.GRAY
            call_toggle_hold.background.applyColorFilter(color)
            val colorIcon = if (isOnHold) Color.BLACK else Color.WHITE
            call_toggle_hold.applyColorFilter(colorIcon)
        }
        call_toggle_hold.background.alpha = if (isOnHold) 255 else 60
    }

    private fun addContact() {
        val number = if (callContact!!.number.isNotEmpty()) callContact!!.number else ""
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, number)
            launchActivityIntent(this)
        }
    }

    private fun updateOtherPersonsInfo(avatar: Bitmap?) {
        if (callContact == null) {
            return
        }

        caller_name_label.text = if (callContact!!.name.isNotEmpty()) callContact!!.name else getString(R.string.unknown_caller)
        if (callContact!!.number.isNotEmpty() && callContact!!.number != callContact!!.name) {
            caller_number.text = callContact!!.number

            if (callContact!!.numberLabel.isNotEmpty()) {
                caller_number.text = "${callContact!!.number} - ${callContact!!.numberLabel}"
            }
        } else {
            val country = getCountryByNumber(this, callContact!!.number)
            if (country != "") {
                caller_number.text = country
            } else caller_number.beGone()
        }

        if (avatar != null) {
            caller_avatar.setImageBitmap(avatar)
        } else {
            caller_avatar.setImageDrawable(null)
        }
    }

    private fun getContactNameOrNumber(contact: CallContact): String {
        return contact.name.ifEmpty {
            contact.number.ifEmpty {
                getString(R.string.unknown_caller)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkCalledSIMCard() {
        try {
            val accounts = telecomManager.callCapablePhoneAccounts
            if (accounts.size > 1) {
                accounts.forEachIndexed { index, account ->
                    if (account == CallManager.getPrimaryCall()?.details?.accountHandle) {
                        call_sim_id.text = "${index + 1}"
                        call_sim_id.beVisible()
                        call_sim_image.beVisible()

                        val acceptDrawableId = when (index) {
                            0 -> R.drawable.ic_phone_one_vector
                            1 -> R.drawable.ic_phone_two_vector
                            else -> R.drawable.ic_phone_vector
                        }

                        val rippleBg = resources.getDrawable(R.drawable.ic_call_accept, theme) as RippleDrawable
                        val layerDrawable = rippleBg.findDrawableByLayerId(R.id.accept_call_background_holder) as LayerDrawable
                        layerDrawable.setDrawableByLayerId(R.id.accept_call_icon, getDrawable(acceptDrawableId))
                        call_accept.setImageDrawable(rippleBg)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun updateCallState(call: Call) {
        val state = call.getStateCompat()
        when (state) {
            Call.STATE_RINGING -> callRinging()
            Call.STATE_ACTIVE -> callStarted()
            Call.STATE_DISCONNECTED -> endCall()
            Call.STATE_CONNECTING, Call.STATE_DIALING -> initOutgoingCallUI()
            Call.STATE_SELECT_PHONE_ACCOUNT -> showPhoneAccountPicker()
        }

        val statusTextId = when (state) {
            Call.STATE_RINGING -> R.string.is_calling
            Call.STATE_CONNECTING, Call.STATE_DIALING -> R.string.dialing
            else -> 0
        }

        if (statusTextId != 0) {
            call_status_label.text = getString(statusTextId)
        }

        call_manage.beVisibleIf(call.hasCapability(Call.Details.CAPABILITY_MANAGE_CONFERENCE))
        if (dialpad_wrapper.isGone()) {
            setActionButtonEnabled(call_swap_holder, state == Call.STATE_ACTIVE)
            setActionButtonEnabled(call_merge_holder, state == Call.STATE_ACTIVE)
        }
    }

    private fun updateState() {
        val phoneState = CallManager.getPhoneState()
        if (phoneState is SingleCall) {
            updateCallState(phoneState.call)
            updateCallOnHoldState(null)
            val state = phoneState.call.getStateCompat()
            val isSingleCallActionsEnabled = (state == Call.STATE_ACTIVE || state == Call.STATE_DISCONNECTED
                || state == Call.STATE_DISCONNECTING || state == Call.STATE_HOLDING)
            if (dialpad_wrapper.isGone()) {
                setActionImageViewEnabled(call_toggle_hold, isSingleCallActionsEnabled)
                setActionButtonEnabled(call_add_holder, isSingleCallActionsEnabled)
            }
        } else if (phoneState is TwoCalls) {
            updateCallState(phoneState.active)
            updateCallOnHoldState(phoneState.onHold)
        }

        updateCallAudioState(CallManager.getCallAudioRoute())
    }

    private fun updateCallOnHoldState(call: Call?) {
        val hasCallOnHold = call != null
        if (hasCallOnHold) {
            getCallContact(applicationContext, call) { contact ->
                runOnUiThread {
                    on_hold_caller_name.text = getContactNameOrNumber(contact)
                }
            }
        }
        on_hold_status_holder.beVisibleIf(hasCallOnHold)
        controls_single_call.beVisibleIf(!hasCallOnHold && dialpad_wrapper.isGone())
        controls_two_calls.beVisibleIf(hasCallOnHold && dialpad_wrapper.isGone())
    }

    private fun updateCallContactInfo(call: Call?) {
        getCallContact(applicationContext, call) { contact ->
            if (call != CallManager.getPrimaryCall()) {
                return@getCallContact
            }
            callContact = contact
            val avatar = if (!call.isConference()) callContactAvatarHelper.getCallContactAvatar(contact) else null
            runOnUiThread {
                updateOtherPersonsInfo(avatar)
                checkCalledSIMCard()
            }
        }
    }

    private fun acceptCall() {
        CallManager.accept()
    }

    private fun initOutgoingCallUI() {
        enableProximitySensor()
        incoming_call_holder.beGone()
        ongoing_call_holder.beVisible()
    }

    private fun callRinging() {
        incoming_call_holder.beVisible()
    }

    private fun callStarted() {
        enableProximitySensor()
        incoming_call_holder.beGone()
        ongoing_call_holder.beVisible()
        callDurationHandler.removeCallbacks(updateCallDurationTask)
        callDurationHandler.post(updateCallDurationTask)
    }

    private fun showPhoneAccountPicker() {
        if (callContact != null) {
            getHandleToUse(intent, callContact!!.number) { handle ->
                CallManager.getPrimaryCall()?.phoneAccountSelected(handle, false)
            }
        }
    }

    private fun endCall() {
        CallManager.reject()
        disableProximitySensor()
        audioRouteChooserDialog?.dismissAllowingStateLoss()

        if (isCallEnded) {
            finishAndRemoveTask()
            return
        }

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (ignored: Exception) {
        }

        isCallEnded = true
        if (callDuration > 0) {
            runOnUiThread {
                call_status_label.text = "${callDuration.getFormattedDuration()} (${getString(R.string.call_ended)})"
                Handler().postDelayed({
                    finishAndRemoveTask()
                }, 3000)
            }
        } else {
            call_status_label.text = getString(R.string.call_ended)
            finish()
        }
    }

    private val callCallback = object : CallManagerListener {
        override fun onStateChanged() {
            updateState()
        }

        override fun onAudioStateChanged(audioState: AudioRoute) {
            updateCallAudioState(audioState)
        }

        override fun onPrimaryCallChanged(call: Call) {
            callDurationHandler.removeCallbacks(updateCallDurationTask)
            updateCallContactInfo(call)
            updateState()
        }
    }

    private val updateCallDurationTask = object : Runnable {
        override fun run() {
            callDuration = CallManager.getPrimaryCall().getCallDuration()
            if (!isCallEnded) {
                call_status_label.text = callDuration.getFormattedDuration()
                callDurationHandler.postDelayed(this, 1000)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun addLockScreenFlags() {
        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        if (isOreoPlus()) {
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            screenOnWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "com.goodwy.dialer:full_wake_lock")
            screenOnWakeLock!!.acquire(5 * 1000L)
        } catch (e: Exception) {
        }
    }

    private fun enableProximitySensor() {
        if (!config.disableProximitySensor && (proximityWakeLock == null || proximityWakeLock?.isHeld == false)) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "com.goodwy.dialer:wake_lock")
            proximityWakeLock!!.acquire(60 * MINUTE_SECONDS * 1000L)
        }
    }

    private fun disableProximitySensor() {
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock!!.release()
        }
    }

    private fun setActionButtonEnabled(button: LinearLayout, enabled: Boolean) {
        button.apply {
            isEnabled = enabled
            alpha = if (enabled) 1.0f else LOWER_ALPHA
        }
    }

    private fun setActionImageViewEnabled(button: ImageView, enabled: Boolean) {
        button.apply {
            isEnabled = enabled
            alpha = if (enabled) 1.0f else LOWER_ALPHA
        }
    }

    private fun getActiveButtonColor() = getProperPrimaryColor()

    private fun getInactiveButtonColor() = getProperTextColor().adjustAlpha(0.10f)

    private fun toggleButtonColor(view: ImageView, enabled: Boolean) {
        /*if (enabled) {
            val color = getActiveButtonColor()
            view.background.applyColorFilter(color)
            view.applyColorFilter(color.getContrastColor())
        } else {
            view.background.applyColorFilter(getInactiveButtonColor())
            view.applyColorFilter(getProperBackgroundColor().getContrastColor())
        }*/

        if (config.transparentCallScreen && hasPermission(PERMISSION_READ_STORAGE)) {
            val color = if (enabled) Color.WHITE else Color.GRAY
            view.background.applyColorFilter(color)
            val colorIcon = if (enabled) Color.BLACK else Color.WHITE
            view.applyColorFilter(colorIcon)
        }
        view.background.alpha = if (enabled) 255 else 60
    }
}
