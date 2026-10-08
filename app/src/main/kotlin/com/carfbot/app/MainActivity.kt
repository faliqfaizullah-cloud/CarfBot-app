package com.carfbot.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var listenNow by mutableStateOf(false)

    private fun wantsListen(i: Intent?): Boolean =
        i != null && (i.getBooleanExtra("listen", false) ||
            i.action == Intent.ACTION_ASSIST ||
            i.action == Intent.ACTION_VOICE_COMMAND)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        listenNow = wantsListen(intent)
        setContent { CarfApp(listenNow) { listenNow = false } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (wantsListen(intent)) listenNow = true
    }
}
