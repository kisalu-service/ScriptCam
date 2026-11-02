package com.scriptcam.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scriptcam.app.data.StudioOrientation
import com.scriptcam.app.domain.ScrollMode
import com.scriptcam.app.domain.SentenceSplitter
import com.scriptcam.app.ui.AppViewModel
import com.scriptcam.app.ui.components.AppBackground
import com.scriptcam.app.ui.components.PillBadge
import com.scriptcam.app.ui.components.PrimaryButton
import com.scriptcam.app.ui.components.PrompterPreview
import com.scriptcam.app.ui.components.PrompterSettingsSection
import com.scriptcam.app.ui.components.ScreenHeader
import com.scriptcam.app.ui.components.SectionCard
import com.scriptcam.app.ui.components.SegmentedSelector
import com.scriptcam.app.ui.components.ToggleRow
import com.scriptcam.app.ui.theme.Accent
import com.scriptcam.app.ui.theme.TextPrimary
import com.scriptcam.app.ui.theme.TextSecondary
import com.scriptcam.app.util.formatDurationHuman

/**
 * Préparation du tournage : choix de la caméra, du mode de défilement, de l'orientation,
 * et aperçu en direct du rendu du téléprompteur.
 */
@Composable
fun PrepareScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onStartStudio: () -> Unit,
    onEditText: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val session by viewModel.session.collectAsState()
    val sentences = SentenceSplitter.split(session.scriptContent)
    val totalWords = session.scriptContent.split(Regex("\\s+")).count { it.isNotBlank() }
    val estimatedMs = if (settings.wordsPerMinute > 0) {
        (totalWords.toFloat() / settings.wordsPerMinute * 60_000f).toLong()
    } else 0L

    Scaffold(containerColor = Color.Transparent) { padding ->
        AppBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ScreenHeader(
                    title = "Préparer le tournage",
                    subtitle = session.scriptTitle.ifBlank { "Sans titre" },
                    onBack = onBack
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SectionCard(title = "Ton script") {
                            Text(
                                text = session.scriptContent.take(220) + if (session.scriptContent.length > 220) "…" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillBadge(text = "${sentences.size} phrases", color = Accent)
                                PillBadge(text = "≈ ${formatDurationHuman(estimatedMs)}", color = TextSecondary)
                            }
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                label = "Modifier le texte",
                                onClick = onEditText,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        SectionCard(title = "Aperçu du rendu") {
                            PrompterPreview(
                                sentences = sentences.take(3),
                                settings = settings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "L'aperçu reflète tes réglages : taille, couleurs, transparence et position.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    item {
                        SectionCard(title = "Caméra et cadrage") {
                            Text("Caméra", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            SegmentedSelector(
                                options = listOf(true to "Frontale", false to "Arrière"),
                                selected = session.useFrontCamera,
                                onSelect = { front ->
                                    viewModel.updateSessionTransform { it.copy(useFrontCamera = front) }
                                }
                            )
                            Spacer(Modifier.height(14.dp))
                            Text("Orientation", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            SegmentedSelector(
                                options = listOf(
                                    StudioOrientation.PORTRAIT to "Vertical 9:16",
                                    StudioOrientation.LANDSCAPE to "Horizontal",
                                    StudioOrientation.AUTO to "Auto"
                                ),
                                selected = session.orientation,
                                onSelect = { orientation ->
                                    viewModel.updateSessionTransform { it.copy(orientation = orientation) }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Vertical 9:16 = format TikTok / Reels / Shorts.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    item {
                        PrompterSettingsSection(
                            settings = settings,
                            onChange = { transform -> viewModel.updateSettings(transform) }
                        )
                    }

                    item {
                        SectionCard(title = "Téléprompteur") {
                            ToggleRow(
                                label = "Mode intelligent recommandé",
                                description = "Défilement adapté au nombre de mots, contrôle manuel permanent",
                                checked = settings.scrollMode == ScrollMode.SMART,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettings {
                                        it.copy(scrollMode = if (enabled) ScrollMode.SMART else ScrollMode.AUTO)
                                    }
                                }
                            )
                        }
                    }

                    item {
                        PrimaryButton(
                            label = "Lancer le studio",
                            onClick = onStartStudio,
                            icon = Icons.Default.Videocam,
                            enabled = session.hasScript,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
