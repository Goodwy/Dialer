package com.goodwy.dialer.activities

import android.annotation.SuppressLint
import android.content.Intent
import com.goodwy.commons.activities.BaseSplashActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
