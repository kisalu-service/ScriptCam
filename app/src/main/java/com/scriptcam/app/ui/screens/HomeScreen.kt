package com.scriptcam.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scriptcam.app.domain.ScriptText
import com.scriptcam.app.ui.AppViewModel
import com.scriptcam.app.ui.components.AppBackground
import com.scriptcam.app.ui.components.EmptyState
import com.scriptcam.app.ui.components.PillBadge
import com.scriptcam.app.ui.components.PrimaryButton
import com.scriptcam.app.ui.components.SecondaryButton
import com.scriptcam.app.ui.theme.Accent
import com.scriptcam.app.ui.theme.Ink600
import com.scriptcam.app.ui.theme.Ink700
import com.scriptcam.app.ui.theme.Ink800
import com.scriptcam.app.ui.theme.TextPrimary
import com.scriptcam.app.ui.theme.TextSecondary
import com.scriptcam.app.util.formatDurationHuman
import com.scriptcam.app.util.formatShortDate

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNewText: () -> Unit,
    onOpenText: (ScriptText) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLastVideo: () -> Unit
) {
    val texts by viewModel.texts.collectAsState()
    val recordings by viewModel.recordings.collectAsState()

    Scaffold(containerColor = Color.Transparent) { padding ->
        AppBackground {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ScriptCam",
                                style = MaterialTheme.typography.headlineLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Ton téléprompteur pour TikTok",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        IconButton(onClick = onOpenLibrary, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "Bibliothèque", tint = TextSecondary)
                        }
                        IconButton(onClick = onOpenSettings, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Paramètres", tint = TextSecondary)
                        }
                    }
                }

                item {
                    HeroCard(
                        hasTexts = texts.isNotEmpty(),
                        lastTitle = texts.firstOrNull()?.title,
                        onNewText = onNewText,
                        onStartWithLast = { texts.firstOrNull()?.let(onOpenText) }
                    )
                }

                if (recordings.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Derniers tournages",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Tout voir",
                                style = MaterialTheme.typography.labelMedium,
                                color = Accent,
                                modifier = Modifier.clickable(onClick = onOpenLibrary)
                            )
                        }
                    }
                    items(recordings.take(3)) { recording ->
                        RecentRecordingRow(
                            title = recording.scriptTitle ?: "Vidéo",
                            subtitle = "${formatShortDate(recording.createdAt)} · ${formatDurationHuman(recording.durationMs)}",
                            onClick = onOpenLastVideo
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Mes textes",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                if (texts.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.ContentPaste,
                            title = "Aucun texte enregistré",
                            message = "Colle ton script TikTok et ScriptCam le découpera automatiquement en phrases."
                        )
                    }
                } else {
                    items(texts, key = { it.id }) { text ->
                        TextCard(text = text, onClick = { onOpenText(text) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    hasTexts: Boolean,
    lastTitle: String?,
    onNewText: () -> Unit,
    onStartWithLast: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF12352A), Color(0xFF0E1B26), Color(0xFF101A2E))
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                PillBadge(text = "Prêt à filmer")
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Prépare ton texte, lance la caméra, lis et publie.",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                label = "Nouveau texte",
                onClick = onNewText,
                icon = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )
            if (hasTexts && lastTitle != null) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    label = "Reprendre : $lastTitle",
                    onClick = onStartWithLast,
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TextCard(text: ScriptText, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink800)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            PillBadge(
                text = formatDurationHuman(text.estimatedSeconds() * 1000L),
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = text.content.replace("\n", " ").trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillBadge(text = "${text.wordCount} mots", color = Accent)
            PillBadge(text = formatShortDate(text.updatedAt), color = TextSecondary)
        }
    }
}

@Composable
private fun RecentRecordingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink700)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ink600),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}
