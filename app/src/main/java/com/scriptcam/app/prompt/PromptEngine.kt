package com.scriptcam.app.prompt

import com.scriptcam.app.domain.ScrollMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État affiché par le téléprompteur.
 *
 * @param index index de la première phrase visible (0-based)
 * @param progress avancement de la phrase courante, de 0 à 1
 * @param isPlaying défilement en cours (ou en attente d'appui en mode manuel)
 * @param finished vrai lorsque la dernière phrase est terminée
 */
data class PromptState(
    val sentences: List<String> = emptyList(),
    val index: Int = 0,
    val progress: Float = 0f,
    val isPlaying: Boolean = false,
    val finished: Boolean = false,
    val mode: ScrollMode = ScrollMode.SMART,
    val sentencesAtOnce: Int = 1
) {
    val total: Int get() = sentences.size
    val currentSentence: String get() = sentences.getOrElse(index) { "" }
    val visibleSentences: List<String>
        get() = sentences.drop(index).take(sentencesAtOnce.coerceAtLeast(1))
    val humanIndex: Int get() = if (total == 0) 0 else index + 1
    val overallProgress: Float
        get() = if (total == 0) 0f else ((index + progress) / total).coerceIn(0f, 1f)
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < total - 1

    /** Nombre de mots de la phrase courante déjà « énoncés » (surlignage progressif). */
    val spokenWordCount: Int
        get() {
            val words = wordCount(currentSentence)
            return if (words == 0) 0 else (progress * words).toInt().coerceIn(0, words)
        }
}

internal fun wordCount(sentence: String): Int =
    sentence.split(Regex("\\s+")).count { it.isNotBlank() }

/**
 * Moteur du téléprompteur — logique pure, sans dépendance Android (testable en JUnit).
 *
 * Modes :
 *  - [ScrollMode.AUTO]   : durée fixe par phrase ([secondsPerSentence]), ajustable en direct.
 *  - [ScrollMode.SMART]  : durée calculée phrase par phrase à partir du nombre de mots
 *                          et de [wordsPerMinute] ; l'utilisateur garde le contrôle total
 *                          (pause, précédent, suivant) sans interrompre l'enregistrement.
 *  - [ScrollMode.MANUAL] : aucune avance automatique, uniquement sur commande.
 *
 * Le moteur n'utilise pas d'horloge interne : l'appelant pousse le temps écoulé via [tick].
 * Cela rend le comportement déterministe et testable, et évite de dépendre du framerate.
 */
class PromptEngine(
    sentences: List<String> = emptyList(),
    mode: ScrollMode = ScrollMode.SMART,
    wordsPerMinute: Int = 150,
    secondsPerSentence: Float = 4f,
    sentencesAtOnce: Int = 1
) {
    private val _state = MutableStateFlow(
        PromptState(
            sentences = sentences,
            mode = mode,
            sentencesAtOnce = sentencesAtOnce.coerceIn(1, 3)
        )
    )
    val state: StateFlow<PromptState> = _state.asStateFlow()

    var wordsPerMinute: Int = wordsPerMinute.coerceIn(60, 400)
        private set
    var secondsPerSentence: Float = secondsPerSentence.coerceIn(1f, 30f)
        private set

    private var elapsedInSentenceMs: Long = 0L

    val current: PromptState get() = _state.value

    // -------------------------------------------------------------- Contenu

    fun setSentences(sentences: List<String>) {
        elapsedInSentenceMs = 0L
        _state.value = _state.value.copy(
            sentences = sentences,
            index = 0,
            progress = 0f,
            finished = false,
            isPlaying = false
        )
    }

    fun setMode(mode: ScrollMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setSentencesAtOnce(count: Int) {
        _state.value = _state.value.copy(sentencesAtOnce = count.coerceIn(1, 3))
    }

    fun setWordsPerMinute(wpm: Int) {
        wordsPerMinute = wpm.coerceIn(60, 400)
    }

    fun setSecondsPerSentence(seconds: Float) {
        secondsPerSentence = seconds.coerceIn(1f, 30f)
    }

    // ------------------------------------------------------------ Commandes

    /** Démarre (ou reprend) le défilement. */
    fun play() {
        if (_state.value.sentences.isEmpty()) return
        val s = _state.value
        val restartFromEnd = s.finished || s.index >= s.total
        if (restartFromEnd) {
            elapsedInSentenceMs = 0L
            _state.value = s.copy(index = 0, progress = 0f, finished = false, isPlaying = true)
        } else {
            _state.value = s.copy(isPlaying = true)
        }
    }

    fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    /** Phrase suivante (contrôle manuel, fonctionne aussi pendant la lecture). */
    fun next() {
        val s = _state.value
        if (s.sentences.isEmpty()) return
        elapsedInSentenceMs = 0L
        if (s.index >= s.total - 1) {
            _state.value = s.copy(index = (s.total - 1).coerceAtLeast(0), progress = 1f, finished = true, isPlaying = false)
        } else {
            _state.value = s.copy(index = s.index + 1, progress = 0f, finished = false)
        }
    }

    /** Phrase précédente. */
    fun previous() {
        val s = _state.value
        if (s.sentences.isEmpty()) return
        elapsedInSentenceMs = 0L
        _state.value = s.copy(
            index = (s.index - 1).coerceAtLeast(0),
            progress = 0f,
            finished = false
        )
    }

    /** Revient à la première phrase et relance la lecture. */
    fun restart(autoPlay: Boolean = true) {
        elapsedInSentenceMs = 0L
        _state.value = _state.value.copy(
            index = 0,
            progress = 0f,
            finished = false,
            isPlaying = autoPlay && _state.value.sentences.isNotEmpty()
        )
    }

    /** Saute directement à une phrase (utilisé par l'indicateur de progression). */
    fun seekTo(index: Int) {
        val s = _state.value
        if (s.sentences.isEmpty()) return
        elapsedInSentenceMs = 0L
        _state.value = s.copy(
            index = index.coerceIn(0, s.total - 1),
            progress = 0f,
            finished = false
        )
    }

    // --------------------------------------------------------- Temps / tick

    /** Durée d'affichage visée pour la phrase courante, en millisecondes. */
    fun targetDurationMs(state: PromptState = _state.value): Long {
        val words = wordCount(state.currentSentence).coerceAtLeast(1)
        return when (state.mode) {
            ScrollMode.AUTO -> (secondsPerSentence * 1000f).toLong()
            ScrollMode.SMART -> ((words.toFloat() / wordsPerMinute) * 60_000f).toLong().coerceAtLeast(600L)
            ScrollMode.MANUAL -> Long.MAX_VALUE
        }
    }

    /**
     * Avance l'horloge du téléprompteur.
     * @return vrai si une transition de phrase a eu lieu (permet de déclencher une animation).
     */
    fun tick(deltaMs: Long): Boolean {
        val s = _state.value
        if (!s.isPlaying || s.sentences.isEmpty() || deltaMs <= 0L) return false
        if (s.mode == ScrollMode.MANUAL) {
            // En manuel, on ne fait qu'animer doucement la barre de progression.
            _state.value = s.copy(progress = 0f)
            return false
        }
        val target = targetDurationMs(s)
        elapsedInSentenceMs += deltaMs
        if (elapsedInSentenceMs >= target) {
            return if (s.index >= s.total - 1) {
                _state.value = s.copy(progress = 1f, isPlaying = false, finished = true)
                false
            } else {
                elapsedInSentenceMs = 0L
                _state.value = s.copy(index = s.index + 1, progress = 0f, finished = false)
                true
            }
        }
        val progress = (elapsedInSentenceMs.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        _state.value = s.copy(progress = progress)
        return false
    }
}
