package com.petitjord.tivihome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
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
        Title("Mise à jour")
        UpdateSettings()

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

        Title("Comptes")
        AppRepo.accounts.forEach { acc ->
            val active = client != null && acc.server == client.base && acc.username == client.account.username
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (active) "● " else "○ ") + "${acc.username} — ${acc.server.removePrefix("http://").removePrefix("https://")}",
                    color = if (active) Accent else Color.White, fontSize = 14.sp, modifier = Modifier.width(380.dp), maxLines = 1,
                )
                if (!active) {
                    Choice("Utiliser", false) { nav.switchAccount(acc) }
                    Choice("Supprimer", false) { AppRepo.removeAccount(acc) }
                }
            }
        }
        Choice("+ Ajouter un compte", false) { nav.logout() }

        Title("Profils")
        ProfileSettings(nav)

        Title("Contrôle parental")
        PinSettings()

        Title("Apparence")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentChoices.forEachIndexed { i, (name, color) ->
                Choice(name, AppTheme.accentIndex == i, dot = color) { AppRepo.setAccent(i) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice("Fond bleu nuit", !AppTheme.oled) { AppRepo.setOled(false) }
            Choice("Noir pur (OLED)", AppTheme.oled) { AppRepo.setOled(true) }
        }

        Title("Démarrage")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice("Ouvrir l'accueil", !AppRepo.startOnLastChannel) { AppRepo.setStartLast(false) }
            Choice("Ouvrir la dernière chaîne", AppRepo.startOnLastChannel) { AppRepo.setStartLast(true) }
        }

        Title("Sous-titres")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Petits", "Normaux", "Grands").forEachIndexed { i, l ->
                Choice(l, AppRepo.subtitleSize == i) { AppRepo.setSubSize(i) }
            }
        }

        Title("Groupes masqués")
        if (AppRepo.hiddenGroups.isEmpty()) {
            Text("Aucun. Maintiens OK sur un groupe de chaînes, de films ou de séries pour le masquer.", color = TextDim, fontSize = 13.sp)
        } else {
            Text("${AppRepo.hiddenGroups.size} groupe(s) masqué(s).", color = Color.White, fontSize = 14.sp)
            Choice("Tout réafficher", false) { AppRepo.showAllGroups() }
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
private fun Choice(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        background = if (selected) AccentDim else Panel,
        modifier = Modifier.height(46.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp).height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(14.dp).background(dot, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(8.dp))
            }
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

@Composable
private fun UpdateSettings() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (com.petitjord.tivihome.data.Updater.status.isEmpty()) com.petitjord.tivihome.data.Updater.check(ctx)
    }
    Line("Version installée", com.petitjord.tivihome.data.Updater.installedName(ctx))
    Text(com.petitjord.tivihome.data.Updater.status.ifEmpty { "Vérification…" }, color = Color.White, fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (com.petitjord.tivihome.data.Updater.available != null) {
            Choice(if (com.petitjord.tivihome.data.Updater.busy) "Téléchargement…" else "Installer la mise à jour", true) {
                if (!com.petitjord.tivihome.data.Updater.busy) scope.launch { com.petitjord.tivihome.data.Updater.downloadAndInstall(ctx) }
            }
        }
        Choice("Rechercher une mise à jour", false) {
            scope.launch { com.petitjord.tivihome.data.Updater.check(ctx) }
        }
    }
}

@Composable
private fun ProfileSettings(nav: Nav) {
    var name by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var askPinFor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.petitjord.tivihome.data.Profile?>(null) }
    Text("Chaque profil a ses favoris, sa reprise de lecture, ses rappels et ses groupes masqués.", color = TextDim, fontSize = 13.sp)
    AppRepo.profiles.forEach { p ->
        val active = p.id == AppRepo.profileId
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (active) "● " else "○ ") + p.name + if (p.kid) "  (enfant)" else "",
                color = if (active) Accent else Color.White, fontSize = 14.sp, modifier = Modifier.width(260.dp),
            )
            if (!active) {
                Choice("Utiliser", false) {
                    val needsPin = !p.kid && AppRepo.profile.kid && AppRepo.parentalPin != null
                    if (needsPin) askPinFor = p else { AppRepo.switchProfile(p.id); nav.reload() }
                }
                if (p.id != 0 && !AppRepo.profile.kid) Choice("Supprimer", false) { AppRepo.removeProfile(p) }
            }
        }
    }
    if (!AppRepo.profile.kid) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = name, onValueChange = { name = it.take(20) }, singleLine = true,
                placeholder = { Text("Nom du profil") }, modifier = Modifier.width(260.dp),
            )
            Choice("+ Adulte", false) { AppRepo.addProfile(name, kid = false); name = "" }
            Choice("+ Enfant", false) { AppRepo.addProfile(name, kid = true); name = "" }
        }
    }
    askPinFor?.let { p ->
        PinDialog("Code parental", onDismiss = { askPinFor = null }) {
            askPinFor = null
            AppRepo.switchProfile(p.id)
            nav.reload()
        }
    }
}

@Composable
private fun PinSettings() {
    var pin by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var askOld by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val ctx = LocalContext.current
    Text(
        if (AppRepo.parentalPin == null) "Aucun code. Choisis un code de 4 chiffres ou plus, puis verrouille des groupes avec OK maintenu."
        else "Code actif · ${AppRepo.lockedGroups.size} groupe(s) verrouillé(s).",
        color = if (AppRepo.parentalPin == null) TextDim else Color.White, fontSize = 13.sp,
    )
    if (AppRepo.profile.kid) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.OutlinedTextField(
            value = pin, onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8) }, singleLine = true,
            placeholder = { Text("Nouveau code") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            modifier = Modifier.width(200.dp),
        )
        Choice(if (AppRepo.parentalPin == null) "Définir le code" else "Changer le code", false) {
            if (pin.length < 4) {
                Toast.makeText(ctx, "4 chiffres minimum", Toast.LENGTH_SHORT).show()
            } else if (AppRepo.parentalPin != null && !AppRepo.unlocked) {
                askOld = true
            } else {
                AppRepo.setPin(pin); pin = ""
                Toast.makeText(ctx, "Code enregistré", Toast.LENGTH_SHORT).show()
            }
        }
        if (AppRepo.parentalPin != null) {
            Choice("Supprimer le code", false) {
                if (!AppRepo.unlocked) askOld = true else AppRepo.setPin(null)
            }
        }
    }
    if (askOld) {
        PinDialog("Code actuel", onDismiss = { askOld = false }) {
            askOld = false
            Toast.makeText(ctx, "Code vérifié, recommence l'opération", Toast.LENGTH_SHORT).show()
        }
    }
}
