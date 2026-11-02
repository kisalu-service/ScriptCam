package com.scriptcam.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestion des fichiers vidéo : suppression, export vers la galerie, partage.
 *
 * Depuis Android 10 (API 29), l'enregistrement passe par MediaStore : le fichier est
 * déjà visible dans la galerie et aucune permission de stockage n'est nécessaire.
 * L'URI renvoyée par MediaStore est directement partageable avec un grant de lecture.
 */
class VideoExporter(private val context: Context) {

    /**
     * Prépare l'URI à envoyer dans le menu de partage Android
     * (TikTok, Instagram, WhatsApp, Google Photos…).
     */
    fun buildShareIntent(uri: Uri, mimeType: String = "video/mp4", title: String? = null): Intent {
        val shareUri = ensureReadableUri(uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!title.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_TEXT, title)
            }
        }
        return Intent.createChooser(intent, "Partager la vidéo").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Certains lecteurs ne peuvent pas lire une URI MediaStore distante ; dans ce cas on
     * copie le fichier dans le cache privé et on l'expose via FileProvider.
     */
    private fun ensureReadableUri(uri: Uri): Uri = when (uri.scheme) {
        "file" -> {
            // Un URI file:// n'est pas partageable entre applications : on passe par
            // FileProvider (chemin exposé dans res/xml/file_paths.xml).
            val file = uri.path?.let(::File)
            if (file != null && file.exists()) {
                runCatching { fileProviderUri(file) }.getOrElse { uri }
            } else {
                uri
            }
        }
        else -> uri
    }

    /** Ouvre la vidéo dans l'application de galerie du téléphone. */
    fun buildViewIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Supprime la vidéo (MediaStore ou fichier privé). */
    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            when (uri.scheme) {
                "content" -> context.contentResolver.delete(uri, null, null) > 0
                "file" -> uri.path?.let { File(it).delete() } ?: false
                else -> false
            }
        }.getOrElse { false }
    }

    /**
     * Copie une vidéo du cache vers la galerie publique (utile si l'enregistrement
     * a été fait hors MediaStore, ou pour un export manuel).
     * @return l'URI publique ou null en cas d'échec.
     */
    suspend fun exportToGallery(source: Uri, displayName: String): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScriptCam")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val target = context.contentResolver.insert(collection, values) ?: return@runCatching null
            context.contentResolver.openInputStream(source)?.use { input ->
                context.contentResolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(target, values, null, null)
            target
        }.getOrNull()
    }

    /** Vérifie qu'une URI MediaStore existe encore (l'utilisateur a pu supprimer le fichier). */
    suspend fun exists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).exists() } == true
                else -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }
        }.getOrElse { false }
    }

    /** Construit une URI partageable via FileProvider pour un fichier privé. */
    fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Identifiant MediaStore à partir d'une URI (utile pour les métadonnées). */
    fun mediaStoreId(uri: Uri): Long? = runCatching { ContentUris.parseId(uri) }.getOrNull()
}
