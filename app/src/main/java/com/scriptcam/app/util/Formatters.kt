package com.scriptcam.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Formatage mm:ss (ou h:mm:ss au-delà d'une heure) pour le chronomètre. */
fun formatDuration(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(safe)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.FRANCE, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.FRANCE, "%02d:%02d", minutes, seconds)
    }
}

/** Durée lisible : « 42 s », « 3 min 12 s ». */
fun formatDurationHuman(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "$minutes min $seconds s" else "$seconds s"
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 -> "$bytes o"
    bytes < 1024 * 1024 -> String.format(Locale.FRANCE, "%.0f Ko", bytes / 1024f)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.FRANCE, "%.1f Mo", bytes / (1024f * 1024f))
    else -> String.format(Locale.FRANCE, "%.2f Go", bytes / (1024f * 1024f * 1024f))
}

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.FRANCE).format(Date(timestamp))

fun formatShortDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(timestamp))
