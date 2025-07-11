package com.agospace.bokob

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.exoplayer.ExoPlayer

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerService : Service() {
    private lateinit var player: ExoPlayer
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        PlayerController.init(applicationContext, player)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, PlayerController.buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        player.release()
        PlayerController.hidePlayerOverlay()
        super.onDestroy()
    }
}