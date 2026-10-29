package com.petitjord.tivihome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petitjord.tivihome.data.AppRepo
import com.petitjord.tivihome.data.Category
import com.petitjord.tivihome.data.Profile

/** Catégorie affichable dans les listes « Tout » (ni masquée, ni verrouillée). */
fun visibleCat(id: String) = !AppRepo.groupInvisible(id) && !AppRepo.groupNeedsPin(id)

/** Saisie du code parental. */
@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = rememberInitialFocus(true)
    fun submit() {
        if (AppRepo.checkPin(pin)) onSuccess() else { wrong = true; pin = "" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(title, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8); wrong = false },
                    singleLine = true,
                    placeholder = { Text("Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.focusRequester(focus),
                )
                if (wrong) Text("Code incorrect", color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }
        },
        confirmButton = { DialogButton("Valider") { submit() } },
        dismissButton = { DialogButton("Annuler", onClick = onDismiss) },
    )
}

/** Menu d'un groupe (OK maintenu) : masquer ou verrouiller. */
@Composable
fun GroupMenu(group: Category, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val locked = group.id in AppRepo.lockedGroups
    var askPin by remember { mutableStateOf(false) }
    if (askPin) {
        PinDialog("Code parental", onDismiss = { askPin = false }) {
            askPin = false
            AppRepo.toggleLockedGroup(group.id); onChanged(); onDismiss()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(group.name, color = Color.White) },
        text = {
            Text(
                if (AppRepo.parentalPin == null) "Pour verrouiller un groupe, choisis d'abord un code parental dans Paramètres."
                else "Un groupe verrouillé demande le code parental, et il est invisible dans les profils enfant.",
                color = TextDim, fontSize = 14.sp,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (AppRepo.parentalPin != null) {
                    DialogButton(if (locked) "Déverrouiller" else "Verrouiller", requestFocus = true) {
                        // Retirer un verrou demande le code ; en poser un, non.
                        if (locked && !AppRepo.unlocked) askPin = true
                        else { AppRepo.toggleLockedGroup(group.id); onChanged(); onDismiss() }
                    }
                }
                DialogButton("Masquer", requestFocus = AppRepo.parentalPin == null) {
                    AppRepo.toggleHiddenGroup(group.id); onChanged(); onDismiss()
                }
            }
        },
        dismissButton = { DialogButton("Annuler", onClick = onDismiss) },
    )
}

@Composable
fun DialogButton(label: String, requestFocus: Boolean = false, onClick: () -> Unit) {
    val fr = rememberInitialFocus(requestFocus)
    FocusBox(
        onClick = onClick,
        focusRequester = if (requestFocus) fr else null,
        background = Panel2,
        modifier = Modifier.height(42.dp),
    ) {
        Box(Modifier.padding(horizontal = 18.dp).height(42.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 15.sp)
        }
    }
}

/** Contenu d'un groupe verrouillé tant que le code n'a pas été saisi. */
@Composable
fun LockedGroupView() {
    var ask by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("🔒", fontSize = 40.sp)
            Text("Ce groupe est protégé par le code parental.", color = Color.White, fontSize = 16.sp)
            DialogButton("Saisir le code", requestFocus = false) { ask = true }
        }
    }
    if (ask) PinDialog("Code parental", onDismiss = { ask = false }) { ask = false }
}

/** Choix du profil au lancement. */
@Composable
fun ProfilePicker(onPicked: () -> Unit) {
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    val first = rememberInitialFocus(true)
    fun pick(p: Profile) {
        // Passer d'un profil enfant à un profil adulte demande le code
        val needsPin = !p.kid && AppRepo.profile.kid && AppRepo.parentalPin != null && p.id != AppRepo.profileId
        if (needsPin) pinFor = p else { AppRepo.switchProfile(p.id); onPicked() }
    }
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Text("Qui regarde ?", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                AppRepo.profiles.forEachIndexed { i, p ->
                    FocusBox(
                        onClick = { pick(p) },
                        focusRequester = if (p.id == AppRepo.profileId || (i == 0 && AppRepo.profiles.none { it.id == AppRepo.profileId })) first else null,
                        background = Color.Transparent,
                        focusedBackground = Panel2,
                        scaleOnFocus = 1.08f,
                        modifier = Modifier.width(150.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(96.dp).background(if (p.kid) Gold else Accent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(p.name.take(1).uppercase(), color = Color.Black, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(p.name, color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
                            if (p.kid) Text("Enfant", color = TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    pinFor?.let { p ->
        PinDialog("Code parental", onDismiss = { pinFor = null }) {
            pinFor = null
            AppRepo.switchProfile(p.id)
            onPicked()
        }
    }
}
