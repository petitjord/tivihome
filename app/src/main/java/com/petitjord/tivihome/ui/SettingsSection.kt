package com.petitjord.tivihome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.petitjord.tivihome.data.AppRepo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsSection(nav: Nav) {
    val ctx = LocalContext.current
    val client = AppRepo.client
    val info = rememberLoad("account") { AppRepo.accountInfo() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title("Compte")
        Line("Serveur", client?.base ?: "—")
        Line("Utilisateur", client?.account?.username ?: "—")
        when (val s = info.state) {
            is Load.Ok -> {
                Line("Statut", s.data.status ?: "—")
                Line("Expiration", s.data.expiry?.let {
                    SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(it))
                } ?: "Illimité")
                Line("Connexions max.", s.data.maxConnections ?: "—")
            }
            is Load.Loading -> Line("Statut", "chargement…")
            is Load.Err -> Line("Statut", "indisponible")
        }

        Title("Lecture de la TV en direct")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice("MPEG-TS (.ts)", AppRepo.liveFormat == "ts") { AppRepo.setFormat("ts") }
            Choice("HLS (.m3u8)", AppRepo.liveFormat == "m3u8") { AppRepo.setFormat("m3u8") }
        }
        Text(
            "Si une chaîne ne démarre pas, essaie l'autre format. Tivihome bascule aussi automatiquement en cas d'échec.",
            color = TextDim, fontSize = 13.sp,
        )

        Title("Guide TV externe (XMLTV)")
        XmltvSettings()

        Title("Données")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice("Actualiser les listes", false) {
                AppRepo.clearCache()
                Toast.makeText(ctx, "Les listes seront rechargées", Toast.LENGTH_SHORT).show()
            }
            Choice("Se déconnecter", false) { nav.logout() }
        }
        Text(
            "Astuce : maintiens OK sur une chaîne, un film ou une série pour l'ajouter aux favoris.",
            color = TextDim, fontSize = 13.sp,
        )
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun Line(label: String, value: String) {
    Row {
        Text(label, color = TextDim, fontSize = 15.sp, modifier = Modifier.width(170.dp))
        Text(value, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        background = if (selected) AccentDim else Panel,
        modifier = Modifier.height(46.dp),
    ) {
        Box(Modifier.padding(horizontal = 20.dp).height(46.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
private fun XmltvSettings() {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var url by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(AppRepo.xmltvUrl) }
    Text(
        "Colle le lien d'un guide au format XMLTV (.xml ou .xml.gz). Il remplace le guide du fournisseur pour les chaînes qu'il connaît. Laisse vide pour garder celui du fournisseur.",
        color = TextDim, fontSize = 13.sp,
    )
    androidx.compose.material3.OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        singleLine = true,
        placeholder = { Text("https://exemple.com/guide.xml.gz") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Choice("Enregistrer et charger", false) {
            AppRepo.updateXmltvUrl(url)
            scope.launch { AppRepo.loadXmltv(force = true) }
        }
        Choice("Retirer", false) {
            url = ""
            AppRepo.updateXmltvUrl("")
        }
    }
    Text(AppRepo.xmltvStatus, color = Color.White, fontSize = 14.sp)
}
