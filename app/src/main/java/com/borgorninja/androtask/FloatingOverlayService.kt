package com.borgorninja.androtask

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FloatingOverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: show floating record/stop bubble
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
