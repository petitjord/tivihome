@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private val settingsCategories = listOf(
    "Général", "Compte", "Profils", "Contrôle parental", "Apparence", "Lecture", "Guide TV (EPG)", "Listes",
)

/**
 * Paramètres dans un panneau qui glisse depuis la droite, par-dessus l'écran en cours.
 * Premier niveau : les rubriques ; OK ouvre la rubrique ; Retour revient en arrière puis ferme.
 */
@Composable
fun SettingsPanel(nav: Nav, onClose: () -> Unit) {
    var cat by remember { mutableStateOf<Int?>(null) }
    var lastCat by remember { mutableIntStateOf(0) }
    androidx.activity.compose.BackHandler { if (cat != null) cat = null else onClose() }

    Box(Modifier.fillMaxSize().background(Color(0x8C000000))) {
        Column(
            Modifier.align(Alignment.CenterEnd).width(500.dp).fillMaxHeight()
                .background(Panel)
                .padding(start = 28.dp, end = 32.dp, top = 28.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cat != null) Text("‹  ", color = TextDim, fontSize = 22.sp)
                Text(
                    cat?.let { settingsCategories[it] } ?: "Paramètres",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            val c = cat
            if (c == null) {
                val first = rememberInitialFocus(true, "list")
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    settingsCategories.forEachIndexed { i, label ->
                        FocusBox(
                            onClick = { lastCat = i; cat = i },
                            focusRequester = if (i == lastCat) first else null,
                            background = Color.Transparent,
                            focusedBackground = Color.White,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { focused ->
                            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = if (focused) Bg else Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Text("›", color = if (focused) Bg else TextDim, fontSize = 20.sp)
                            }
                        }
                    }
                }
            } else {
                // Le focus entre directement dans la rubrique ouverte
                val inner = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(c) {
                    repeat(20) { kotlinx.coroutines.delay(40); if (runCatching { inner.requestFocus() }.isSuccess) return@LaunchedEffect }
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .focusRequester(inner).focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsContent(c, nav)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(cat: Int, nav: Nav) {
    val ctx = LocalContext.current
    val client = AppRepo.client
    when (cat) {
        0 -> {
            Title("Mise à jour")
            UpdateSettings()
            Title("Démarrage")
            Flow {
                Choice("Ouvrir l'accueil", !AppRepo.startOnLastChannel) { AppRepo.setStartLast(false) }
                Choice("Ouvrir la dernière chaîne", AppRepo.startOnLastChannel) { AppRepo.setStartLast(true) }
            }
            Title("Données")
            Flow {
                Choice("Actualiser les listes", false) {
                    AppRepo.clearCache()
                    Toast.makeText(ctx, "Les listes seront rechargées", Toast.LENGTH_SHORT).show()
                }
                Choice("Se déconnecter", false) { nav.logout() }
            }
            Text("Astuce : maintiens OK sur une chaîne, un film ou une série pour l'ajouter aux favoris.", color = TextDim, fontSize = 13.sp)
        }
        1 -> {
            val info = rememberLoad("account") { AppRepo.accountInfo() }
            Line("Serveur", client?.base ?: "—")
            Line("Utilisateur", client?.account?.username ?: "—")
            when (val st = info.state) {
                is Load.Ok -> {
                    Line("Statut", st.data.status ?: "—")
                    Line("Expiration", st.data.expiry?.let { SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(it)) } ?: "Illimité")
                    Line("Connexions max.", st.data.maxConnections ?: "—")
                }
                is Load.Loading -> Line("Statut", "chargement…")
                is Load.Err -> Line("Statut", "indisponible")
            }
            Title("Comptes enregistrés")
            AppRepo.accounts.forEach { acc ->
                val active = client != null && acc.server == client.base && acc.username == client.account.username
                Text(
                    (if (active) "● " else "○ ") + "${acc.username} — ${acc.server.removePrefix("http://").removePrefix("https://")}",
                    color = if (active) Accent else Color.White, fontSize = 14.sp, maxLines = 1,
                )
                if (!active) {
                    Flow {
                        Choice("Utiliser", false) { nav.switchAccount(acc) }
                        Choice("Supprimer", false) { AppRepo.removeAccount(acc) }
                    }
                }
            }
            Choice("+ Ajouter un compte", false) { nav.logout() }
        }
        2 -> ProfileSettings(nav)
        3 -> PinSettings()
        4 -> {
            Title("Couleur d'accent")
            Flow {
                AccentChoices.forEachIndexed { i, (name, color) ->
                    Choice(name, AppTheme.accentIndex == i, dot = color) { AppRepo.setAccent(i) }
                }
            }
            Title("Fond")
            Flow {
                Choice("Bleu nuit", !AppTheme.oled) { AppRepo.setOled(false) }
                Choice("Noir pur (OLED)", AppTheme.oled) { AppRepo.setOled(true) }
            }
            Title("Effets")
            Flow {
                Choice("Légers (fluide)", !AppTheme.richEffects) { AppRepo.setRichEffects(false) }
                Choice("Complets", AppTheme.richEffects) { AppRepo.setRichEffects(true) }
            }
            Text("Les effets complets ajoutent un fond animé et des halos ; à réserver aux boîtiers puissants.", color = TextDim, fontSize = 12.sp)
        }
        5 -> {
            Title("Format de la TV en direct")
            Flow {
                Choice("MPEG-TS (.ts)", AppRepo.liveFormat == "ts") { AppRepo.setFormat("ts") }
                Choice("HLS (.m3u8)", AppRepo.liveFormat == "m3u8") { AppRepo.setFormat("m3u8") }
            }
            Text("Si une chaîne ne démarre pas, essaie l'autre format. Tivihome bascule aussi tout seul en cas d'échec.", color = TextDim, fontSize = 13.sp)
            Title("Taille des sous-titres")
            Flow {
                listOf("Petits", "Normaux", "Grands").forEachIndexed { i, l ->
                    Choice(l, AppRepo.subtitleSize == i) { AppRepo.setSubSize(i) }
                }
            }
        }
        6 -> XmltvSettings()
        else -> {
            Title("Groupes masqués")
            if (AppRepo.hiddenGroups.isEmpty()) {
                Text("Aucun. Maintiens OK sur un groupe de chaînes, de films ou de séries pour le masquer ou le verrouiller.", color = TextDim, fontSize = 13.sp)
            } else {
                Text("${AppRepo.hiddenGroups.size} groupe(s) masqué(s).", color = Color.White, fontSize = 14.sp)
                Choice("Tout réafficher", false) { AppRepo.showAllGroups() }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Flow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Title(text: String) {
    Text(text.uppercase(), color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Line(label: String, value: String) {
    Row {
        Text(label, color = TextDim, fontSize = 14.sp, modifier = Modifier.width(150.dp))
        Text(value, color = Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        background = if (selected) AccentDim else Panel2,
        focusedBackground = Color.White,
        modifier = Modifier.height(42.dp),
    ) { focused ->
        Row(Modifier.padding(horizontal = 16.dp).height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(14.dp).background(dot, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, color = if (focused) Bg else Color.White, fontSize = 14.sp)
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
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                (if (active) "● " else "○ ") + p.name + if (p.kid) "  (enfant)" else "",
                color = if (active) Accent else Color.White, fontSize = 14.sp, modifier = Modifier.width(240.dp),
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
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = name, onValueChange = { name = it.take(20) }, singleLine = true,
                placeholder = { Text("Nom du profil") }, modifier = Modifier.width(240.dp),
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
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
