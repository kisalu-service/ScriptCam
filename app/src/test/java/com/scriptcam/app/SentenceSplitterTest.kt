package com.scriptcam.app

import com.scriptcam.app.domain.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun decoupe_l_exemple_du_cahier_des_charges() {
        val text = "Beaucoup de personnes veulent réussir. Mais peu acceptent le temps que cela demande. " +
            "La vraie question est de savoir si nous sommes prêts à faire les sacrifices nécessaires."
        val sentences = SentenceSplitter.split(text)
        assertEquals(3, sentences.size)
        assertEquals("Beaucoup de personnes veulent réussir.", sentences[0])
        assertEquals("Mais peu acceptent le temps que cela demande.", sentences[1])
        assertEquals(
            "La vraie question est de savoir si nous sommes prêts à faire les sacrifices nécessaires.",
            sentences[2]
        )
    }

    @Test
    fun gere_les_points_d_interrogation_et_d_exclamation() {
        val sentences = SentenceSplitter.split("Tu veux réussir ? Alors commence maintenant ! C'est simple.")
        // Les espaces avant ponctuation sont normalisés pour un affichage propre sur l'écran.
        assertEquals(listOf("Tu veux réussir?", "Alors commence maintenant!", "C'est simple."), sentences)
    }

    @Test
    fun gere_les_points_de_suspension() {
        val sentences = SentenceSplitter.split("Il hésite... Puis il se lance. Bravo !")
        assertEquals(3, sentences.size)
        assertTrue(sentences.first().endsWith(".."))
    }

    @Test
    fun ne_coupe_pas_apres_une_abreviation() {
        val sentences = SentenceSplitter.split("M. Diallo est arrivé. Il était en avance.")
        assertEquals(2, sentences.size)
        assertEquals("M. Diallo est arrivé.", sentences[0])
    }

    @Test
    fun respecte_les_paragraphes() {
        val text = "Premier paragraphe sans ponctuation\n\nDeuxième paragraphe sans ponctuation"
        val sentences = SentenceSplitter.split(text)
        assertEquals(2, sentences.size)
    }

    @Test
    fun renvoie_une_liste_vide_pour_un_texte_vide() {
        assertTrue(SentenceSplitter.split("   ").isEmpty())
        assertTrue(SentenceSplitter.split("").isEmpty())
    }

    @Test
    fun normalise_les_espaces_avant_ponctuation() {
        val sentences = SentenceSplitter.split("Vraiment ? Oui !")
        // Les deux fragments sont très courts : ils sont regroupés pour rester lisibles.
        assertEquals(listOf("Vraiment? Oui!"), sentences)
    }

    @Test
    fun ne_coupe_pas_apres_une_minuscule() {
        // ex. « 3.5 millions » ou une phrase qui continue en minuscule
        val sentences = SentenceSplitter.split("Il a 3.5 millions d'abonnés. C'est énorme.")
        assertEquals(2, sentences.size)
    }
}
