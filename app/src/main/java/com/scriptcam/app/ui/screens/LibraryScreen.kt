package com.scriptcam.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scriptcam.app.domain.Recording
import com.scriptcam.app.domain.ScriptText
import com.scriptcam.app.ui.AppViewModel
import com.scriptcam.app.ui.components.AppBackground
import com.scriptcam.app.ui.components.EmptyState
import com.scriptcam.app.ui.components.PillBadge
import com.scriptcam.app.ui.components.PrimaryButton
import com.scriptcam.app.ui.components.ScreenHeader
import com.scriptcam.app.ui.components.SegmentedSelector
import com.scriptcam.app.ui.theme.Accent
import com.scriptcam.app.ui.theme.Ink600
import com.scriptcam.app.ui.theme.Ink700
import com.scriptcam.app.ui.theme.Ink800
import com.scriptcam.app.ui.theme.TextPrimary
import com.scriptcam.app.ui.theme.TextSecondary
import com.scriptcam.app.util.formatDurationHuman
import com.scriptcam.app.util.formatShortDate
import com.scriptcam.app.util.formatSize

/** Bibliothèque locale : textes sauvegardés et vidéos enregistrées. */
@Composable
fun LibraryScreen(
    viewModel: AppViewModel,
    initialTab: Int = 0,
    onBack: () -> Unit,
    onOpenText: (ScriptText) -> Unit,
    onEditText: (ScriptText) -> Unit,
    onOpenRecording: (Recording) -> Unit,
    onNewText: () -> Unit,
    viewRecording: (Recording) -> Unit
) {
    val texts by viewModel.texts.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    var tab by remember { mutableStateOf(if (initialTab == 1) 1 else 0) }
    var textToDelete by remember { mutableStateOf<ScriptText?>(null) }
    var recordingToDelete by remember { mutableStateOf<Recording?>(null) }
    val context = LocalContext.current

    Scaffold(containerColor = Color.Transparent) { padding ->
        AppBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ScreenHeader(
                    title = "Bibliothèque",
                    subtitle = "${texts.size} texte(s) · ${recordings.size} vidéo(s)",
                    onBack = onBack
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SegmentedSelector(
                        options = listOf(0 to "Textes", 1 to "Vidéos"),
                        selected = tab,
                        onSelect = { tab = it }
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (tab == 0) {
                    if (texts.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Description,
                            title = "Aucun texte",
                            message = "Tes scripts apparaîtront ici, prêts à être filmés hors ligne.",
                            action = { PrimaryButton(label = "Créer un texte", onClick = onNewText) }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(texts, key = { it.id }) { text ->
                                LibraryTextRow(
                                    text = text,
                                    onClick = { onOpenText(text) },
                                    onEdit = { onEditText(text) },
                                    onDelete = { textToDelete = text }
                                )
                            }
                        }
                    }
                } else {
                    if (recordings.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.VideoLibrary,
                            title = "Aucune vidéo",
                            message = "Les vidéos filmées avec ScriptCam sont enregistrées dans ta galerie et listées ici."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recordings, key = { it.id }) { rec ->
                                LibraryVideoRow(
                                    recording = rec,
                                    onPlay = { onOpenRecording(rec) },
                                    onShare = {
                                        val intent = viewModel.exporter.buildShareIntent(
                                            Uri.parse(rec.uri), rec.mimeType, rec.scriptTitle
                                        )
                                        runCatching { context.startActivity(intent) }
                                    },
                                    onDelete = { recordingToDelete = rec }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    textToDelete?.let { text ->
        AlertDialog(
            onDismissRequest = { textToDelete = null },
            containerColor = Ink800,
            title = { Text("Supprimer « ${text.title} » ?", color = TextPrimary) },
            text = { Text("Le texte sera définitivement supprimé. Les vidéos déjà filmées restent intactes.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteText(text.id)
                    textToDelete = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { textToDelete = null }) { Text("Annuler", color = TextSecondary) }
            }
        )
    }

    recordingToDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            containerColor = Ink800,
            title = { Text("Supprimer cette vidéo ?", color = TextPrimary) },
            text = { Text("Elle sera aussi retirée de la galerie du téléphone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecording(rec)
                    recordingToDelete = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) { Text("Annuler", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun LibraryTextRow(
    text: ScriptText,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink800)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(text.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            text.content.replace("\n", " ").take(110),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillBadge(text = "${text.wordCount} mots")
            Spacer(Modifier.width(8.dp))
            PillBadge(text = formatDurationHuman(text.estimatedSeconds() * 1000L), color = TextSecondary)
            Spacer(Modifier.weight(1f))
            ActionChip(icon = Icons.Default.Edit, label = "Modifier", onClick = onEdit)
            Spacer(Modifier.width(6.dp))
            ActionChip(icon = Icons.Default.Delete, label = "Supprimer", onClick = onDelete, danger = true)
        }
    }
}

@Composable
private fun LibraryVideoRow(
    recording: Recording,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink700)
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ink600),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                recording.scriptTitle ?: "Vidéo",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatShortDate(recording.createdAt)} · ${formatDurationHuman(recording.durationMs)} · ${formatSize(recording.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        ActionChip(icon = Icons.Default.Share, label = null, onClick = onShare)
        Spacer(Modifier.width(6.dp))
        ActionChip(icon = Icons.Default.Delete, label = null, onClick = onDelete, danger = true)
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink600.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = if (label == null) 8.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (danger) MaterialTheme.colorScheme.error else TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (danger) MaterialTheme.colorScheme.error else TextSecondary
            )
        }
    }
}
