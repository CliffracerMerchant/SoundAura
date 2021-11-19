/* This file is part of SoundObservatory, which is released under the
 * Apache License 2.0. See license.md in the project's root directory
 * or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat

class SoundObservatoryApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        val playerServiceIntent = Intent(this, PlayerService::class.java)
        //ContextCompat.startForegroundService(this, playerServiceIntent)
        startService(playerServiceIntent)
    }
}