package com.scriptcam.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scriptcam.app.data.PromptSettings
import com.scriptcam.app.prompt.PromptState

/**
 * Superposition du téléprompteur sur l'aperçu caméra.
 *
 * - Position verticale réglable ([PromptSettings.verticalPosition]) pour ne jamais masquer
 *   le visage.
 * - Couleur/transparence de fond réglables, miroir horizontal optionnel.
 * - Affichage d'une ou plusieurs phrases, surlignage progressif des mots déjà énoncés
 *   (repère de rythme discret).
 * - Transition douce (fondu + glissement) entre les phrases.
 */
@Composable
fun PrompterOverlay(
    state: PromptState,
    settings: PromptSettings,
    modifier: Modifier = Modifier,
    showProgressWords: Boolean = true
) {
    val containerAlpha = settings.backgroundAlpha.coerceIn(0f, 1f)
    val bgColor = Color(settings.backgroundArgb).copy(alpha = containerAlpha)
    val textColor = Color(settings.textColorArgb)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // La position verticale est proportionnelle à la hauteur réelle de l'écran :
        // 0 % = tout en haut, 100 % = tout en bas. Le texte ne masque donc jamais
        // systématiquement le visage.
        val fraction = settings.verticalPosition.coerceIn(0f, 0.9f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (containerAlpha <= 0.01f) Color.Transparent else bgColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .graphicsLayer { if (settings.mirrorText) scaleX = -1f },
                contentAlignment = Alignment.Center
            ) {
                if (state.sentences.isEmpty()) {
                    Text(
                        text = "Aucun texte à afficher",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = settings.fontSizeSp.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = state.index,
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 })
                                    .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 6 })
                            },
                            label = "phrase"
                        ) { targetIndex ->
                            val visible = state.sentences
                                .drop(targetIndex)
                                .take(state.sentencesAtOnce.coerceAtLeast(1))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                visible.forEachIndexed { offset, sentence ->
                                    val isCurrent = offset == 0
                                    val annotated = if (isCurrent && showProgressWords) {
                                        highlightSpoken(
                                            sentence = sentence,
                                            spokenWords = state.spokenWordCount,
                                            color = textColor,
                                            accent = Color(0xFF3DDC97)
                                        )
                                    } else {
                                        buildAnnotatedString {
                                            withStyle(SpanStyle(color = textColor.copy(alpha = 0.72f))) {
                                                append(sentence)
                                            }
                                        }
                                    }
                                    Text(
                                        text = annotated,
                                        fontSize = if (isCurrent) settings.fontSizeSp.sp
                                        else (settings.fontSizeSp * 0.72f).sp,
                                        lineHeight = (settings.fontSizeSp * settings.lineHeight).sp,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 3.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                ProgressDots(
                                    total = state.total,
                                    current = state.index,
                                    progress = state.progress,
                                    accent = Color(0xFF3DDC97)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Surligne les mots déjà prononcés (repère de rythme) sans distraire. */
private fun highlightSpoken(
    sentence: String,
    spokenWords: Int,
    color: Color,
    accent: Color
) = buildAnnotatedString {
    val tokens = sentence.split(" ")
    tokens.forEachIndexed { index, token ->
        val isSpoken = index < spokenWords
        withStyle(SpanStyle(color = if (isSpoken) accent else color)) {
            append(token)
            if (index != tokens.lastIndex) append(" ")
        }
    }
}

/**
 * Indicateur discret : un point par phrase (maximum 12 points, sinon barre).
 */
@Composable
private fun ProgressDots(total: Int, current: Int, progress: Float, accent: Color) {
    if (total <= 0) return
    if (total <= 12) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { i ->
                val isCurrent = i == current
                val isDone = i < current
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(if (isCurrent) 18.dp else 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                isDone -> accent.copy(alpha = 0.9f)
                                isCurrent -> accent.copy(alpha = 0.35f + 0.65f * progress)
                                else -> Color.White.copy(alpha = 0.25f)
                            }
                        )
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(((current + progress) / total).coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
        }
    }
}

/** Aperçu statique du rendu (utilisé dans l'écran de préparation). */
@Composable
fun PrompterPreview(
    sentences: List<String>,
    settings: PromptSettings,
    modifier: Modifier = Modifier
) {
    val previewState = PromptState(
        sentences = sentences,
        index = 0,
        progress = 0f,
        isPlaying = false,
        mode = settings.scrollMode,
        sentencesAtOnce = settings.sentencesAtOnce
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF101724))
    ) {
        PrompterOverlay(
            state = previewState,
            settings = settings,
            showProgressWords = false
        )
        Text(
            text = "Aperçu du téléprompteur",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
        )
    }
}
