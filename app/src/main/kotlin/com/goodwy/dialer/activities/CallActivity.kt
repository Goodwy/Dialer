package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.SimpleListItem
import com.goodwy.dialer.R
import com.goodwy.dialer.databinding.ActivityCallBinding
import com.goodwy.dialer.dialogs.ChangeTextDialog
import com.goodwy.dialer.dialogs.DynamicBottomSheetChooserDialog
import com.goodwy.dialer.extensions.*
import com.goodwy.dialer.helpers.*
import com.goodwy.dialer.models.*
import com.mikhaellopez.rxanimation.*
import com.mikhaellopez.rxanimation.fadeIn
import com.mikhaellopez.rxanimation.fadeOut
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class CallActivity : SimpleActivity() {
    companion object {
        fun getStartIntent(context: Context, needSelectSIM: Boolean = false): Intent {
            val openAppIntent = Intent(context, CallActivity::class.java)
            openAppIntent.putExtra(NEED_SELECT_SIM, needSelectSIM)
            openAppIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT //Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT --removed it, it can cause a full screen ringing instead of notifications
            return openAppIntent
        }
    }

    private val binding by viewBinding(ActivityCallBinding::inflate)

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
    private var dialpadHeight = 0f
    private var needSelectSIM = false //true - if the call is called from a third-party application not via ACTION_CALL, for example, this is how MIUI applications do it.

    private var audioRouteChooserDialog: DynamicBottomSheetChooserDialog? = null

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        addLockScreenFlags()
        showTransparentTop = true
        updateNavigationBarColor = false
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (CallManager.getPhoneState() == NoCall) {
            finish()
            return
        }

        needSelectSIM = intent.getBooleanExtra(NEED_SELECT_SIM, false)
        if (needSelectSIM) initOutgoingCall(CallManager.getPrimaryCall()!!.details.handle)

        initButtons()
        audioManager.mode = AudioManager.MODE_IN_CALL
        CallManager.addListener(callCallback)
        updateTextColors(binding.callHolder)

        if (config.backgroundCallScreen == TRANSPARENT_BACKGROUND) checkPermission()

        val configBackgroundCallScreen = config.backgroundCallScreen
        if (configBackgroundCallScreen == TRANSPARENT_BACKGROUND || configBackgroundCallScreen == BLACK_BACKGROUND ||
            configBackgroundCallScreen == BLUR_AVATAR || configBackgroundCallScreen == AVATAR) {
            updateStatusbarColor(Color.BLACK)
            updateNavigationBarColor(Color.BLACK)

            if (configBackgroundCallScreen == BLACK_BACKGROUND) {
                binding.callHolder.setBackgroundColor(Color.BLACK)
            } else {
                binding.callHolder.setBackgroundColor(resources.getColor(R.color.default_call_background))
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (configBackgroundCallScreen == TRANSPARENT_BACKGROUND && hasPermission(PERMISSION_READ_STORAGE)) {
                    val wallpaperManager = WallpaperManager.getInstance(this)
                    val wallpaperBlur = BlurFactory.fileToBlurBitmap(wallpaperManager.drawable!!, this, 0.2f, 25f)
                    if (wallpaperBlur != null) {
                        val drawable: Drawable = BitmapDrawable(resources, wallpaperBlur)
                        binding.callHolder.background = drawable
                        binding.callHolder.background.alpha = 60
                        if (isQPlus()) {
                            binding.callHolder.background.colorFilter = BlendModeColorFilter(Color.DKGRAY, BlendMode.SOFT_LIGHT)
                        } else {
                            binding.callHolder.background.setColorFilter(Color.DKGRAY, PorterDuff.Mode.DARKEN)
                        }
                    }
                }
            }

            binding.apply {
                arrayOf(
                    callerNameLabel, callerDescription, callerNumber, callerNotes, callStatusLabel, callDeclineLabel, callAcceptLabel,
                    dialpadInclude.dialpad1, dialpadInclude.dialpad2, dialpadInclude.dialpad3, dialpadInclude.dialpad4,
                    dialpadInclude.dialpad5, dialpadInclude.dialpad6, dialpadInclude.dialpad7, dialpadInclude.dialpad8,
                    dialpadInclude.dialpad9, dialpadInclude.dialpad0, dialpadInclude.dialpadPlus, dialpadInput,
                    dialpadInclude.dialpad2Letters, dialpadInclude.dialpad3Letters, dialpadInclude.dialpad4Letters,
                    dialpadInclude.dialpad5Letters, dialpadInclude.dialpad6Letters, dialpadInclude.dialpad7Letters,
                    dialpadInclude.dialpad8Letters, dialpadInclude.dialpad9Letters,
                    onHoldCallerName, onHoldLabel, callMessageLabel, callRemindLabel,
                    callToggleMicrophoneLabel, callDialpadLabel, callToggleSpeakerLabel, callAddLabel,
                    callSwapLabel, callMergeLabel, callToggleLabel, callAddContactLabel,
                    dialpadClose, callEndLabel, callAcceptAndDecline
                ).forEach {
                    it.setTextColor(Color.WHITE)
                }

                arrayOf(
                    callToggleMicrophone, callToggleSpeaker, callDialpad, /*dialpadClose,*/ callSimImage, callDetails,
                    callToggleHold, callAddContact, callAdd, callSwap, callMerge, callInfo, addCallerNote, imageView,
                    dialpadInclude.dialpadAsterisk, dialpadInclude.dialpadHashtag
                ).forEach {
                    it.applyColorFilter(Color.WHITE)
                }

                callSimId.setTextColor(Color.WHITE.getContrastColor())
                // Transparent status bar and navigation bar
                setWindowTransparency(false) { statusBarSize, bottomNavigationBarSize, leftNavigationBarSize, rightNavigationBarSize ->
                    callHolder.setPadding(leftNavigationBarSize, statusBarSize, rightNavigationBarSize, bottomNavigationBarSize)
                }
            }
        } else {
            updateStatusbarColor(getProperBackgroundColor())
            updateNavigationBarColor(getProperBackgroundColor())

            val properTextColor = getProperTextColor()
            binding.apply {
                arrayOf(
                    callToggleMicrophone, callToggleSpeaker, callDialpad, /*dialpadClose,*/ callSimImage, callDetails,
                    callToggleHold, callAddContact, callAdd, callSwap, callMerge, callInfo, addCallerNote, imageView,
                    dialpadInclude.dialpadAsterisk, dialpadInclude.dialpadHashtag, callMessage, callRemind
                ).forEach {
                    it.applyColorFilter(properTextColor)
                }

                callSimId.setTextColor(properTextColor.getContrastColor())
                dialpadInput.disableKeyboard()

                dialpadWrapper.onGlobalLayout {
                    dialpadHeight = dialpadWrapper.height.toFloat()
                }
            }
        }
        updateCallContactInfo(CallManager.getPrimaryCall())

        binding.apply {
            arrayOf(
                callToggleMicrophone, callToggleSpeaker, callToggleHold, onHoldStatusHolder,
                callRemind, callMessage,
                callDialpadHolder, callAddContactHolder, callAddHolder, callSwapHolder, callMergeHolder,
                callAcceptAndDecline
            ).forEach {
                it.background.applyColorFilter(Color.GRAY)
                it.background.alpha = 60
            }
            arrayOf(
                dialpadInclude.dialpad0Holder, dialpadInclude.dialpad1Holder, dialpadInclude.dialpad2Holder, dialpadInclude.dialpad3Holder,
                dialpadInclude.dialpad4Holder, dialpadInclude.dialpad5Holder, dialpadInclude.dialpad6Holder, dialpadInclude.dialpad7Holder,
                dialpadInclude.dialpad8Holder, dialpadInclude.dialpad9Holder, dialpadInclude.dialpadAsteriskHolder, dialpadInclude.dialpadHashtagHolder
            ).forEach {
                it.foreground.applyColorFilter(Color.GRAY)
                it.foreground.alpha = 60
            }
        }

        val isSmallScreen =
            resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_SMALL
        if (config.callButtonStyle == IOS17 || isSmallScreen) {
            binding.callEndLabel.beVisible()
            binding.callAddContactHolder.beGone()

            val callAddHolderParams = binding.callAddHolder.layoutParams as ConstraintLayout.LayoutParams
            callAddHolderParams.topToTop = binding.callEnd.id
            callAddHolderParams.bottomToBottom = binding.callEnd.id
            callAddHolderParams.topMargin = 0
            binding.callAddHolder.requestLayout()
            val callSwapHolderParams = binding.callSwapHolder.layoutParams as ConstraintLayout.LayoutParams
            callSwapHolderParams.topToTop = binding.callEnd.id
            callSwapHolderParams.bottomToBottom = binding.callEnd.id
            callSwapHolderParams.topMargin = 0
            binding.callSwapHolder.requestLayout()

            val marginStartEnd = resources.getDimension(R.dimen.margin_button_horizontal).toInt()
            val callToggleHoldParams = binding.callToggleHold.layoutParams as ConstraintLayout.LayoutParams
            callToggleHoldParams.topToTop = binding.callEnd.id
            callToggleHoldParams.bottomToBottom = binding.callEnd.id
            callToggleHoldParams.leftToRight = binding.callEnd.id
            callToggleHoldParams.topMargin = 0
            callToggleHoldParams.marginStart = marginStartEnd
            binding.callToggleHold.requestLayout()
            val callMergeHolderParams = binding.callMergeHolder.layoutParams as ConstraintLayout.LayoutParams
            callMergeHolderParams.topToTop = binding.callEnd.id
            callMergeHolderParams.bottomToBottom = binding.callEnd.id
            callMergeHolderParams.leftToRight = binding.callEnd.id
            callMergeHolderParams.topMargin = 0
            callMergeHolderParams.marginStart = marginStartEnd
            binding.callMergeHolder.requestLayout()

            val marginBottom =
                if (isSmallScreen) resources.getDimension(R.dimen.call_button_row_margin_small).toInt()
                else resources.getDimension(R.dimen.call_button_row_margin).toInt()
            val callDialpadHolderParams = binding.callDialpadHolder.layoutParams as ConstraintLayout.LayoutParams
            callDialpadHolderParams.topToTop = -1
            callDialpadHolderParams.bottomToBottom = -1
            callDialpadHolderParams.bottomToTop = binding.callEnd.id
            callDialpadHolderParams.bottomMargin = marginBottom
            binding.callDialpadHolder.requestLayout()
        }
        if (isSmallScreen) {
            binding.apply {
                arrayOf(
                    callDeclineLabel, callAcceptLabel, callMessageLabel, callRemindLabel,
                    callToggleMicrophoneLabel, callDialpadLabel, callToggleSpeakerLabel, callAddLabel,
                    callSwapLabel, callMergeLabel, callToggleLabel, callAddContactLabel, callEndLabel
                ).forEach {
                    it.beGone()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initOutgoingCall(callNumber: Uri) {
        try {
            getHandleToUse(intent, callNumber.toString()) { handle ->
                if (handle != null) {
                    CallManager.getPrimaryCall()?.phoneAccountSelected(handle, false)
                }
            }
        } catch (e: Exception) {
            showErrorToast(e)
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        updateState()
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingSuperCall", "Wakelock")
    override fun onDestroy() {
        super.onDestroy()
        CallManager.removeListener(callCallback)
        disableProximitySensor()

        if (isOreoMr1Plus()) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        } else {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                // or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
        }

        if (screenOnWakeLock?.isHeld == true) {
            screenOnWakeLock!!.release()
        }
        if (config.flashForAlerts) MyCameraImpl.newInstance(this).stopSOS()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.dialpadWrapper.isVisible()) {
            hideDialpad()
            return
        } else if (config.backPressedEndCall) {
            endCall()
        } else {
            super.onBackPressed()
        }
    }

    private fun initButtons() = binding.apply {
        when (config.answerStyle) {
            ANSWER_SLIDER, ANSWER_SLIDER_OUTLINE -> {
                arrayOf(
                    callDecline, callDeclineLabel,
                    callAccept, callAcceptLabel,
                    callDraggableVertical, callUpArrow, callDownArrow
                ).forEach {
                    it.beGone()
                }
                handleSwipe()
            }
            ANSWER_SLIDER_VERTICAL -> {
                arrayOf(
                    callDecline, callDeclineLabel,
                    callAccept, callAcceptLabel,
                    callDraggable, callDraggableBackground,
                    callLeftArrow, callRightArrow
                ).forEach {
                    it.beGone()
                }
                handleSwipeVertical()
            }
            else -> {
                arrayOf(
                    callDraggable, callDraggableBackground, callDraggableVertical,
                    callLeftArrow, callRightArrow,
                    callUpArrow, callDownArrow
                ).forEach {
                    it.beGone()
                }

                callDecline.setOnClickListener {
                    endCall()
                }

                callAccept.setOnClickListener {
                    acceptCall()
                }
            }
        }

        callToggleMicrophone.setOnClickListener {
            toggleMicrophone()
            maybePerformDialpadHapticFeedback(it)
        }

        callToggleSpeaker.setOnClickListener {
            changeCallAudioRoute()
            maybePerformDialpadHapticFeedback(it)
        }

        callToggleSpeaker.setOnLongClickListener {
//            if (CallManager.getCallAudioRoute() == AudioRoute.BLUETOOTH) {
//                openBluetoothSettings()
            val supportAudioRoutes = CallManager.getSupportedAudioRoutes()
            if (supportAudioRoutes.size > 2) {
                val isSpeakerOn = !isSpeakerOn
                val newRoute = if (isSpeakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
                CallManager.setAudioRoute(newRoute)
            }
            else toast(callToggleSpeaker.contentDescription.toString())
            maybePerformDialpadHapticFeedback(it)
            true
        }

        callDialpadHolder.setOnClickListener {
            toggleDialpadVisibility()
            maybePerformDialpadHapticFeedback(it)
        }

        dialpadClose.setOnClickListener {
            hideDialpad()
            maybePerformDialpadHapticFeedback(it)
        }

        callAddHolder.setOnClickListener {
            Intent(applicationContext, DialpadActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(this)
            }
            maybePerformDialpadHapticFeedback(it)
        }

        callSwapHolder.setOnClickListener {
            CallManager.swap()
            maybePerformDialpadHapticFeedback(it)
        }

        callMergeHolder.setOnClickListener {
            CallManager.merge()
            maybePerformDialpadHapticFeedback(it)
        }

        callInfo.setOnClickListener {
            startActivity(Intent(this@CallActivity, ConferenceActivity::class.java))
            maybePerformDialpadHapticFeedback(it)
        }

        callToggleHold.setOnClickListener {
            toggleHold()
            maybePerformDialpadHapticFeedback(it)
        }

        callAddContactHolder.setOnClickListener {
            addContact()
            maybePerformDialpadHapticFeedback(it)
        }

        callEnd.setOnClickListener {
            endCall()
        }

        dialpadInclude.apply {
            dialpad0Holder.setOnClickListener { dialpadPressed('0') }
            dialpad1Holder.setOnClickListener { dialpadPressed('1') }
            dialpad2Holder.setOnClickListener { dialpadPressed('2') }
            dialpad3Holder.setOnClickListener { dialpadPressed('3') }
            dialpad4Holder.setOnClickListener { dialpadPressed('4') }
            dialpad5Holder.setOnClickListener { dialpadPressed('5') }
            dialpad6Holder.setOnClickListener { dialpadPressed('6') }
            dialpad7Holder.setOnClickListener { dialpadPressed('7') }
            dialpad8Holder.setOnClickListener { dialpadPressed('8') }
            dialpad9Holder.setOnClickListener { dialpadPressed('9') }

            dialpad0Holder.setOnLongClickListener { dialpadPressed('+'); true }
            dialpadAsteriskHolder.setOnClickListener { dialpadPressed('*') }
            dialpadHashtagHolder.setOnClickListener { dialpadPressed('#') }
        }

        arrayOf(
            callToggleMicrophone, callDialpadHolder, callToggleHold,
            callAddHolder, callSwapHolder, callMergeHolder, callInfo, addCallerNote, callAddContactHolder
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
    private fun handleSwipe() = binding.apply {
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
        var initialBackgroundWidth = 0

        val isRtl = isRTLLayout
        callAccept.onGlobalLayout {
            minDragX = if (isRtl) callDraggableBackground.left.toFloat() + resources.getDimension(R.dimen.three_dp)
                        else callDraggableBackground.left.toFloat() - callDraggable.width.toFloat()
            maxDragX = if (isRtl) callDraggableBackground.right.toFloat() - 60f
                        else callDraggableBackground.right.toFloat() - callDraggable.width.toFloat() - resources.getDimension(R.dimen.three_dp) - 20f
            initialDraggableX = if (isRtl) callDraggableBackground.right.toFloat() - callDraggable.width.toFloat() else callDraggableBackground.left.toFloat() + resources.getDimension(R.dimen.three_dp)
            initialLeftArrowX = callLeftArrow.x
            initialRightArrowX = callRightArrow.x
            initialLeftArrowScaleX = callLeftArrow.scaleX
            initialLeftArrowScaleY = callLeftArrow.scaleY
            initialRightArrowScaleX = callRightArrow.scaleX
            initialRightArrowScaleY = callRightArrow.scaleY
            leftArrowTranslation = if (isRtl) 50f else -50f //-callDraggableBackground.x
            rightArrowTranslation = if (isRtl) -50f else 50f //callDraggableBackground.x
            initialBackgroundWidth = callDraggableBackground.width

            callLeftArrow.applyColorFilter(getColor(R.color.red_call))
            callRightArrow.applyColorFilter(getColor(R.color.green_call))

            startArrowAnimation(callLeftArrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
            startArrowAnimation(callRightArrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
        }

        val configBackgroundCallScreen = config.backgroundCallScreen
        if (config.answerStyle == ANSWER_SLIDER_OUTLINE) {
            callDraggableBackground.background = AppCompatResources.getDrawable(this@CallActivity, R.drawable.call_draggable_background_stroke)
            val colorBg = if (configBackgroundCallScreen == THEME_BACKGROUND) getProperTextColor() else Color.WHITE
            callDraggableBackgroundIcon.background.mutate().setTint(colorBg)
        } else {
            callDraggableBackground.background.alpha = 51 // 20%
        }
        val colorBg = if (configBackgroundCallScreen == TRANSPARENT_BACKGROUND || configBackgroundCallScreen == BLUR_AVATAR || configBackgroundCallScreen == AVATAR || configBackgroundCallScreen == BLACK_BACKGROUND) Color.WHITE
            else getProperTextColor()
        callDraggableBackgroundIcon.drawable.mutate().setTint(getColor(R.color.green_call))
        callDraggableBackgroundIcon.background.mutate().setTint(colorBg)
        callDraggableBackground.background.mutate().setTint(colorBg)

        var lock = false
        callDraggable.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    //callDraggableBackground.animate().alpha(0f)
                    stopAnimation = true
                    callLeftArrow.animate().alpha(0f)
                    callRightArrow.animate().alpha(0f)
                    lock = false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
//                    callDraggable.animate().x(initialDraggableX).withEndAction {
//                        callDraggableBackground.animate().alpha(0.2f)
//                    }
                    callDraggable.x(initialDraggableX)
                    callDraggableBackgroundIcon.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, R.drawable.ic_phone_down_vector))
                    callDraggableBackgroundIcon.drawable.mutate().setTint(getColor(R.color.green_call))
                    callLeftArrow.animate().alpha(1f)
                    callRightArrow.animate().alpha(1f)
                    stopAnimation = false
                    startArrowAnimation(callLeftArrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
                    startArrowAnimation(callRightArrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)

                    callDraggableBackground.layoutParams.width = initialBackgroundWidth
                    callDraggableBackground.layoutParams = callDraggableBackground.layoutParams
                }

                MotionEvent.ACTION_MOVE -> {
                    callDraggable.x = min(maxDragX, max(minDragX, event.rawX - dragDownX))
                    callDraggableBackground.layoutParams.width = if (isRtl) (initialBackgroundWidth - (maxDragX + 60 - callDraggable.height.toFloat() - callDraggable.x)).toInt()
                        else (initialBackgroundWidth + (minDragX + resources.getDimension(R.dimen.three_dp) + callDraggable.width - callDraggable.x)).toInt()
                    callDraggableBackground.layoutParams = callDraggableBackground.layoutParams
                    //callerNameLabel.text = callDraggable.x.toString() + "   " + initialBackgroundWidth.toString() + "   " + (minDragX + callDraggable.width - callDraggable.x).toString()
                    when {
                        callDraggable.x >= maxDragX -> {
                            if (!lock) {
                                lock = true
                                if (isRtl) {
                                    endCall()
                                } else {
                                    acceptCall()
                                }
                            }
                        }

                        callDraggable.x <= minDragX + 50f -> {
                            if (!lock) {
                                lock = true
                                if (isRtl) {
                                    acceptCall()
                                } else {
                                    endCall()
                                }
                            }
                        }

                        callDraggable.x > initialDraggableX + 20f -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_down_red_vector
                            } else {
                                R.drawable.ic_phone_green_vector
                            }
                            callDraggableBackgroundIcon.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, drawableRes))
                        }

                        callDraggable.x < initialDraggableX - 20f -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_green_vector
                            } else {
                                R.drawable.ic_phone_down_red_vector
                            }
                            callDraggableBackgroundIcon.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, drawableRes))
                        }

                        callDraggable.x <= initialDraggableX + 20f || callDraggable.x >= initialDraggableX - 20f -> {
                            lock = false
                            callDraggableBackgroundIcon.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, R.drawable.ic_phone_down_vector))
                            callDraggableBackgroundIcon.drawable.mutate().setTint(getColor(R.color.green_call))
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

    @SuppressLint("ClickableViewAccessibility")
    private fun handleSwipeVertical() = binding.apply {
        var minDragY = 0f
        var maxDragY = 0f
        var initialDraggableY = 0f
        var initialDownArrowY = 0f
        var initialUpArrowY = 0f
        var initialDownArrowScaleX = 0f
        var initialDownArrowScaleY = 0f
        var initialUpArrowScaleX = 0f
        var initialUpArrowScaleY = 0f
        var downArrowTranslation = 0f
        var upArrowTranslation = 0f

        callDraggableVertical.onGlobalLayout {
            minDragY = callDraggableVertical.top.toFloat() - callDraggableVertical.height.toFloat()
            maxDragY = callDraggableVertical.bottom.toFloat()
            initialDraggableY = callDraggableVertical.top.toFloat()
            initialDownArrowY = callDownArrow.y
            initialUpArrowY = callUpArrow.y
            initialDownArrowScaleX = callDownArrow.scaleX
            initialDownArrowScaleY = callDownArrow.scaleY
            initialUpArrowScaleX = callUpArrow.scaleX
            initialUpArrowScaleY = callUpArrow.scaleY
            downArrowTranslation = 50f
            upArrowTranslation = -50f

            callDownArrow.applyColorFilter(getColor(R.color.red_call))
            callUpArrow.applyColorFilter(getColor(R.color.green_call))

            startArrowAnimationVertical(callDownArrow, initialDownArrowY, initialDownArrowScaleX, initialDownArrowScaleY, downArrowTranslation)
            startArrowAnimationVertical(callUpArrow, initialUpArrowY, initialUpArrowScaleX, initialUpArrowScaleY, upArrowTranslation)
        }

        val configBackgroundCallScreen = config.backgroundCallScreen
        val colorBg = if (configBackgroundCallScreen == TRANSPARENT_BACKGROUND || configBackgroundCallScreen == BLUR_AVATAR || configBackgroundCallScreen == AVATAR || configBackgroundCallScreen == BLACK_BACKGROUND) Color.WHITE
        else getProperTextColor()
        callDraggableVertical.drawable.mutate().setTint(getColor(R.color.green_call))
        callDraggableVertical.background.mutate().setTint(colorBg)
        //callDraggableVertical.background.alpha = 51 // 20%

        var lock = false
        callDraggableVertical.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.y
                    //callDraggableBackground.animate().alpha(0f)
                    stopAnimation = true
                    callDownArrow.animate().alpha(0f)
                    callUpArrow.animate().alpha(0f)
                    lock = false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
                    callDraggableVertical.animate().y(initialDraggableY)
                    callDraggableVertical.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, R.drawable.ic_phone_down_vector))
                    callDraggableVertical.drawable.mutate().setTint(getColor(R.color.green_call))
                    callDownArrow.animate().alpha(1f)
                    callUpArrow.animate().alpha(1f)
                    stopAnimation = false
                    startArrowAnimationVertical(callDownArrow, initialDownArrowY, initialDownArrowScaleX, initialDownArrowScaleY, downArrowTranslation)
                    startArrowAnimationVertical(callUpArrow, initialUpArrowY, initialUpArrowScaleX, initialUpArrowScaleY, upArrowTranslation)
                }

                MotionEvent.ACTION_MOVE -> {
                    callDraggableVertical.y = min(maxDragY, max(minDragY, event.rawY - dragDownX - statusBarHeight))
                    //callerNameLabel.text = callDraggableVertical.y.toString() + "   " + statusBarHeight.toString() + "   " + callDraggableVertical.top.toFloat().toString()
                    when {
                        callDraggableVertical.y >= maxDragY -> {
                            if (!lock) {
                                lock = true
                                endCall()
                            }
                        }
                        callDraggableVertical.y <= minDragY -> {
                            if (!lock) {
                                lock = true
                                acceptCall()
                            }
                        }
                        callDraggableVertical.y > initialDraggableY + 20f -> {
                            lock = false
                            callDraggableVertical.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, R.drawable.ic_phone_down_red_vector))
                        }
                        callDraggableVertical.y < initialDraggableY - 20f -> {
                            lock = false
                            callDraggableVertical.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, R.drawable.ic_phone_green_vector))
                        }
                        callDraggableVertical.y <= initialDraggableY + 20f || callDraggableVertical.y >= initialDraggableY - 20f -> {
                            lock = false
                            callDraggableVertical.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, R.drawable.ic_phone_down_vector))
                            callDraggableVertical.drawable.mutate().setTint(getColor(R.color.green_call))
                        }
                    }
                }
            }
            true
        }
    }

    private fun startArrowAnimationVertical(arrow: ImageView, initialY: Float, initialScaleX: Float, initialScaleY: Float, translation: Float) {
        arrow.apply {
            alpha = 1f
            y = initialY
            scaleX = initialScaleX
            scaleY = initialScaleY
            animate()
                .alpha(0f)
                .translationY(translation)
                .scaleXBy(-0.5f)
                .scaleYBy(-0.5f)
                .setDuration(1000)
                .withEndAction {
                    if (!stopAnimation) {
                        startArrowAnimationVertical(this, initialY, initialScaleX, initialScaleY, translation)
                    }
                }
        }
    }

    private fun dialpadPressed(char: Char) {
        CallManager.keypad(char)
        binding.dialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(binding.dialpadInput)
    }

