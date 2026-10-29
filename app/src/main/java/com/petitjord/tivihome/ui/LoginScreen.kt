package com.petitjord.tivihome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.petitjord.tivihome.data.Account
import com.petitjord.tivihome.data.AppRepo
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = rememberInitialFocus(true)

    fun submit() {
        if (server.isBlank() || user.isBlank() || pass.isBlank()) {
            error = "Remplis les trois champs."
            return
        }
        busy = true; error = null
        scope.launch {
            try {
                if (AppRepo.login(Account(server, user.trim(), pass))) onLoggedIn()
                else error = "Identifiants refusés par le serveur."
            } catch (e: Exception) {
                error = "Connexion impossible : ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Tivihome", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Accent)
            Text("Connexion Xtream Codes", color = TextDim, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Adresse du serveur") },
                placeholder = { Text("http://exemple.com:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(first),
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Nom d'utilisateur") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Color(0xFFFF6B6B)) }
            Button(onClick = { if (!busy) submit() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                else Text("Se connecter", fontSize = 18.sp)
            }
            Text(
                "Ces informations te sont fournies par ton abonnement IPTV. Elles restent enregistrées uniquement sur ce téléviseur.",
                color = TextDim, fontSize = 13.sp,
            )
        }
    }
}
