package com.scriptcam.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scriptcam.app.ui.AppViewModel
import com.scriptcam.app.ui.components.AppBackground
import com.scriptcam.app.ui.components.PrompterSettingsSection
import com.scriptcam.app.ui.components.ScreenHeader
import com.scriptcam.app.ui.components.SecondaryButton
import com.scriptcam.app.ui.components.SectionCard
import com.scriptcam.app.ui.theme.Accent
import com.scriptcam.app.ui.theme.TextPrimary
import com.scriptcam.app.ui.theme.TextSecondary

/** Paramètres : préférences du téléprompteur, de la caméra et informations. */
@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        AppBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ScreenHeader(title = "Paramètres", onBack = onBack)
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        PrompterSettingsSection(
                            settings = settings,
                            onChange = { transform -> viewModel.updateSettings(transform) }
                        )
                    }

                    item {
                        SectionCard(title = "À propos") {
                            Text("ScriptCam 1.0.0", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Téléprompteur vidéo hors ligne : aucune connexion Internet, aucun compte, aucune publicité, aucun filigrane. Tes textes et tes vidéos restent sur ton téléphone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Vidéos enregistrées dans Movies/ScriptCam (galerie).",
                                style = MaterialTheme.typography.labelMedium,
                                color = Accent
                            )
                        }
                    }

                    item {
                        SecondaryButton(
                            label = "Réinitialiser les réglages",
                            onClick = { confirmReset = true },
                            destructive = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = com.scriptcam.app.ui.theme.Ink800,
            title = { Text("Réinitialiser les réglages ?", color = TextPrimary) },
            text = { Text("Les valeurs par défaut du téléprompteur seront restaurées. Tes textes et vidéos sont conservés.", color = TextSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.resetSettings()
                    confirmReset = false
                }) { Text("Réinitialiser", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmReset = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            }
        )
    }
}
