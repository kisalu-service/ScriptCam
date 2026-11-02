package com.scriptcam.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.scriptcam.app.domain.Recording
import com.scriptcam.app.ui.AppViewModel
import com.scriptcam.app.ui.components.AppBackground
import com.scriptcam.app.ui.components.PillBadge
import com.scriptcam.app.ui.components.PrimaryButton
import com.scriptcam.app.ui.components.ScreenHeader
import com.scriptcam.app.ui.components.SecondaryButton
import com.scriptcam.app.ui.components.SectionCard
import com.scriptcam.app.ui.theme.Accent
import com.scriptcam.app.ui.theme.Ink800
import com.scriptcam.app.ui.theme.TextPrimary
import com.scriptcam.app.ui.theme.TextSecondary
import com.scriptcam.app.util.formatDate
import com.scriptcam.app.util.formatDurationHuman
import com.scriptcam.app.util.formatSize

/**
 * Aperçu après tournage : lecture de la vidéo, partage Android (TikTok, Instagram,
 * WhatsApp…), ouverture dans la galerie, suppression et nouveau tournage.
 */
@Composable
fun PreviewScreen(
    viewModel: AppViewModel,
    recording: Recording?,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }

    fun share() {
        val uri = recording?.uri?.let(Uri::parse) ?: return
        val intent = viewModel.exporter.buildShareIntent(uri, recording.mimeType, recording.scriptTitle)
        runCatching { context.startActivity(intent) }
    }

    fun openInGallery() {
        val uri = recording?.uri?.let(Uri::parse) ?: return
        runCatching {
            context.startActivity(viewModel.exporter.buildViewIntent(uri))
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        AppBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ScreenHeader(
                    title = "Aperçu de la vidéo",
                    subtitle = recording?.scriptTitle ?: "Tournage terminé",
                    onBack = onBack
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Lecteur vidéo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (recording == null) {
                            Text("Aucune vidéo", color = TextSecondary)
                        } else if (showPlayer) {
                            VideoPlayer(uri = Uri.parse(recording.uri))
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = "Lire",
                                    tint = Accent,
                                    modifier = Modifier
                                        .height(72.dp)
                                        .fillMaxWidth(0.25f)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text("Vidéo enregistrée", color = TextPrimary)
                                Spacer(Modifier.height(12.dp))
                                SecondaryButton(label = "Lire la vidéo", onClick = { showPlayer = true })
                            }
                        }
                    }

                    if (recording != null) {
                        SectionCard(title = "Détails") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillBadge(text = formatDurationHuman(recording.durationMs))
                                PillBadge(text = formatSize(recording.sizeBytes), color = TextSecondary)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = formatDate(recording.createdAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (recording.savedToGallery)
                                    "Enregistrée dans la galerie : Movies/ScriptCam"
                                else
                                    "Enregistrée dans le stockage de l'application",
                                style = MaterialTheme.typography.labelMedium,
                                color = Accent
                            )
                        }
                    }

                    PrimaryButton(
                        label = "Partager (TikTok, Instagram…)",
                        onClick = { share() },
                        icon = Icons.Default.Share,
                        enabled = recording != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            label = "Ouvrir dans la galerie",
                            onClick = { openInGallery() },
                            enabled = recording != null,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            label = "Bibliothèque",
                            onClick = onOpenLibrary,
                            icon = Icons.Default.VideoLibrary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            label = "Recommencer",
                            onClick = onRetake,
                            icon = Icons.Default.Replay,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            label = "Supprimer",
                            onClick = { showDeleteDialog = true },
                            icon = Icons.Default.Delete,
                            destructive = true,
                            enabled = recording != null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog && recording != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Ink800,
            title = { Text("Supprimer cette vidéo ?", color = TextPrimary) },
            text = { Text("La vidéo sera retirée de la galerie.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecording(recording)
                    showDeleteDialog = false
                    onBack()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler", color = TextSecondary) }
            }
        )
    }
}

/** Lecteur minimaliste basé sur VideoView (aucune dépendance supplémentaire). */
@Composable
private fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx: Context ->
            VideoView(ctx).apply {
                setVideoURI(uri)
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
        },
        update = { view ->
            if (!view.isPlaying) runCatching { view.start() }
        }
    )
}
