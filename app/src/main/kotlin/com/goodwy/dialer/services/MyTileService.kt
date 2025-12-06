package com.goodwy.dialer.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.goodwy.commons.activities.ManageBlockedNumbersActivity
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.helpers.isQPlus
import com.goodwy.commons.helpers.isUpsideDownCakePlus
import com.goodwy.dialer.R

class MyTileService : TileService() {

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        super.onClick()
//        if (isLocked) {
//            unlockAndRun { this.toggle() }
//        } else {
//            toggle()
//        }
        toggle()
    }

    override fun onTileRemoved() {
    }

    override fun onTileAdded() {
        updateTile()
    }

    private fun toggle() {
        if (isQPlus() && !baseConfig.blockingEnabled) {
            if (isNotDefaultCallerIdApp()) {
                setDefaultCallerIdApp()
            } else {
                baseConfig.blockingEnabled = !baseConfig.blockingEnabled
                updateTile()
            }
        } else {
            baseConfig.blockingEnabled = !baseConfig.blockingEnabled
            updateTile()
        }
    }

    private fun updateTile() {
        qsTile?.state = if (baseConfig.blockingEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (isQPlus()) qsTile?.subtitle =
            if (baseConfig.blockingEnabled) getString(R.string.on) else getString(R.string.off)
        qsTile?.icon = Icon.createWithResource(
            applicationContext,
            if (baseConfig.blockingEnabled) R.drawable.ic_call_missed_vector else R.drawable.ic_call_received_vector
        )
        qsTile?.updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun isNotDefaultCallerIdApp(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java)
        return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun setDefaultCallerIdApp() {
        try {
            val intent = Intent(this, ManageBlockedNumbersActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.action = ManageBlockedNumbersActivity.SET_DEFAULT_CALLER_ID
            startActivityAndCollapse(intent)
        } catch (_: Exception) {
            if (isUpsideDownCakePlus()) {
                try {
                    val intent = Intent(this, ManageBlockedNumbersActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    intent.action = ManageBlockedNumbersActivity.SET_DEFAULT_CALLER_ID

                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    startActivityAndCollapse(pendingIntent)
                } catch (_: Exception) {
                    setDefaultCallerIdAppUnlockAndRun()
                }
            } else {
                setDefaultCallerIdAppUnlockAndRun()
            }
        }
    }

    private fun setDefaultCallerIdAppUnlockAndRun() {
        unlockAndRun {
            val intent = Intent(this, ManageBlockedNumbersActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.action = ManageBlockedNumbersActivity.SET_DEFAULT_CALLER_ID
            startActivity(intent)
        }
    }
}
