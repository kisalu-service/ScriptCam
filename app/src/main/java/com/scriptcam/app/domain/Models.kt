package com.scriptcam.app.domain

/** Mode de défilement du téléprompteur. */
enum class ScrollMode {
    /** Les phrases défilent automatiquement à un rythme choisi. */
    AUTO,

    /** La phrase change uniquement sur appui utilisateur. */
    MANUAL,

    /** Défilement progressif + contrôle manuel immédiat (mode recommandé). */
    SMART;

    companion object {
        fun fromName(name: String?): ScrollMode =
            entries.firstOrNull { it.name == name } ?: SMART
    }
}

/** Un texte sauvegardé par l'utilisateur. */
data class ScriptText(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val wordCount: Int
        get() = content.split(Regex("\\s+")).count { it.isNotBlank() }

    /** Estimation de durée de lecture (145 mots/minute par défaut). */
    fun estimatedSeconds(wordsPerMinute: Int = 145): Int =
        if (wordsPerMinute <= 0) 0 else ((wordCount.toFloat() / wordsPerMinute) * 60f).toInt()
}

/** Une vidéo enregistrée, indexée localement. */
data class Recording(
    val id: String,
    val uri: String,
    val scriptTitle: String?,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0L,
    val savedToGallery: Boolean = false,
    val mimeType: String = "video/mp4"
)