//    private fun openBluetoothSettings() {
//        try {
//            val storageSettingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
//            startActivity(storageSettingsIntent)
//        } catch (e: Exception) {
//            showErrorToast(e)
//        }
//    }

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

    private fun updateCallAudioState(route: AudioRoute?, changeProximitySensor: Boolean = true) {
        if (route != null) {
            //If enabled, one of the users has his microphone turned off at the start of a call
            //isMicrophoneOff = audioManager.isMicrophoneMute
            updateMicrophoneButton()

            isSpeakerOn = route == AudioRoute.SPEAKER
            val supportedAudioRoutes = CallManager.getSupportedAudioRoutes()
            binding.callToggleSpeaker.apply {
                val bluetoothConnected = supportedAudioRoutes.contains(AudioRoute.BLUETOOTH)
                contentDescription = if (bluetoothConnected) {
                    getString(R.string.choose_audio_route)
                } else {
                    getString(if (isSpeakerOn) R.string.turn_speaker_off else R.string.turn_speaker_on)
                }
                // show speaker icon when a headset is connected, a headset icon maybe confusing to some
                if (/*route == AudioRoute.WIRED_HEADSET || */route == AudioRoute.EARPIECE) {
                    setImageResource(R.drawable.ic_volume_down_vector)
                } else {
                    setImageResource(route.iconRes)
                }
            }
            val supportAudioRoutes = CallManager.getSupportedAudioRoutes()
            binding.callToggleSpeakerLabel.text = if (supportAudioRoutes.size == 2) getString(R.string.audio_route_speaker) else  getString(route.stringRes)
            toggleButtonColor(binding.callToggleSpeaker, enabled = route != AudioRoute.EARPIECE && route != AudioRoute.WIRED_HEADSET)
            createOrUpdateAudioRouteChooser(supportedAudioRoutes, create = false)

            if (changeProximitySensor) { // No need to turn on the sensor when a call has not yet been answered
                if (isSpeakerOn) {
                    disableProximitySensor()
                } else {
                    enableProximitySensor()
                }
            }
        }
    }

    private fun toggleMicrophone() {
        isMicrophoneOff = !isMicrophoneOff

        audioManager.isMicrophoneMute = isMicrophoneOff
        CallManager.inCallService?.setMuted(isMicrophoneOff)
        updateMicrophoneButton()
    }

    private fun updateMicrophoneButton() {
        binding.apply {
            val drawable = if (!isMicrophoneOff) R.drawable.ic_microphone_vector else R.drawable.ic_microphone_off_vector
            callToggleMicrophone.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, drawable))

            val configBackgroundCallScreen = config.backgroundCallScreen
            if (configBackgroundCallScreen == TRANSPARENT_BACKGROUND || configBackgroundCallScreen == BLUR_AVATAR || configBackgroundCallScreen == AVATAR) {
                val color = if (isMicrophoneOff) Color.WHITE else Color.GRAY
                callToggleMicrophone.background.applyColorFilter(color)
                val colorIcon = if (isMicrophoneOff) Color.BLACK else Color.WHITE
                callToggleMicrophone.applyColorFilter(colorIcon)
            }
            callToggleMicrophone.background.alpha = if (isMicrophoneOff) 255 else 60
            callToggleMicrophone.contentDescription = getString(if (isMicrophoneOff) R.string.turn_microphone_on else R.string.turn_microphone_off)
            //callToggleMicrophoneLabel.text = getString(if (isMicrophoneOff) R.string.turn_microphone_on else R.string.turn_microphone_off)
        }
    }

    private fun toggleDialpadVisibility() {
        if (binding.dialpadWrapper.isVisible()) hideDialpad() else showDialpad()
    }

    private fun showDialpad() {
        binding.apply {
            dialpadWrapper.beVisible()
            dialpadClose.beVisible()
            arrayOf(
                callerAvatar, callerNameLabel, callerDescription, callerNumber, callerNotes, callStatusLabel,
                callSimImage, callSimId, callToggleMicrophone, callDialpadHolder,
                callToggleSpeaker, callAddContactHolder, callInfo, addCallerNote
            ).forEach {
                it.beGone()
            }
            controlsSingleCall.beGone()
            controlsTwoCalls.beGone()

            RxAnimation.together(
                dialpadWrapper.scale(1f),
                dialpadWrapper.fadeIn(),
                dialpadClose.fadeIn()
            ).doAfterTerminate {
            }.subscribe()
        }
    }

    @SuppressLint("MissingPermission")
    private fun hideDialpad() {
        binding.apply {
            RxAnimation.together(
                dialpadWrapper.scale(0.7f),
                dialpadWrapper.fadeOut(),
                dialpadClose.fadeOut()
            ).doAfterTerminate {
                dialpadWrapper.beGone()
                dialpadClose.beGone()
                arrayOf(
                    callerAvatar, callerNameLabel, callerNumber, callStatusLabel,
                    callToggleMicrophone, callDialpadHolder,
                    callToggleSpeaker
                ).forEach {
                    it.beVisible()
                }
                val isSmallScreen =
                    resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_SMALL
                callAddContactHolder.beVisibleIf(config.callButtonStyle == IOS16 && !isSmallScreen)
                callerDescription.beVisibleIf(callerDescription.text.isNotEmpty())
                callerNotes.beVisibleIf(callerNotes.text.isNotEmpty())
                val accounts = telecomManager.callCapablePhoneAccounts
                callSimImage.beVisibleIf(accounts.size > 1)
                callSimId.beVisibleIf(accounts.size > 1)
                updateState()
            }.subscribe()
        }
    }

    private fun toggleHold() {
        binding.apply {
            val isOnHold = CallManager.toggleHold()
            val drawable = if (isOnHold) R.drawable.ic_pause_crossed_vector else R.drawable.ic_pause_vector
            callToggleHold.setImageDrawable(AppCompatResources.getDrawable(this@CallActivity, drawable))
            val description = getString(if (isOnHold) R.string.resume_call else R.string.hold_call)
            callToggleLabel.text = description
            callToggleHold.contentDescription = description
            holdStatusLabel.beInvisibleIf(!isOnHold)
            RxAnimation.from(holdStatusLabel)
                .shake()
                .subscribe()

            val configBackgroundCallScreen = config.backgroundCallScreen
            if (configBackgroundCallScreen == TRANSPARENT_BACKGROUND || configBackgroundCallScreen == BLUR_AVATAR || configBackgroundCallScreen == AVATAR) {
                val color = if (isOnHold) Color.WHITE else Color.GRAY
                callToggleHold.background.applyColorFilter(color)
                val colorIcon = if (isOnHold) Color.BLACK else Color.WHITE
                callToggleHold.applyColorFilter(colorIcon)
            }
            callToggleHold.background.alpha = if (isOnHold) 255 else 60
        }
    }

    private fun addContact() {
        val number = callContact?.number?.ifEmpty { "" } ?: ""
        val formatNumber = if (config.formatPhoneNumbers) number.formatPhoneNumber() else number
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, formatNumber)
            launchActivityIntent(this)
        }
    }

    @Suppress("DEPRECATION")
    private fun updateOtherPersonsInfo(avatarUri: String, isConference: Boolean) {
        if (callContact == null) {
            return
        }

        binding.apply {
            val (name, _, number, numberLabel, description, isABusinessCall, isVoiceMail) = callContact!!
            callerNameLabel.text =
                formatterUnicodeWrap(name.ifEmpty { getString(R.string.unknown_caller) })
            if (number.isNotEmpty() && number != name) {
                val numberText = formatterUnicodeWrap(number)
                if (numberLabel.isNotEmpty()) {
                    val numberLabelText = formatterUnicodeWrap(numberLabel)
                    callerNumber.text = numberLabelText
                    callerNumber.setOnClickListener {
                        if (callerNumber.text == numberLabelText) callerNumber.text = numberText
                        else callerNumber.text = numberLabelText
                        maybePerformDialpadHapticFeedback(it)
                    }
                } else {
                    callerNumber.text = numberText
                }

                if (description.isNotEmpty() && description != name) {
                    callerDescription.text = formatterUnicodeWrap(description)
                    callerDescription.beVisible()
                } else callerDescription.beGone()
            } else {
                callerDescription.beGone()
                val country = if (number.startsWith("+")) getCountryByNumber(number) else ""
                if (country != "") {
                    callerNumber.text = formatterUnicodeWrap(country)//country
                } else callerNumber.beGone()
            }

            callerAvatar.apply {
                if (number == name || isABusinessCall || isVoiceMail || isDestroyed || isFinishing) {
                    val drawable =
                        if (isABusinessCall) AppCompatResources.getDrawable(this@CallActivity, R.drawable.placeholder_company)
                        else if (isVoiceMail) AppCompatResources.getDrawable(this@CallActivity, R.drawable.placeholder_voicemail)
                        else AppCompatResources.getDrawable(this@CallActivity, R.drawable.placeholder_contact)
                    if (baseConfig.useColoredContacts) {
                        val letterBackgroundColors = getLetterBackgroundColors()
                        val color = letterBackgroundColors[abs(name.hashCode()) % letterBackgroundColors.size].toInt()
                        (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                    }
                    setImageDrawable(drawable)
                } else {
                    if (!isFinishing && !isDestroyed) {
                        val placeholder = if (isConference) {
                            SimpleContactsHelper(this@CallActivity).getColoredGroupIcon(name)
                        } else null
                        SimpleContactsHelper(this@CallActivity.applicationContext).loadContactImage(
                            avatarUri,
                            this,
                            name,
                            placeholder
                        )
                    }
                }
            }

            callMessage.apply {
                setOnClickListener {
                    val wrapper: Context = ContextThemeWrapper(this@CallActivity, getPopupMenuTheme())
                    val popupMenu = PopupMenu(wrapper, callMessage, Gravity.END)
                    val quickAnswers = config.quickAnswers
                    popupMenu.menu.add(1, 1, 1, R.string.other).setIcon(R.drawable.ic_transparent)
                    if (quickAnswers.size == 3) {
                        popupMenu.menu.add(1, 2, 2, quickAnswers[0]).setIcon(R.drawable.ic_clock_vector)
                        popupMenu.menu.add(1, 3, 3, quickAnswers[1]).setIcon(R.drawable.ic_run)
                        popupMenu.menu.add(1, 4, 4, quickAnswers[2]).setIcon(R.drawable.ic_microphone_off_vector)
                    }
                    popupMenu.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> {
                                sendSMS(callContact!!.number)
                                endCall()
                            }

                            else -> {
                                endCall(rejectWithMessage = true, textMessage = item.title.toString())
                            }
                        }
                        true
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        popupMenu.setForceShowIcon(true)
                    }
                    popupMenu.show()
                    // icon coloring
                    popupMenu.menu.apply {
                        for (index in 0 until this.size()) {
                            val item = this.getItem(index)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                item.icon!!.colorFilter = BlendModeColorFilter(
                                    getProperTextColor(), BlendMode.SRC_IN
                                )
                            } else {
                                item.icon!!.setColorFilter(getProperTextColor(), PorterDuff.Mode.SRC_IN)
                            }
                        }
                    }

                    //sendSMS(callContact!!.number, "textMessage")
                }
                setOnLongClickListener { toast(R.string.send_sms); true; }
            }

            callRemind.apply {
                setOnClickListener {
                    this@CallActivity.handleNotificationPermission { permission ->
                        if (permission) {
                            val wrapper: Context = ContextThemeWrapper(this@CallActivity, getPopupMenuTheme())
                            val popupMenu = PopupMenu(wrapper, callRemind, Gravity.START)
                            popupMenu.menu.add(1, 1, 1, String.format(resources.getQuantityString(R.plurals.minutes, 10, 10)))
                            popupMenu.menu.add(1, 2, 2, String.format(resources.getQuantityString(R.plurals.minutes, 30, 30)))
                            popupMenu.menu.add(1, 3, 3, String.format(resources.getQuantityString(R.plurals.minutes, 60, 60)))
                            popupMenu.setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    1 -> {
                                        startTimer(600)
                                        endCall()
                                    }

                                    2 -> {
                                        startTimer(1800)
                                        endCall()
                                    }

                                    else -> {
                                        startTimer(3600)
                                        endCall()
                                    }
                                }
                                true
                            }
                            popupMenu.show()
                        } else {
                            toast(R.string.allow_notifications_reminders)
                        }
                    }
                }
                setOnLongClickListener { toast(R.string.remind_me); true; }
            }

            val callNote = callerNotesHelper.getCallerNotes(number)
            callerNotes.apply {
                beVisibleIf(callNote != null && !isConference)
                if (callNote != null) {
                    text = callNote.note
                }
                setOnClickListener {
                    changeNoteDialog(number)
                }
            }

            addCallerNote.apply {
                setOnClickListener {
                    changeNoteDialog(number)
                }
            }
        }
    }

    private fun changeNoteDialog(number: String) {
        val callerNote = callerNotesHelper.getCallerNotes(number)
        ChangeTextDialog(
            activity = this@CallActivity,
            title = number.normalizeString(),
            currentText = callerNote?.note,
            maxLength = CALLER_NOTES_MAX_LENGTH,
            showNeutralButton = true,
            neutralTextRes = R.string.delete
        ) {
            if (it != "") {
                callerNotesHelper.addCallerNotes(number, it, callerNote) {
                    binding.callerNotes.text = it
                    binding.callerNotes.beVisible()
                }
            } else {
                callerNotesHelper.deleteCallerNotes(callerNote) {
                    binding.callerNotes.text = it
                    binding.callerNotes.beGone()
                }
            }
        }
    }

    private fun startTimer(duration: Int) {
        timerHelper.getTimers { timers ->
            val runningTimers = timers.filter { it.state is TimerState.Running && it.id == 1 }
            runningTimers.forEach { timer ->
                EventBus.getDefault().post(TimerEvent.Delete(timer.id!!))
            }
            val newTimer = createNewTimer()
            newTimer.id = 1
            newTimer.title = callContact!!.name
            newTimer.label = callContact!!.number
            newTimer.seconds = duration
            newTimer.vibrate = true
            timerHelper.insertOrUpdateTimer(newTimer)
            EventBus.getDefault().post(TimerEvent.Start(1, duration.secondsToMillis))
        }
    }

    private val Int.secondsToMillis get() = TimeUnit.SECONDS.toMillis(this.toLong())

    private fun sendSMS(number: String, text: String = " ") {
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.fromParts("smsto", number, null)
            putExtra("sms_body", text)
            launchActivityIntent(this)
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
                        binding.apply {
                            val simId = "${index + 1}"
                            callSimId.text = simId
                            callSimId.beVisible()
                            callSimImage.beVisible()
                        }

                        val acceptDrawableId = when (index) {
                            0 -> R.drawable.ic_phone_one_vector
                            1 -> R.drawable.ic_phone_two_vector
                            else -> R.drawable.ic_phone_vector
                        }
                        val acceptDrawable = AppCompatResources.getDrawable(this@CallActivity, acceptDrawableId)

                        val rippleBg = AppCompatResources.getDrawable(this, R.drawable.ic_call_accept) as RippleDrawable
                        val layerDrawable = rippleBg.findDrawableByLayerId(R.id.accept_call_background_holder) as LayerDrawable
                        layerDrawable.setDrawableByLayerId(R.id.accept_call_icon, acceptDrawable)
                        binding.callAccept.setImageDrawable(rippleBg)
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

        binding.apply {
            if (statusTextId != 0) {
                callStatusLabel.text = getString(statusTextId)
            }

            callInfo.beVisibleIf(!isCallEnded && call.hasCapability(Call.Details.CAPABILITY_MANAGE_CONFERENCE))
            addCallerNote.beVisibleIf(!callInfo.isVisible)
            if (dialpadWrapper.isGone()) {
                setActionButtonEnabled(callSwapHolder, enabled = !isCallEnded && state == Call.STATE_ACTIVE)
                setActionButtonEnabled(callMergeHolder, enabled = !isCallEnded && state == Call.STATE_ACTIVE)
            }
        }
    }

    private fun updateState() {
        val phoneState = CallManager.getPhoneState()
        var changeProximitySensor = true
        if (phoneState is SingleCall) {
            updateCallState(phoneState.call)
            updateCallOnHoldState(null)
            val state = phoneState.call.getStateCompat()
            val isSingleCallActionsEnabled = !isCallEnded && (state == Call.STATE_ACTIVE || state == Call.STATE_DISCONNECTED
                || state == Call.STATE_DISCONNECTING || state == Call.STATE_HOLDING)
            if (binding.dialpadWrapper.isGone()) {
                setActionImageViewEnabled(binding.callToggleHold, isSingleCallActionsEnabled)
                setActionButtonEnabled(binding.callAddHolder, isSingleCallActionsEnabled)
            }
            if (state == Call.REJECT_REASON_UNWANTED) changeProximitySensor = false
        } else if (phoneState is TwoCalls) {
            updateCallState(phoneState.active)
            updateCallOnHoldState(phoneState.onHold, phoneState.active)
        }

        runOnUiThread {
            updateCallAudioState(CallManager.getCallAudioRoute(), changeProximitySensor)
            updateMicrophoneButton()
        }
    }

    private fun updateCallOnHoldState(call: Call?, callActive: Call? = null) {
        val hasCallOnHold = call != null
        if (hasCallOnHold) {
            getCallContact(applicationContext, call) { contact ->
                runOnUiThread {
                    binding.onHoldCallerName.text = getContactNameOrNumber(contact)
                }
            }

            // A second call has been received but not yet accepted
            if (call!!.getStateCompat() == Call.REJECT_REASON_UNWANTED) {
                binding.apply {
                    ongoingCallHolder.beGone()
                    incomingCallHolder.beVisible()
                    callStatusLabel.text = getString(R.string.is_calling)

                    arrayOf(
                        callDraggable, callDraggableBackground, callDraggableVertical,
                        callLeftArrow, callRightArrow,
                        callUpArrow, callDownArrow
                    ).forEach {
                        it.beGone()
                    }


                    callDecline.beVisible()
                    callDecline.setOnClickListener {
                        endCall()
                    }

                    callAccept.beVisible()
                    callAccept.setOnClickListener {
                        acceptCall()
                    }

                    callAcceptAndDecline.apply {
                        beVisible()
                        setText(R.string.answer_end_other_call)
                        setOnClickListener {
                            acceptCall()
                            callActive?.disconnect()
                        }
                    }
                }
            }
        } else {
            if (config.callBlockButton) binding.callAcceptAndDecline.apply {
                beVisible()
                setText(R.string.block_number)
                setOnClickListener {
                    if (callContact != null) {
                        val number = callContact!!.number
                        val baseString = R.string.block_confirmation
                        val question = String.format(resources.getString(baseString), number)

                        ConfirmationAdvancedDialog(this@CallActivity, question, cancelOnTouchOutside = false) {
                            if (it) {
                                blockNumbers(number.normalizePhoneNumber())
                            }
                        }
                    }
                }
            }
        }

        binding.apply {
            onHoldStatusHolder.beVisibleIf(hasCallOnHold)
            controlsSingleCall.beVisibleIf(!hasCallOnHold && dialpadWrapper.isGone())
            controlsTwoCalls.beVisibleIf(hasCallOnHold && dialpadWrapper.isGone())
        }
    }

    private fun blockNumbers(number: String) {
        config.tabsChanged = true
        if (addBlockedNumber(number)) endCall()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateCallContactInfo(call: Call?) {
        binding.callDetails.beVisibleIf(call.isHD())
        getCallContact(applicationContext, call) { contact ->
            if (call != CallManager.getPrimaryCall()) {
                return@getCallContact
            }
            callContact = contact

            val configBackgroundCallScreen = config.backgroundCallScreen
            val isConference = call.isConference()

            var drawable: Drawable? = null
            if (configBackgroundCallScreen == BLUR_AVATAR || configBackgroundCallScreen == AVATAR) {
                val avatar = if (!isConference) callContactAvatarHelper.getCallContactAvatar(contact.photoUri, false) else null
                if (avatar != null) {
                    val bg = when (configBackgroundCallScreen) {
                        BLUR_AVATAR -> BlurFactory.fileToBlurBitmap(avatar, this, 0.6f, 5f)
                        AVATAR -> avatar
                        else -> null
                    }
                    val windowHeight = binding.callHolder.height //window.decorView.height
                    val windowWidth = binding.callHolder.width //window.decorView.width
                    if (bg != null && windowWidth != 0) {
                        val aspectRatio = windowHeight / windowWidth
                        val aspectRatioNotZero = if (aspectRatio == 0) 1 else aspectRatio
                        drawable = BitmapDrawable(resources, bg.cropCenter(bg.width/aspectRatioNotZero, bg.height))
                    }
                } else {
//                    val bg = BlurFactory.fileToBlurBitmap(resources.getDrawable(R.drawable.button_gray_bg, theme), this, 0.6f, 25f)
//                    drawable = BitmapDrawable(resources, bg)
                    binding.callHolder.setBackgroundColor(resources.getColor(R.color.default_call_background))
                }
            }

            runOnUiThread {
                if (drawable != null) {
                    binding.callHolder.background = drawable
                    binding.callHolder.background.alpha = 60
                    if (isQPlus()) {
                        binding.callHolder.background.colorFilter = BlendModeColorFilter(Color.DKGRAY, BlendMode.SOFT_LIGHT)
                    } else {
                        binding.callHolder.background.setColorFilter(Color.DKGRAY, PorterDuff.Mode.DARKEN)
                    }
                }

                val avatarRound = if (!isConference) contact.photoUri else ""
                updateOtherPersonsInfo(avatarRound, isConference)
                checkCalledSIMCard()
            }
        }
    }

    private fun acceptCall() {
        CallManager.accept()
    }

    private fun initOutgoingCallUI() {
        enableProximitySensor()
        binding.incomingCallHolder.beGone()
        binding.ongoingCallHolder.beVisible()
        binding.callEnd.beVisible()
    }

    private fun callRinging() {
        binding.incomingCallHolder.beVisible()
    }

    private fun callStarted() {
        enableProximitySensor()
        binding.incomingCallHolder.beGone()
        binding.ongoingCallHolder.beVisible()
        binding.callEnd.beVisible()
        callDurationHandler.removeCallbacks(updateCallDurationTask)
        callDurationHandler.post(updateCallDurationTask)
        maybePerformCallHapticFeedback(binding.callerNameLabel)
        if (config.flashForAlerts) MyCameraImpl.newInstance(this).toggleSOS()
    }

    private fun showPhoneAccountPicker() {
        if (callContact != null && !needSelectSIM) {
            getHandleToUse(intent, callContact!!.number) { handle ->
                CallManager.getPrimaryCall()?.phoneAccountSelected(handle, false)
            }
        }
    }

    private fun endCall(rejectWithMessage: Boolean = false, textMessage: String? = null) {
        CallManager.reject(rejectWithMessage, textMessage)
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
        runOnUiThread {
            val phoneState = CallManager.getPhoneState()
            if (callDuration > 0) {
                disableAllActionButtons()
                @SuppressLint("SetTextI18n")
                val label = "${callDuration.getFormattedDuration()} (${getString(R.string.call_ended)})"
                binding.callStatusLabel.text = label
                finishAndRemoveTask()
                if (phoneState is TwoCalls) startActivity(Intent(this, CallActivity::class.java))
            } else {
                disableAllActionButtons()
                binding.callStatusLabel.text = getString(R.string.call_ended)
                if (phoneState is TwoCalls) {
                    finishAndRemoveTask()
                    startActivity(Intent(this, CallActivity::class.java))
                } else finish()
            }
        }
        maybePerformCallHapticFeedback(binding.callerNameLabel)
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
            val call = CallManager.getPrimaryCall()
            callDuration = call.getCallDuration()
            if (!isCallEnded && call.getStateCompat() != Call.REJECT_REASON_UNWANTED) {
                binding.callStatusLabel.text = callDuration.getFormattedDuration()
                callDurationHandler.postDelayed(this, 1000)
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private fun addLockScreenFlags() {
        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
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
        } catch (_: Exception) {
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

    private fun disableAllActionButtons() {
        (binding.ongoingCallHolder.children + binding.callEnd)
            .filter { it is ImageView && it.isVisible() }
            .forEach { view ->
                setActionButtonEnabled(button = view as ImageView, enabled = false)
            }
        (binding.ongoingCallHolder.children)
            .filter { it is LinearLayout && it.isVisible() }
            .forEach { view ->
                setActionButtonEnabled(button = view as LinearLayout, enabled = false)
            }
    }

    private fun setActionButtonEnabled(button: LinearLayout, enabled: Boolean) {
        button.apply {
            isEnabled = enabled
            alpha = if (enabled) 1.0f else LOWER_ALPHA
        }
    }

    private fun setActionButtonEnabled(button: ImageView, enabled: Boolean) {
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

    private fun toggleButtonColor(view: ImageView, enabled: Boolean) {
        val configBackgroundCallScreen = config.backgroundCallScreen
        if (configBackgroundCallScreen == TRANSPARENT_BACKGROUND || configBackgroundCallScreen == BLUR_AVATAR || configBackgroundCallScreen == AVATAR) {
            val color = if (enabled) Color.WHITE else Color.GRAY
            view.background.applyColorFilter(color)
            val colorIcon = if (enabled) Color.BLACK else Color.WHITE
            view.applyColorFilter(colorIcon)
        }
        view.background.alpha = if (enabled) 255 else 60
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(PERMISSION_READ_STORAGE)) {
                config.backgroundCallScreen = BLUR_AVATAR
                toast(R.string.no_storage_permissions)
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (config.callVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun maybePerformCallHapticFeedback(view: View?) {
        if (config.callStartEndVibration) {
            view?.performHapticFeedback()
        }
    }
}
