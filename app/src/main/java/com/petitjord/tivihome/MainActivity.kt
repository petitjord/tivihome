package com.petitjord.tivihome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.ui.App
import com.petitjord.tivihome.ui.TiviTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppRepo.init(applicationContext)
        setContent {
            TiviTheme { App() }
        }
    }
}
