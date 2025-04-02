package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.IS_RIGHT_APP
import com.goodwy.commons.helpers.REQUEST_CODE_SET_DEFAULT_DIALER
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.R
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.extensions.getHandleToUse
import com.goodwy.dialer.helpers.SHOW_RECENT_CALLS_ON_DIALPAD
import androidx.core.net.toUri

class DialerActivity : SimpleActivity() {
    private var callNumber: Uri? = null

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        if (intent.action == Intent.ACTION_CALL && intent.data != null) {
            callNumber = intent.data

            // make sure Simple Dialer is the default Phone app before initiating an outgoing call
            if (!isDefaultDialer()) {
                launchSetDefaultDialerIntent()
            } else {
                val key = intent.getStringExtra(IS_RIGHT_APP) ?: ""
                if (config.blockCallFromAnotherApp && key != BuildConfig.RIGHT_APP_KEY) {
                    val number = Uri.decode(intent.dataString).substringAfter("tel:")
                    Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.fromParts("tel", number, null)
                        putExtra(SHOW_RECENT_CALLS_ON_DIALPAD, true)
                        launchActivityIntent(this)
                    }
                } else initOutgoingCall()
            }
        } else {
            toast(R.string.unknown_error_occurred)
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initOutgoingCall() {
        try {
            if (isNumberBlocked(callNumber.toString().replace("tel:", ""), getBlockedNumbers())) {
                toast(R.string.calling_blocked_number)
                finish()
                return
            }

            getHandleToUse(intent, callNumber.toString()) { handle ->
                if (handle != null) {
                    Bundle().apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, false)
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                        telecomManager.placeCall(callNumber, this)
                    }
                }
                finish()
            }
        } catch (e: Exception) {
            showErrorToast(e)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            if (!isDefaultDialer()) {
                try {
                    hideKeyboard()
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:$packageName".toUri()
                        startActivity(this)
                    }
                    toast(R.string.default_phone_app_prompt, Toast.LENGTH_LONG)
                } catch (ignored: Exception) {
                }
                finish()
            } else {
                initOutgoingCall()
            }
        }
    }
}
