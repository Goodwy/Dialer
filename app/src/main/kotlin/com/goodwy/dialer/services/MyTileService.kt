package com.goodwy.dialer.services


import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.goodwy.commons.activities.ManageBlockedNumbersActivity
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.helpers.isQPlus
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
        if (isQPlus() && !baseConfig.blockUnknownNumbers) {
            if (isNotDefaultCallerIdApp()) {
                setDefaultCallerIdApp()
            } else {
                baseConfig.blockUnknownNumbers = !baseConfig.blockUnknownNumbers
                updateTile()
            }
        } else {
            baseConfig.blockUnknownNumbers = !baseConfig.blockUnknownNumbers
            updateTile()
        }
    }

    private fun updateTile() {
        qsTile?.state = if (baseConfig.blockUnknownNumbers) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
//        if (isQPlus()) qsTile?.subtitle =
//            if (baseConfig.blockUnknownNumbers) "On" else "Off"
        qsTile?.updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun isNotDefaultCallerIdApp(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java)
        return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun setDefaultCallerIdApp() {
        val intent = Intent(this, ManageBlockedNumbersActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.action = ManageBlockedNumbersActivity.SET_DEFAULT_CALLER_ID
        startActivityAndCollapse(intent)
    }
}
