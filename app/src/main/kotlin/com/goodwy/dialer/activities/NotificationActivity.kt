package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.goodwy.commons.extensions.launchActivityIntent
import com.goodwy.commons.extensions.telecomManager
import com.goodwy.commons.helpers.CURRENT_PHONE_NUMBER
import com.goodwy.commons.helpers.IS_RIGHT_APP
import com.goodwy.dialer.BuildConfig
import com.goodwy.dialer.helpers.MISSED_CALL_BACK
import com.goodwy.dialer.helpers.MISSED_CALL_MESSAGE
import com.goodwy.dialer.helpers.MISSED_CALL_NOTIFICATION_ID

//Empty activation to remove missed call notifications when you press to call or send a message
//https://stackoverflow.com/questions/18261969/clicking-android-notification-actions-does-not-close-notification-drawer?noredirect=1&lq=1
class NotificationActivity : SimpleActivity() {
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phoneNumber = intent.extras?.getString(CURRENT_PHONE_NUMBER) ?: return
        val notificationId = intent.extras?.getInt(MISSED_CALL_NOTIFICATION_ID, -1) ?: return

        if (notificationId != -1) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
        telecomManager.cancelMissedCallsNotification()

        when (intent.action) {
            MISSED_CALL_BACK -> phoneNumber.let {
                Intent(Intent.ACTION_CALL).apply {
                    data = Uri.fromParts("tel", it, null)
                    putExtra(IS_RIGHT_APP, BuildConfig.RIGHT_APP_KEY)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    launchActivityIntent(this)
                }
            }

            MISSED_CALL_MESSAGE -> phoneNumber.let {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.fromParts("smsto", it, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    launchActivityIntent(this)
                }
            }
        }

        finish()
    }
}
