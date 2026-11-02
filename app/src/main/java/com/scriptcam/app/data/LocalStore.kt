package com.scriptcam.app.data

import android.content.Context
import com.scriptcam.app.domain.Recording
import com.scriptcam.app.domain.ScriptText
import com.scriptcam.app.util.toListOfObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Stockage local des textes et de l'index des vidéos.
 *
 * Choix technique : fichiers JSON dans le stockage privé de l'application
 * (`filesDir/texts.json` et `filesDir/recordings.json`).
 * - Aucun serveur, aucune dépendance lourde (pas de Room/KSP : compilation plus rapide
 *   et APK plus léger, ce qui compte sur les téléphones de gamme modeste).
 * - Écriture atomique via fichier temporaire pour éviter toute corruption.
 * - Les données restent disponibles hors ligne.
 */
class LocalStore(context: Context) {

    private val textsFile = File(context.filesDir, "texts.json")
    private val recordingsFile = File(context.filesDir, "recordings.json")
    private val mutex = Mutex()

    private val _texts = MutableStateFlow<List<ScriptText>>(emptyList())
    val texts: StateFlow<List<ScriptText>> = _texts.asStateFlow()

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    private var loaded = false

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withLock
            _texts.value = readTexts().sortedByDescending { it.updatedAt }
            _recordings.value = readRecordings().sortedByDescending { it.createdAt }
            loaded = true
        }
    }

    // ---------------------------------------------------------------- Textes

    suspend fun getText(id: String): ScriptText? {
        ensureLoaded()
        return _texts.value.firstOrNull { it.id == id }
    }

    suspend fun saveText(text: ScriptText): ScriptText = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedInternal()
            val now = System.currentTimeMillis()
            val existing = _texts.value.firstOrNull { it.id == text.id }
            val toSave = if (existing == null) {
                text.copy(createdAt = now, updatedAt = now)
            } else {
                text.copy(createdAt = existing.createdAt, updatedAt = now)
            }
            val list = _texts.value.toMutableList()
            val index = list.indexOfFirst { it.id == toSave.id }
            if (index >= 0) list[index] = toSave else list.add(0, toSave)
            val sorted = list.sortedByDescending { it.updatedAt }
            writeTexts(sorted)
            _texts.value = sorted
            toSave
        }
    }

    suspend fun createText(title: String, content: String): ScriptText =
        saveText(ScriptText(id = UUID.randomUUID().toString(), title = title, content = content))

    suspend fun deleteText(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedInternal()
            val list = _texts.value.filterNot { it.id == id }
            writeTexts(list)
            _texts.value = list
        }
        Unit
    }

    suspend fun duplicateText(id: String): ScriptText? {
        val source = getText(id) ?: return null
        return createText("${source.title} (copie)", source.content)
    }

    // ------------------------------------------------------------- Vidéos

    suspend fun addRecording(recording: Recording) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedInternal()
            val list = (_recordings.value + recording).sortedByDescending { it.createdAt }
            writeRecordings(list)
            _recordings.value = list
        }
        Unit
    }

    suspend fun deleteRecording(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedInternal()
            val list = _recordings.value.filterNot { it.id == id }
            writeRecordings(list)
            _recordings.value = list
        }
        Unit
    }

    suspend fun markSavedToGallery(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedInternal()
            val list = _recordings.value.map { if (it.id == id) it.copy(savedToGallery = true) else it }
            writeRecordings(list)
            _recordings.value = list
        }
        Unit
    }

    // ------------------------------------------------------------ Interne

    /** Version non suspendue utilisée lorsqu'on tient déjà le mutex. */
    private fun ensureLoadedInternal() {
        if (loaded) return
        _texts.value = readTexts().sortedByDescending { it.updatedAt }
        _recordings.value = readRecordings().sortedByDescending { it.createdAt }
        loaded = true
    }

    private fun readTexts(): List<ScriptText> = runCatching {
        if (!textsFile.exists()) return emptyList()
        val array = JSONArray(textsFile.readText())
        array.toListOfObjects().mapNotNull { o ->
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ScriptText(
                id = id,
                title = o.optString("title", "Sans titre"),
                content = o.optString("content", ""),
                createdAt = o.optLong("createdAt", 0L),
                updatedAt = o.optLong("updatedAt", 0L)
            )
        }
    }.getOrDefault(emptyList())

    private fun writeTexts(list: List<ScriptText>) {
        val array = JSONArray()
        list.forEach { t ->
            array.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("content", t.content)
                    .put("createdAt", t.createdAt)
                    .put("updatedAt", t.updatedAt)
            )
        }
        writeAtomic(textsFile, array.toString())
    }

    private fun readRecordings(): List<Recording> = runCatching {
        if (!recordingsFile.exists()) return emptyList()
        val array = JSONArray(recordingsFile.readText())
        array.toListOfObjects().mapNotNull { o ->
            val uri = o.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Recording(
                id = o.optString("id", uri.hashCode().toString()),
                uri = uri,
                scriptTitle = if (o.has("scriptTitle") && !o.isNull("scriptTitle")) o.optString("scriptTitle") else null,
                durationMs = o.optLong("durationMs", 0L),
                createdAt = o.optLong("createdAt", 0L),
                sizeBytes = o.optLong("sizeBytes", 0L),
                savedToGallery = o.optBoolean("savedToGallery", false),
                mimeType = o.optString("mimeType", "video/mp4")
            )
        }
    }.getOrDefault(emptyList())

    private fun writeRecordings(list: List<Recording>) {
        val array = JSONArray()
        list.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("uri", r.uri)
                    .put("scriptTitle", r.scriptTitle ?: JSONObject.NULL)
                    .put("durationMs", r.durationMs)
                    .put("createdAt", r.createdAt)
                    .put("sizeBytes", r.sizeBytes)
                    .put("savedToGallery", r.savedToGallery)
                    .put("mimeType", r.mimeType)
            )
        }
        writeAtomic(recordingsFile, array.toString())
    }

    private fun writeAtomic(target: File, content: String) {
        runCatching {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(content)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }
}
