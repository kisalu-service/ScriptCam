package com.scriptcam.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.scriptcam.app.data.LocalStore
import com.scriptcam.app.data.PromptSettings
import com.scriptcam.app.data.SettingsRepository
import com.scriptcam.app.data.StudioOrientation
import com.scriptcam.app.data.VideoExporter
import com.scriptcam.app.domain.Recording
import com.scriptcam.app.domain.ScriptText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel racine partagé par tous les écrans.
 *
 * Il porte : les textes, l'index des vidéos, les réglages du téléprompteur et la
 * « session de tournage » (texte + réglages + caméra préférée) qui sert de pont entre
 * l'écran de préparation, le studio et l'aperçu.
 */
class AppViewModel(
    private val store: LocalStore,
    private val settingsRepo: SettingsRepository,
    val exporter: VideoExporter
) : ViewModel() {

    val texts: StateFlow<List<ScriptText>> = store.texts
    val recordings: StateFlow<List<Recording>> = store.recordings

    val settings: StateFlow<PromptSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, PromptSettings())

    // ---------------------------------------------------- Session de tournage

    private val _session = MutableStateFlow(ShootSession())
    val session: StateFlow<ShootSession> = _session.asStateFlow()

    // ------------------------------------------- Dernier résultat de tournage

    private val _lastVideo = MutableStateFlow<Recording?>(null)
    val lastVideo: StateFlow<Recording?> = _lastVideo.asStateFlow()

    var pendingSharedText by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { store.ensureLoaded() }
    }

    // ------------------------------------------------------------- Textes

    suspend fun getText(id: String): ScriptText? = store.getText(id)

    fun saveText(id: String?, title: String, content: String, onSaved: (ScriptText) -> Unit = {}) {
        viewModelScope.launch {
            val saved = if (id == null) {
                store.createText(title, content)
            } else {
                val existing = store.getText(id)
                store.saveText(
                    ScriptText(
                        id = id,
                        title = title,
                        content = content,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            onSaved(saved)
        }
    }

    fun deleteText(id: String) {
        viewModelScope.launch { store.deleteText(id) }
    }

    fun duplicateText(id: String) {
        viewModelScope.launch { store.duplicateText(id) }
    }

    /** Reçoit un texte partagé depuis une autre application (Intent ACTION_SEND). */
    fun onSharedTextReceived(text: String) {
        pendingSharedText = text
    }

    fun clearSharedText() {
        pendingSharedText = ""
    }

    // -------------------------------------------------------- Réglages

    fun updateSettings(transform: (PromptSettings) -> PromptSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun resetSettings() {
        viewModelScope.launch { settingsRepo.reset() }
    }

    // -------------------------------------------------- Session de tournage

    fun prepareSession(text: ScriptText) {
        _session.value = _session.value.copy(
            scriptId = text.id,
            scriptTitle = text.title,
            scriptContent = text.content
        )
    }

    fun updateSessionTransform(transform: (ShootSession) -> ShootSession) {
        _session.value = transform(_session.value)
    }

    fun registerRecording(recording: Recording) {
        _lastVideo.value = recording
        viewModelScope.launch { store.addRecording(recording) }
    }

    fun clearLastVideo() {
        _lastVideo.value = null
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            exporter.delete(android.net.Uri.parse(recording.uri))
            store.deleteRecording(recording.id)
            if (_lastVideo.value?.id == recording.id) _lastVideo.value = null
        }
    }

    fun markSavedToGallery(recordingId: String) {
        viewModelScope.launch { store.markSavedToGallery(recordingId) }
    }

    companion object {
        fun factory(store: LocalStore, settings: SettingsRepository, exporter: VideoExporter) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    AppViewModel(store, settings, exporter) as T
            }
    }
}

/** Ce que l'utilisateur a choisi avant de filmer. */
data class ShootSession(
    val scriptId: String? = null,
    val scriptTitle: String = "",
    val scriptContent: String = "",
    val useFrontCamera: Boolean = true,
    val orientation: StudioOrientation = StudioOrientation.AUTO
) {
    val hasScript: Boolean get() = scriptContent.isNotBlank()
}
