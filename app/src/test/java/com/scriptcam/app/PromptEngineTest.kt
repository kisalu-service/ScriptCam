package com.scriptcam.app

import com.scriptcam.app.domain.ScrollMode
import com.scriptcam.app.prompt.PromptEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptEngineTest {

    private val phrases = listOf(
        "Beaucoup de personnes veulent réussir.",
        "Mais peu acceptent le temps que cela demande.",
        "La vraie question est de savoir si nous sommes prêts."
    )

    private fun engine(mode: ScrollMode = ScrollMode.SMART, wpm: Int = 150) =
        PromptEngine(phrases, mode = mode, wordsPerMinute = wpm)

    @Test
    fun demarre_sur_la_premiere_phrase() {
        val e = engine()
        assertEquals(0, e.current.index)
        assertEquals(phrases[0], e.current.currentSentence)
        assertEquals(3, e.current.total)
        assertEquals(1, e.current.humanIndex)
        assertFalse(e.current.hasPrevious)
        assertTrue(e.current.hasNext)
    }

    @Test
    fun le_mode_manuel_ne_defile_pas_tout_seul() {
        val e = engine(ScrollMode.MANUAL)
        e.play()
        repeat(50) { e.tick(1000) }
        assertEquals(0, e.current.index)
        e.next()
        assertEquals(1, e.current.index)
    }

    @Test
    fun le_mode_auto_change_de_phrase_apres_la_duree() {
        val e = PromptEngine(phrases, mode = ScrollMode.AUTO, secondsPerSentence = 2f)
        e.play()
        e.tick(1000)
        assertEquals(0, e.current.index)
        assertTrue(e.current.progress > 0.4f)
        e.tick(1100)
        assertEquals(1, e.current.index)
    }

    @Test
    fun le_mode_intelligent_calcule_la_duree_selon_les_mots() {
        val e = engine(ScrollMode.SMART, wpm = 60) // 1 mot par seconde
        // « Beaucoup de personnes veulent réussir. » = 5 mots → ~5 s à 60 mots/min
        assertEquals(5000L, e.targetDurationMs())
        e.play()
        e.tick(3000)
        assertEquals(0, e.current.index)
        e.tick(2100)
        assertEquals(1, e.current.index)
    }

    @Test
    fun precedent_et_suivant_fonctionnent_pendant_la_lecture() {
        val e = engine()
        e.play()
        e.next()
        assertEquals(1, e.current.index)
        assertTrue(e.current.isPlaying)
        e.previous()
        assertEquals(0, e.current.index)
    }

    @Test
    fun la_derniere_phrase_marque_la_fin_sans_depasser_l_index() {
        val e = PromptEngine(phrases, mode = ScrollMode.AUTO, secondsPerSentence = 1f)
        e.play()
        repeat(10) { e.tick(600) }
        assertEquals(phrases.lastIndex, e.current.index)
        assertTrue(e.current.finished)
        assertFalse(e.current.isPlaying)
    }

    @Test
    fun recommencer_revient_au_debut() {
        val e = engine()
        e.next(); e.next()
        e.restart(autoPlay = false)
        assertEquals(0, e.current.index)
        assertFalse(e.current.isPlaying)
        e.restart(autoPlay = true)
        assertTrue(e.current.isPlaying)
    }

    @Test
    fun progressif_sur_les_mots_pour_le_surlignage() {
        val e = PromptEngine(phrases, mode = ScrollMode.AUTO, secondsPerSentence = 4f)
        e.play()
        e.tick(2000)
        val words = phrases[0].split(" ").size
        assertEquals(words / 2, e.current.spokenWordCount)
    }

    @Test
    fun affichage_de_plusieurs_phrases_est_borne() {
        val e = PromptEngine(phrases, sentencesAtOnce = 9)
        assertEquals(3, e.current.sentencesAtOnce)
        e.setSentencesAtOnce(2)
        assertEquals(2, e.current.visibleSentences.size)
    }

    @Test
    fun progression_globale_coherente() {
        val e = engine()
        assertEquals(0f, e.current.overallProgress, 0.001f)
        e.next()
        assertEquals(1f / 3f, e.current.overallProgress, 0.001f)
        e.next()
        assertEquals(2f / 3f, e.current.overallProgress, 0.001f)
        e.next() // termine la dernière phrase
        assertTrue(e.current.finished)
        assertEquals(1f, e.current.overallProgress, 0.001f)
    }
}
