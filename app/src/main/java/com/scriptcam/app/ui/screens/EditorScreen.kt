package com.scriptcam.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scriptcam.app.domain.SentenceSplitter
import com.scriptcam.app.domain.ScriptText
import com.scriptcam.app.ui.AppViewModel
import com.scriptcam.app.ui.components.AppBackground
import com.scriptcam.app.ui.components.InlineNotice
import com.scriptcam.app.ui.components.PillBadge
import com.scriptcam.app.ui.components.PrimaryButton
import com.scriptcam.app.ui.components.ScreenHeader
import com.scriptcam.app.ui.components.SecondaryButton
import com.scriptcam.app.ui.components.SectionCard
import com.scriptcam.app.ui.theme.Accent
import com.scriptcam.app.ui.theme.Ink600
import com.scriptcam.app.ui.theme.Ink700
import com.scriptcam.app.ui.theme.Ink800
import com.scriptcam.app.ui.theme.TextPrimary
import com.scriptcam.app.ui.theme.TextSecondary

/**
 * Éditeur de texte : saisie, collage depuis le presse-papiers, aperçu du découpage
 * en phrases, enregistrement, suppression, puis lancement du tournage.
 */
@Composable
fun EditorScreen(
    viewModel: AppViewModel,
    textId: String?,
    initialText: String?,
    onBack: () -> Unit,
    onStartShooting: (ScriptText) -> Unit
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf(initialText.orEmpty()) }
    var loaded by remember { mutableStateOf(textId == null) }
    var savedId by rememberSaveable { mutableStateOf(textId) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSentencePreview by remember { mutableStateOf(false) }

    LaunchedEffect(textId) {
        if (!loaded && textId != null) {
            viewModel.getText(textId)?.let { existing ->
                title = existing.title
                content = existing.content
                savedId = existing.id
            }
            loaded = true
        }
    }

    val sentences = remember(content) { SentenceSplitter.split(content) }
    val wordCount = remember(content) { content.split(Regex("\\s+")).count { it.isNotBlank() } }
    val estimatedSeconds = ((wordCount / 150f) * 60f).toInt()

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val pasted = clip.getItemAt(0).coerceToText(context).toString()
            if (pasted.isNotBlank()) {
                content = if (content.isBlank()) pasted else "$content\n\n$pasted"
                if (title.isBlank()) {
                    title = pasted.trim().take(40).substringBefore('.').ifBlank { "Mon script" }
                }
            }
        }
    }

    fun save(onSaved: (ScriptText) -> Unit = {}) {
        val finalTitle = title.ifBlank {
            content.trim().take(40).substringBefore('.').ifBlank { "Sans titre" }
        }
        viewModel.saveText(savedId, finalTitle, content) { saved ->
            savedId = saved.id
            title = saved.title
            onSaved(saved)
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        AppBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ScreenHeader(
                    title = if (savedId == null) "Nouveau texte" else "Modifier le texte",
                    subtitle = "${sentences.size} phrase(s) · $wordCount mots · ~${estimatedSeconds}s",
                    onBack = onBack,
                    actions = {
                        if (savedId != null) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = TextSecondary)
                            }
                        }
                    }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Titre du texte") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SecondaryButton(
                                label = "Coller",
                                onClick = { pasteFromClipboard() },
                                icon = Icons.Default.ContentPaste,
                                modifier = Modifier.weight(1f)
                            )
                            SecondaryButton(
                                label = "Aperçu phrases",
                                onClick = { showSentencePreview = !showSentencePreview },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Ton texte") },
                            placeholder = { Text("Colle ici ton script TikTok…", color = TextSecondary) },
                            minLines = 8,
                            maxLines = 20,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillBadge(text = "${sentences.size} phrases", color = Accent)
                            PillBadge(text = "$wordCount mots", color = TextSecondary)
                            PillBadge(text = "≈ $estimatedSeconds s", color = TextSecondary)
                        }
                    }

                    if (showSentencePreview && sentences.isNotEmpty()) {
                        item {
                            SectionCard(title = "Découpage automatique") {
                                sentences.forEachIndexed { index, sentence ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Accent,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Text(
                                            text = sentence,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (content.isNotBlank() && sentences.isEmpty()) {
                        item {
                            InlineNotice(
                                text = "Le texte ne contient pas encore de phrase complète. Ajoute un point pour marquer une fin de phrase.",
                                color = com.scriptcam.app.ui.theme.Warning
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        PrimaryButton(
                            label = "Commencer à filmer",
                            onClick = {
                                save { saved -> onStartShooting(saved) }
                            },
                            icon = Icons.Default.PlayArrow,
                            enabled = content.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton(
                            label = "Enregistrer le texte",
                            onClick = { save() },
                            icon = Icons.Default.Save,
                            enabled = content.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Ink800,
            title = { Text("Supprimer ce texte ?", color = TextPrimary) },
            text = { Text("Cette action est définitive.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    savedId?.let(viewModel::deleteText)
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

@Composable
internal fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Ink600,
    cursorColor = Accent,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary,
    focusedContainerColor = Ink800,
    unfocusedContainerColor = Ink800
)
