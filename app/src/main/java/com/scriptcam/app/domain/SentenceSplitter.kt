package com.scriptcam.app.domain

import java.text.Normalizer

/**
 * Découpage d'un texte en phrases affichables par le téléprompteur.
 *
 * Règles :
 *  - Coupe après `.`, `!`, `?`, `…`, `;` (et leurs répétitions `!?`, `...`).
 *  - Ne coupe pas après une abréviation courante (M., Mme., Dr., etc.) ni après un chiffre isolé.
 *  - Conserve la ponctuation finale dans la phrase.
 *  - Regroupe les fragments trop courts (< [minChars]) avec la phrase suivante pour éviter
 *    des écrans d'un seul mot difficilement lisibles.
 *  - Respecte les paragraphes (retour à la ligne) : un paragraphe sans ponctuation devient
 *    une phrase.
 */
object SentenceSplitter {

    private val ABBREVIATIONS = setOf(
        "m", "mm", "mme", "mlle", "mmes", "dr", "pr", "me", "st", "ste", "etc", "cf",
        "ex", "av", "bd", "env", "p", "pp", "fig", "no", "art", "ref", "tel", "vol",
        "jr", "sr", "ltd", "inc", "co", "min", "max", "approx", "vs", "nb", "p.ex"
    )

    /** Caractères qui terminent une phrase. */
    private const val TERMINATORS = ".!?…"

    fun split(text: String, minChars: Int = 12): List<String> {
        val cleaned = normalize(text)
        if (cleaned.isBlank()) return emptyList()

        val sentences = mutableListOf<String>()
        for (paragraph in cleaned.split(Regex("\\n\\s*\\n|\\n"))) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue
            splitParagraph(trimmed).forEach { addMergingShort(sentences, it, minChars) }
        }
        return sentences.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Normalise les espaces et la ponctuation « typographique » pour faciliter le découpage. */
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
        .replace("\r\n", "\n")
        .replace('\u00A0', ' ')
        .replace("\u2026", "…")
        .replace(" !", "!").replace(" ?", "?").replace(" ;", ";")
        .replace("…", "...")

    private fun splitParagraph(paragraph: String): List<String> {
        val out = mutableListOf<String>()
        val buffer = StringBuilder()
        var i = 0
        while (i < paragraph.length) {
            val c = paragraph[i]
            buffer.append(c)
            if (c in TERMINATORS || c == ';') {
                // Absorbe les terminateurs répétés : "...", "!!", "?!".
                var j = i + 1
                while (j < paragraph.length && (paragraph[j] in TERMINATORS || paragraph[j] == ';')) {
                    buffer.append(paragraph[j])
                    j++
                }
                i = j
                // Le terminateur doit être suivi d'un blanc puis d'une majuscule (ou fin de paragraphe)
                // pour constituer une vraie fin de phrase.
                val followsWhitespace = i >= paragraph.length || paragraph[i].isWhitespace()
                val looksLikeAbbreviation = c == '.' && isAbbreviation(buffer.toString())
                val nextIsLowercase = nextNonSpace(paragraph, i)?.isLowerCase() == true
                if (followsWhitespace && !looksLikeAbbreviation && !nextIsLowercase) {
                    out += buffer.toString()
                    buffer.clear()
                }
                continue
            }
            i++
        }
        if (buffer.isNotBlank()) out += buffer.toString()
        return out
    }

    private fun nextNonSpace(text: String, from: Int): Char? {
        var k = from
        while (k < text.length && text[k].isWhitespace()) k++
        return if (k < text.length) text[k] else null
    }

    private fun isAbbreviation(chunk: String): Boolean {
        val withoutTerminator = chunk.trimEnd('.', '!', '?', ';', '…', ' ').trim()
        val lastWord = withoutTerminator.substringAfterLast(' ').lowercase()
        if (lastWord.isEmpty()) return false
        // Une lettre isolée suivie d'un point = initiale (ex. « J. K. Rowling »)
        if (lastWord.length == 1 && lastWord[0].isLetter()) return true
        // Un nombre suivi d'un point (ex. « 1. ») n'est pas une abréviation
        if (lastWord.all { it.isDigit() }) return false
        return lastWord in ABBREVIATIONS
    }

    private fun addMergingShort(target: MutableList<String>, sentence: String, minChars: Int) {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return
        val lastIndex = target.lastIndex
        if (lastIndex >= 0 && target[lastIndex].length < minChars) {
            target[lastIndex] = "${target[lastIndex]} $trimmed".replace("  ", " ")
        } else if (minChars > 0 && trimmed.length < minChars && !endsWithTerminator(trimmed)) {
            // Fragment très court sans ponctuation : on le garde (dernière phrase d'un paragraphe)
            target += trimmed
        } else {
            target += trimmed
        }
    }

    private fun endsWithTerminator(s: String): Boolean = s.lastOrNull()?.let { it in TERMINATORS || it == ';' } == true
}
