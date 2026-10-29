package com.petitjord.zaptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.petitjord.zaptv.data.AppRepo
import com.petitjord.zaptv.ui.App
import com.petitjord.zaptv.ui.ZapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppRepo.init(applicationContext)
        setContent {
            ZapTheme { App() }
        }
    }
}
