package com.scriptcam.app.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** État de l'enregistrement exposé à l'interface. */
enum class RecordingState { IDLE, RECORDING, PAUSED, FINISHING, ERROR }

/** Résultat final d'un enregistrement. */
data class RecordedVideo(
    val uri: Uri,
    val durationMs: Long,
    val sizeBytes: Long,
    val savedInGallery: Boolean
)

sealed interface CameraEvent {
    data class Started(val uri: Uri) : CameraEvent
    data class Finalized(val video: RecordedVideo) : CameraEvent
    data class Failure(val message: String, val cause: Throwable? = null) : CameraEvent
    data class Info(val message: String) : CameraEvent
}

/**
 * Encapsule CameraX : aperçu, bascule avant/arrière, enregistrement vidéo + audio,
 * pause/reprise et écriture directe dans la galerie (MediaStore).
 *
 * Choix de qualité : FHD (1080p) demandé, avec repli automatique vers la meilleure
 * qualité disponible si l'appareil ne la propose pas — indispensable sur les téléphones
 * de gamme modeste.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraController"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var activeRecording: Recording? = null

    var recordingState: RecordingState = RecordingState.IDLE
        private set

    var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
        private set

    var hasFrontCamera: Boolean = true
        private set

    /** Indique si le micro peut être utilisé (sinon on enregistre sans audio plutôt que d'échouer). */
    private val hasAudioPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Lie l'aperçu et la capture vidéo à un [PreviewView].
     * @param mirrorPreview miroir de l'aperçu (utile en caméra frontale)
     */
    fun bind(
        previewView: PreviewView,
        preferFront: Boolean,
        mirrorPreview: Boolean,
        onEvent: (CameraEvent) -> Unit
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val frontAvailable = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val backAvailable = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                hasFrontCamera = frontAvailable
                lensFacing = when {
                    preferFront && frontAvailable -> CameraSelector.LENS_FACING_FRONT
                    backAvailable -> CameraSelector.LENS_FACING_BACK
                    frontAvailable -> CameraSelector.LENS_FACING_FRONT
                    else -> CameraSelector.LENS_FACING_BACK
                }
                bindInternal(previewView, mirrorPreview, onEvent)
            } catch (t: Throwable) {
                Log.e(TAG, "Impossible d'initialiser la caméra", t)
                onEvent(CameraEvent.Failure("Impossible d'initialiser la caméra : ${t.message}", t))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindInternal(
        previewView: PreviewView,
        mirrorPreview: Boolean,
        onEvent: (CameraEvent) -> Unit
    ) {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val qualitySelector = QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // PreviewView applique automatiquement le miroir en caméra frontale ;
            // on le désactive si l'utilisateur préfère une image non inversée.
            previewView.scaleX = if (mirrorPreview && lensFacing == CameraSelector.LENS_FACING_FRONT) -1f else 1f

            provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
        } catch (t: Throwable) {
            Log.e(TAG, "Échec de la liaison caméra", t)
            onEvent(CameraEvent.Failure("Caméra indisponible : ${t.message}", t))
        }
    }

    /** Bascule caméra frontale ↔ arrière. Renvoie le nouveau sens, ou null si impossible. */
    fun switchCamera(
        previewView: PreviewView,
        mirrorPreview: Boolean,
        onEvent: (CameraEvent) -> Unit
    ): Int? {
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            onEvent(CameraEvent.Info("Impossible de changer de caméra pendant l'enregistrement."))
            return null
        }
        val provider = cameraProvider ?: return null
        val target = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        val selector = CameraSelector.Builder().requireLensFacing(target).build()
        if (!provider.hasCamera(selector)) {
            onEvent(CameraEvent.Info("Cette caméra n'est pas disponible sur cet appareil."))
            return null
        }
        lensFacing = target
        bindInternal(previewView, mirrorPreview, onEvent)
        return target
    }

    /** Démarre l'enregistrement dans la galerie (MediaStore). */
    fun startRecording(scriptTitle: String?, onEvent: (CameraEvent) -> Unit) {
        val capture = videoCapture
        if (capture == null) {
            onEvent(CameraEvent.Failure("La caméra n'est pas prête."))
            recordingState = RecordingState.ERROR
            return
        }
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            onEvent(CameraEvent.Info("Un enregistrement est déjà en cours."))
            return
        }
        try {
            val name = buildFileName(scriptTitle)
            var pending: androidx.camera.video.PendingRecording = when (val target = buildOutputOptions(name)) {
                is OutputTarget.Store -> capture.output.prepareRecording(context, target.options)
                is OutputTarget.Local -> capture.output.prepareRecording(context, target.options)
            }
            if (hasAudioPermission) {
                pending = pending.withAudioEnabled()
            } else {
                onEvent(CameraEvent.Info("Micro non autorisé : la vidéo sera enregistrée sans son."))
            }

            activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordingState = RecordingState.RECORDING
                        onEvent(CameraEvent.Started(Uri.EMPTY))
                    }
                    is VideoRecordEvent.Status -> Unit
                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        recordingState = RecordingState.IDLE
                        if (event.hasError()) {
                            val message = describeError(event.error)
                            Log.e(TAG, "Finalize error ${event.error}: ${event.cause?.message}")
                            onEvent(CameraEvent.Failure(message, event.cause))
                        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            // Fichier écrit dans le stockage de l'application : on le publie
                            // dans la galerie puis on repartage son URI via FileProvider.
                            val file = pendingFile
                            pendingFile = null
                            if (file != null && file.exists() && file.length() > 0) {
                                publishToGallery(file)
                                val providerUri = runCatching {
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                }.getOrElse { android.net.Uri.fromFile(file) }
                                onEvent(
                                    CameraEvent.Finalized(
                                        RecordedVideo(
                                            uri = providerUri,
                                            durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L,
                                            sizeBytes = event.recordingStats.numBytesRecorded,
                                            savedInGallery = true
                                        )
                                    )
                                )
                            } else {
                                onEvent(CameraEvent.Failure("Aucune donnée vidéo n'a été écrite."))
                            }
                        } else {
                            val uri = event.outputResults.outputUri
                            val stats = event.recordingStats
                            onEvent(
                                CameraEvent.Finalized(
                                    RecordedVideo(
                                        uri = uri,
                                        durationMs = stats.recordedDurationNanos / 1_000_000L,
                                        sizeBytes = stats.numBytesRecorded,
                                        savedInGallery = uri != Uri.EMPTY
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Échec du démarrage de l'enregistrement", t)
            recordingState = RecordingState.ERROR
            onEvent(CameraEvent.Failure("Impossible de démarrer l'enregistrement : ${t.message}", t))
        }
    }

    fun pauseRecording(onEvent: (CameraEvent) -> Unit) {
        val recording = activeRecording ?: return
        try {
            recording.pause()
            recordingState = RecordingState.PAUSED
        } catch (t: Throwable) {
            onEvent(CameraEvent.Failure("Pause impossible : ${t.message}", t))
        }
    }

    fun resumeRecording(onEvent: (CameraEvent) -> Unit) {
        val recording = activeRecording ?: return
        try {
            recording.resume()
            recordingState = RecordingState.RECORDING
        } catch (t: Throwable) {
            onEvent(CameraEvent.Failure("Reprise impossible : ${t.message}", t))
        }
    }

    /** Arrête l'enregistrement : le fichier reste dans la galerie. */
    fun stopRecording() {
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            recordingState = RecordingState.FINISHING
        }
        activeRecording?.stop()
        activeRecording = null
    }

    fun unbind() {
        runCatching { activeRecording?.stop() }
        activeRecording = null
        runCatching { cameraProvider?.unbindAll() }
        recordingState = RecordingState.IDLE
    }

    fun hasAudioPermissionNow(): Boolean = hasAudioPermission

    /**
     * Options de sortie selon la version d'Android :
     *  - API 29+ : écriture directe dans la galerie via MediaStore (aucune permission).
     *  - API 24-28 : enregistrement dans le dossier Movies de l'application puis
     *    publication dans la galerie par MediaScannerConnection.
     */
    private sealed interface OutputTarget {
        data class Store(val options: MediaStoreOutputOptions) : OutputTarget
        data class Local(val options: FileOutputOptions) : OutputTarget
    }

    private fun buildOutputOptions(name: String): OutputTarget {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScriptCam")
                put(android.provider.MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            }
            OutputTarget.Store(
                MediaStoreOutputOptions
                    .Builder(context.contentResolver, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .setContentValues(values)
                    .build()
            )
        } else {
            val directory = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
                "ScriptCam"
            ).apply { if (!exists()) mkdirs() }
            pendingFile = File(directory, name)
            OutputTarget.Local(FileOutputOptions.Builder(pendingFile!!).build())
        }
    }

    /** Fichier en cours d'écriture sur Android 9 et antérieur (null à partir d'Android 10). */
    private var pendingFile: File? = null

    private fun publishToGallery(file: File) {
        runCatching {
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null
            )
        }
    }

    private fun buildFileName(scriptTitle: String?): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val slug = scriptTitle?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "_")
            ?.trim('_')
            ?.take(24)
            ?.ifBlank { "scriptcam" }
            ?: "scriptcam"
        return "ScriptCam_${slug}_$stamp.mp4"
    }

    private fun describeError(code: Int): String = when (code) {
        VideoRecordEvent.Finalize.ERROR_NONE -> "Erreur inconnue."
        VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "Erreur interne de l'enregistreur vidéo."
        VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "Aucune donnée valide n'a été enregistrée."
        VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "Limite de taille de fichier atteinte."
        VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "Options de sortie invalides."
        VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "Encodage vidéo impossible sur cet appareil."
        VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "Stockage insuffisant."
        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "La source caméra s'est interrompue."
        VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> "Durée maximale atteinte."
        VideoRecordEvent.Finalize.ERROR_RECORDING_GARBAGE_COLLECTED -> "Enregistrement interrompu par le système."
        else -> "L'enregistrement a échoué (code $code)."
    }
}
