package com.scriptcam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scriptcam.app.data.PromptSettings
import com.scriptcam.app.domain.ScrollMode
import com.scriptcam.app.ui.theme.Accent
import com.scriptcam.app.ui.theme.Ink600
import com.scriptcam.app.ui.theme.Ink700
import com.scriptcam.app.ui.theme.TextPrimary
import com.scriptcam.app.ui.theme.TextSecondary

/** Palette de couleurs proposées pour le texte et le fond du téléprompteur. */
val TextColorChoices = listOf(
    "Blanc" to Color(0xFFFFFFFF),
    "Crème" to Color(0xFFFFF3D6),
    "Jaune" to Color(0xFFFFE066),
    "Menthe" to Color(0xFF7CFFC4),
    "Ciel" to Color(0xFF9CD2FF)
)

val BackgroundColorChoices = listOf(
    "Noir" to Color(0xFF000000),
    "Nuit" to Color(0xFF0B1220),
    "Bleu" to Color(0xFF122B4A),
    "Vert" to Color(0xFF10352A),
    "Gris" to Color(0xFF2A2F3A)
)

/**
 * Bloc de réglages du téléprompteur, partagé entre l'écran de préparation
 * et l'écran des paramètres pour garantir un comportement identique.
 */
@Composable
fun PrompterSettingsSection(
    settings: PromptSettings,
    onChange: ((PromptSettings) -> PromptSettings) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        SectionCard(title = "Mode de défilement") {
            SegmentedSelector(
                options = listOf(
                    ScrollMode.SMART to "Intelligent",
                    ScrollMode.AUTO to "Auto",
                    ScrollMode.MANUAL to "Manuel"
                ),
                selected = settings.scrollMode,
                onSelect = { mode -> onChange { it.copy(scrollMode = mode) } }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (settings.scrollMode) {
                    ScrollMode.SMART ->
                        "Le texte avance à ton rythme estimé, et tu peux toujours revenir en arrière ou passer à la phrase suivante sans couper la vidéo."
                    ScrollMode.AUTO ->
                        "Chaque phrase reste affichée une durée fixe, que tu peux régler ci-dessous."
                    ScrollMode.MANUAL ->
                        "La phrase ne change que lorsque tu appuies sur « Suivant »."
                },
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(12.dp))
            SettingSlider(
                label = "Rythme de lecture",
                value = settings.wordsPerMinute.toFloat(),
                valueRange = 80f..260f,
                steps = 17,
                valueLabel = "${settings.wordsPerMinute} mots/min",
                onValueChange = { v -> onChange { it.copy(wordsPerMinute = v.toInt()) } }
            )
            Text(
                text = "Repère : une diction posée tourne autour de 140-160 mots/min.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        SectionCard(title = "Affichage") {
            SettingSlider(
                label = "Taille du texte",
                value = settings.fontSizeSp.toFloat(),
                valueRange = 16f..56f,
                valueLabel = "${settings.fontSizeSp} pt",
                onValueChange = { v -> onChange { it.copy(fontSizeSp = v.toInt()) } }
            )
            SettingSlider(
                label = "Espacement des lignes",
                value = settings.lineHeight,
                valueRange = 1.0f..1.8f,
                valueLabel = String.format(java.util.Locale.FRANCE, "%.2f", settings.lineHeight),
                onValueChange = { v -> onChange { it.copy(lineHeight = v) } }
            )
            SettingSlider(
                label = "Position verticale",
                value = settings.verticalPosition,
                valueRange = 0f..0.9f,
                valueLabel = when {
                    settings.verticalPosition < 0.25f -> "Haut"
                    settings.verticalPosition < 0.55f -> "Milieu"
                    else -> "Bas"
                },
                onValueChange = { v -> onChange { it.copy(verticalPosition = v) } }
            )

            Spacer(Modifier.height(6.dp))
            Text("Phrases affichées simultanément", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            SegmentedSelector(
                options = listOf(1 to "1", 2 to "2", 3 to "3"),
                selected = settings.sentencesAtOnce,
                onSelect = { count -> onChange { it.copy(sentencesAtOnce = count) } }
            )

            Spacer(Modifier.height(14.dp))
            Text("Couleur du texte", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextColorChoices.forEach { (_, color) ->
                    ColorSwatch(
                        color = color,
                        selected = settings.textColorArgb == color.value.toInt(),
                        onClick = { onChange { it.copy(textColorArgb = color.value.toInt()) } }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Couleur de l'arrière-plan", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackgroundColorChoices.forEach { (_, color) ->
                    ColorSwatch(
                        color = color,
                        selected = settings.backgroundArgb == color.value.toInt(),
                        onClick = { onChange { it.copy(backgroundArgb = color.value.toInt()) } }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            SettingSlider(
                label = "Transparence du fond",
                value = settings.backgroundAlpha,
                valueRange = 0f..1f,
                valueLabel = "${(settings.backgroundAlpha * 100).toInt()} %",
                onValueChange = { v -> onChange { it.copy(backgroundAlpha = v) } }
            )
        }

        if (!compact) {
            SectionCard(title = "Tournage") {
                ToggleRow(
                    label = "Texte en miroir",
                    description = "Inverse l'affichage horizontalement",
                    checked = settings.mirrorText,
                    onCheckedChange = { v -> onChange { it.copy(mirrorText = v) } }
                )
                ToggleRow(
                    label = "Garder l'écran allumé",
                    description = "Évite la mise en veille pendant le tournage",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { v -> onChange { it.copy(keepScreenOn = v) } }
                )
                ToggleRow(
                    label = "Caméra frontale par défaut",
                    checked = settings.useFrontCameraByDefault,
                    onCheckedChange = { v -> onChange { it.copy(useFrontCameraByDefault = v) } }
                )
                ToggleRow(
                    label = "Mode basse lumière (720p)",
                    description = "Capteur plus sensible : image nettement plus lumineuse de nuit, résolution réduite",
                    checked = settings.lowLightMode,
                    onCheckedChange = { v -> onChange { it.copy(lowLightMode = v) } }
                )
                ToggleRow(
                    label = "Enregistrer dans la galerie",
                    description = "Les vidéos vont dans Movies/ScriptCam",
                    checked = settings.saveToGallery,
                    onCheckedChange = { v -> onChange { it.copy(saveToGallery = v) } }
                )
                Spacer(Modifier.height(10.dp))
                Text("Compte à rebours avant l'enregistrement", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                SegmentedSelector(
                    options = listOf(0 to "Aucun", 3 to "3 s", 5 to "5 s"),
                    selected = if (settings.countdownSeconds == 0) 0 else if (settings.countdownSeconds <= 3) 3 else 5,
                    onSelect = { seconds -> onChange { it.copy(countdownSeconds = seconds) } }
                )
            }
        }
    }
}

/** Petit bouton rond utilitaire (utilisé dans le studio). */
@Composable
fun RoundIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Int = 52,
    background: Color = Ink700.copy(alpha = 0.85f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, Ink600.copy(alpha = 0.7f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Bandeau d'avertissement discret. */
@Composable
fun InlineNotice(text: String, modifier: Modifier = Modifier, color: Color = Accent) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun LabelValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
