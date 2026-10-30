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
        // Journal de plantage : on garde la cause pour l'afficher au prochain lancement
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                getSharedPreferences("tivihome", MODE_PRIVATE).edit()
                    .putString("lastCrash", android.util.Log.getStackTraceString(e).take(4000))
                    .commit()
            }
            previous?.uncaughtException(t, e)
        }
        AppRepo.init(applicationContext)
        com.petitjord.tivihome.ui.Fonts.init(applicationContext)
        // Logos et affiches chargés avec le même client réseau tolérant
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(applicationContext)
                .okHttpClient(com.petitjord.tivihome.data.Net.client)
                .crossfade(true)
                .build(),
        )
        setContent {
            TiviTheme { App() }
        }
    }
}
