package com.ladb.reborn.services

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.ladb.reborn.MainActivity

/** Quick Settings tile that opens the terminal. */
class QsTileService : TileService() {

    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = android.app.PendingIntent.getActivity(
                applicationContext, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
