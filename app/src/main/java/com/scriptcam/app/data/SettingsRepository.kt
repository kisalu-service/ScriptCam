package com.scriptcam.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scriptcam.app.domain.ScrollMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Orientation préférée du studio de tournage. */
enum class StudioOrientation { AUTO, PORTRAIT, LANDSCAPE }

/**
 * Tous les réglages du téléprompteur et de l'application.
 * Valeurs par défaut pensées pour un tournage TikTok vertical (9:16).
 */
data class PromptSettings(
    val fontSizeSp: Int = 30,
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundArgb: Int = 0xFF000000.toInt(),
    val backgroundAlpha: Float = 0.55f,
    val verticalPosition: Float = 0.34f, // 0 = haut, 1 = bas
    val lineHeight: Float = 1.25f,
    val scrollMode: ScrollMode = ScrollMode.SMART,
    val wordsPerMinute: Int = 150,
    val sentencesAtOnce: Int = 1,
    val mirrorText: Boolean = false,
    val countdownSeconds: Int = 3,
    val keepScreenOn: Boolean = true,
    val orientation: StudioOrientation = StudioOrientation.AUTO,
    val useFrontCameraByDefault: Boolean = true,
    val saveToGallery: Boolean = true,
    /** Tourne en 720p (capteur plus sensible) : recommandé en faible éclairage. */
    val lowLightMode: Boolean = false,
    /** Compensation d'exposition appliquée au démarrage (en crans EV). */
    val exposureSteps: Int = 0
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scriptcam_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val fontSize = intPreferencesKey("font_size")
        val textColor = intPreferencesKey("text_color")
        val bgColor = intPreferencesKey("bg_color")
        val bgAlpha = floatPreferencesKey("bg_alpha")
        val verticalPosition = floatPreferencesKey("vertical_position")
        val lineHeight = floatPreferencesKey("line_height")
        val scrollMode = stringPreferencesKey("scroll_mode")
        val wpm = intPreferencesKey("wpm")
        val sentencesAtOnce = intPreferencesKey("sentences_at_once")
        val mirror = booleanPreferencesKey("mirror")
        val countdown = intPreferencesKey("countdown")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val orientation = stringPreferencesKey("orientation")
        val frontCamera = booleanPreferencesKey("front_camera")
        val saveToGallery = booleanPreferencesKey("save_to_gallery")
    }

    val settings: Flow<PromptSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { p ->
            val defaults = PromptSettings()
            PromptSettings(
                fontSizeSp = p[Keys.fontSize] ?: defaults.fontSizeSp,
                textColorArgb = p[Keys.textColor] ?: defaults.textColorArgb,
                backgroundArgb = p[Keys.bgColor] ?: defaults.backgroundArgb,
                backgroundAlpha = p[Keys.bgAlpha] ?: defaults.backgroundAlpha,
                verticalPosition = p[Keys.verticalPosition] ?: defaults.verticalPosition,
                lineHeight = p[Keys.lineHeight] ?: defaults.lineHeight,
                scrollMode = ScrollMode.fromName(p[Keys.scrollMode]),
                wordsPerMinute = p[Keys.wpm] ?: defaults.wordsPerMinute,
                sentencesAtOnce = p[Keys.sentencesAtOnce] ?: defaults.sentencesAtOnce,
                mirrorText = p[Keys.mirror] ?: defaults.mirrorText,
                countdownSeconds = p[Keys.countdown] ?: defaults.countdownSeconds,
                keepScreenOn = p[Keys.keepScreenOn] ?: defaults.keepScreenOn,
                orientation = p[Keys.orientation]?.let { name ->
                    StudioOrientation.entries.firstOrNull { it.name == name }
                } ?: defaults.orientation,
                useFrontCameraByDefault = p[Keys.frontCamera] ?: defaults.useFrontCameraByDefault,
                saveToGallery = p[Keys.saveToGallery] ?: defaults.saveToGallery,
                lowLightMode = p[Keys.lowLight] ?: defaults.lowLightMode,
                exposureSteps = p[Keys.exposure] ?: defaults.exposureSteps
            )
        }

    suspend fun update(transform: (PromptSettings) -> PromptSettings) {
        context.dataStore.edit { prefs ->
            val current = PromptSettings(
                fontSizeSp = prefs[Keys.fontSize] ?: 30,
                textColorArgb = prefs[Keys.textColor] ?: 0xFFFFFFFF.toInt(),
                backgroundArgb = prefs[Keys.bgColor] ?: 0xFF000000.toInt(),
                backgroundAlpha = prefs[Keys.bgAlpha] ?: 0.55f,
                verticalPosition = prefs[Keys.verticalPosition] ?: 0.34f,
                lineHeight = prefs[Keys.lineHeight] ?: 1.25f,
                scrollMode = ScrollMode.fromName(prefs[Keys.scrollMode]),
                wordsPerMinute = prefs[Keys.wpm] ?: 150,
                sentencesAtOnce = prefs[Keys.sentencesAtOnce] ?: 1,
                mirrorText = prefs[Keys.mirror] ?: false,
                countdownSeconds = prefs[Keys.countdown] ?: 3,
                keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
                orientation = prefs[Keys.orientation]?.let { n -> StudioOrientation.entries.firstOrNull { it.name == n } }
                    ?: StudioOrientation.AUTO,
                useFrontCameraByDefault = prefs[Keys.frontCamera] ?: true,
                saveToGallery = prefs[Keys.saveToGallery] ?: true,
                lowLightMode = prefs[Keys.lowLight] ?: false,
                exposureSteps = prefs[Keys.exposure] ?: 0
            )
            val next = transform(current)
            prefs[Keys.fontSize] = next.fontSizeSp
            prefs[Keys.textColor] = next.textColorArgb
            prefs[Keys.bgColor] = next.backgroundArgb
            prefs[Keys.bgAlpha] = next.backgroundAlpha
            prefs[Keys.verticalPosition] = next.verticalPosition
            prefs[Keys.lineHeight] = next.lineHeight
            prefs[Keys.scrollMode] = next.scrollMode.name
            prefs[Keys.wpm] = next.wordsPerMinute
            prefs[Keys.sentencesAtOnce] = next.sentencesAtOnce
            prefs[Keys.mirror] = next.mirrorText
            prefs[Keys.countdown] = next.countdownSeconds
            prefs[Keys.keepScreenOn] = next.keepScreenOn
            prefs[Keys.orientation] = next.orientation.name
            prefs[Keys.frontCamera] = next.useFrontCameraByDefault
            prefs[Keys.saveToGallery] = next.saveToGallery
            prefs[Keys.lowLight] = next.lowLightMode
            prefs[Keys.exposure] = next.exposureSteps
        }
    }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }
}
