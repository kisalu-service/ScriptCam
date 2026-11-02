package com.scriptcam.app.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ExperimentalMirrorMode
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
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
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.CameraCaptureSession
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

    /** Instance CameraX liée : indispensable pour piloter le zoom et la torche. */
    private var camera: Camera? = null

    /** Capture d'image fixe — sert au « flash photo » en basse lumière. */
    private var imageCapture: ImageCapture? = null

    /** Zoom courant (1.0 = objectif natif). */
    var zoomRatio: Float = 1f
        private set

    /** Torche allumée (caméra arrière) ou lampe d'appoint (caméra frontale). */
    var torchEnabled: Boolean = false
        private set

    /**
     * Mode basse lumière : privilégie le 720p au 1080p.
     * Un capteur qui sort moins de pixels expose plus longtemps et « binne » ses photosites :
     * l'image est nettement plus lumineuse en faible éclairage, au prix de la résolution.
     */
    var lowLightMode: Boolean = false
        private set

    /** Compensation d'exposition en cours (en « crans » EV), 0 = exposition automatique neutre. */
    var exposureSteps: Int = 0
        private set

    /**
     * Temps d'exposition imposé au capteur, en nanosecondes (null = exposition automatique).
     * C'est LE levier pour la caméra selfie : contrairement à la compensation d'exposition,
     * il autorise une pose longue (1/15 s, 1/8 s…) que le mode vidéo n'accorde pas seul.
     */
    var manualExposureNs: Long? = null
        private set

    /** Dernière exposition réellement appliquée par le capteur (lue dans les métadonnées). */
    @Volatile
    var liveExposureNs: Long = 0L
        private set

    /** Dernière sensibilité réellement appliquée (ISO). */
    @Volatile
    var liveIso: Int = 0
        private set

    /** Callback système qui remonte l'exposition effective image par image. */
    private val exposureProbe = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: android.hardware.camera2.TotalCaptureResult
        ) {
            result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { liveExposureNs = it }
            result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { liveIso = it }
        }
    }

    /** Mémorise les paramètres de liaison pour pouvoir relier à nouveau (changement de FPS). */
    private data class BindRequest(
        val previewView: PreviewView,
        val preferFront: Boolean,
        val mirrorPreview: Boolean,
        val onEvent: (CameraEvent) -> Unit
    )
    private var lastBind: BindRequest? = null

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
        lastBind = BindRequest(previewView, preferFront, mirrorPreview, onEvent)
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

    @OptIn(ExperimentalMirrorMode::class)
    private fun bindInternal(
        previewView: PreviewView,
        mirrorPreview: Boolean,
        onEvent: (CameraEvent) -> Unit
    ) {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            // FPS verrouillé : en 30 i/s constant, l'obturateur ne peut pas descendre assez
            // bas pour la nuit. Un verrou à 30/30 (ou 24/24) laisse le capteur allonger
            // son temps d'exposition quand la scène est sombre.
            val fpsRange = pickFpsRange()
            val previewBuilder = Preview.Builder()
                .apply { if (fpsRange != null) setTargetFrameRate(fpsRange) }
            applyExposureControls(previewBuilder)
            preview = previewBuilder.build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            // En basse lumière on descend en 720p : moins de pixels = exposition plus longue
            // = image sensiblement plus lumineuse. Sinon FHD avec repli automatique.
            val qualitySelector = if (lowLightMode) {
                QualitySelector.from(Quality.HD, FallbackStrategy.higherQualityOrLowerThan(Quality.HD))
            } else {
                QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            // IMPORTANT : l'aperçu de la caméra frontale est en miroir (comme un miroir réel),
            // mais CameraX n'enregistre PAS le miroir par défaut → la vidéo finale était
            // inversée par rapport à ce que l'utilisateur voyait pendant le tournage.
            // MIRROR_MODE_ON_FRONT_ONLY aligne le fichier sur l'aperçu (recommandation Google).
            val videoBuilder = VideoCapture.Builder(recorder)
                .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                .apply { if (fpsRange != null) setTargetFrameRate(fpsRange) }
            applyExposureControls(videoBuilder)
            videoCapture = videoBuilder.build()

            // PreviewView applique déjà le miroir de la caméra frontale : on ne le retourne
            // plus manuellement, sinon l'aperçu et la vidéo repartaient en sens opposés.
            previewView.scaleX = 1f

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                UseCaseGroup.Builder()
                    .addUseCase(preview!!)
                    .addUseCase(videoCapture!!)
                    .addUseCase(imageCapture!!)
                    .build()
            )
            applyExposureLock(exposureSteps)
            // Le zoom et la torche sont réinitialisés à chaque (re)liaison.
            camera?.let { cam ->
                if (cam.cameraInfo.hasFlashUnit()) {
                    cam.cameraControl.enableTorch(false)
                }
                torchEnabled = false
                cam.cameraControl.setZoomRatio(zoomRatio.coerceIn(minZoom(), maxZoom()))
            }
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
        // Le zoom d'un objectif n'a pas de sens sur l'autre : on repart de 1x.
        zoomRatio = 1f
        torchEnabled = false
        bindInternal(previewView, mirrorPreview, onEvent)
        return target
    }

    // ------------------------------------------------- Exposition (luminosité)

    /** Vrai si l'appareil accepte une compensation d'exposition. */
    fun isExposureCompensationSupported(): Boolean = runCatching {
        camera?.cameraInfo?.exposureState?.isExposureCompensationSupported == true
    }.getOrDefault(false)

    /** Plage de compensation disponible (en crans), par exemple -6..+6. */
    fun exposureIndexRange(): IntRange = runCatching {
        val state = camera?.cameraInfo?.exposureState ?: return@runCatching 0..0
        val range = state.exposureCompensationRange
        (range.lower)..(range.upper)
    }.getOrDefault(0..0)

    /**
     * Règle la luminosité de l'image en direct (et donc de la vidéo enregistrée).
     *
     * ⚠️ À utiliser en dernier recours : +2 EV éclaircit, mais allonge le temps d'exposition
     * ou pousse le gain → risque de flou de mouvement et de bruit. Le mode basse lumière
     * (720p) est souvent un meilleur premier réflexe.
     */
    fun setExposureCompensation(steps: Int): Int? {
        val cam = camera ?: return null
        val clamped = steps.coerceIn(exposureIndexRange())
        return runCatching {
            cam.cameraControl.setExposureCompensationIndex(clamped)
            exposureSteps = clamped
            clamped
        }.getOrNull()
    }

    // --------------------------------- Exposition manuelle du capteur (selfie)

    /**
     * Applique (ou retire) le contrôle d'exposition manuel sur les cas d'usage CameraX.
     *
     * Pourquoi c'est nécessaire : en mode vidéo, l'exposition automatique de CameraX choisit un
     * temps de pose court (≈ 1/30 s) pour tenir les 30 images/seconde. L'application photo du
     * téléphone, elle, utilise le mode photo et peut poser 1/8 s. C'est exactement l'écart de
     * luminosité constaté. On désactive donc l'AE (`CONTROL_AE_MODE_OFF`) et on impose
     * `SENSOR_EXPOSURE_TIME` quand l'utilisateur choisit une pose longue.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun <B : androidx.camera.core.ExtendableBuilder<B>> applyExposureControls(builder: B) {
        runCatching {
            val extender = Camera2Interop.Extender(builder)
            val exposure = manualExposureNs
            if (exposure != null) {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                extender.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)
                // Le capteur doit pouvoir « prendre son temps » : on relâche aussi la cadence.
                extender.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, exposure)
            } else {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
            }
            // Remontée de l'exposition réelle (affichée dans le studio).
            extender.setSessionCaptureCallback(exposureProbe)
        }.onFailure { Log.w(TAG, "Contrôle d'exposition indisponible : ${it.message}") }
    }

    /** Plage d'exposition supportée par le capteur courant, en nanosecondes. */
    @OptIn(ExperimentalCamera2Interop::class)
    fun sensorExposureRangeNs(): LongRange? = runCatching {
        val provider = cameraProvider ?: return@runCatching null
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val info = provider.getCameraInfo(selector) ?: return@runCatching null
        val range = Camera2CameraInfo.from(info)
            .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            ?: return@runCatching null
        range.lower..range.upper
    }.getOrNull()

    /**
     * Fixe une pose manuelle. [ns] = null → retour à l'exposition automatique.
     * La liaison caméra est refaite, donc à appeler hors enregistrement.
     * @return la valeur appliquée (null si retour en auto ou si refusé).
     */
    fun setManualExposureNs(ns: Long?, onEvent: (CameraEvent) -> Unit = {}): Long? {
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            onEvent(CameraEvent.Info("Impossible de changer l'exposition pendant l'enregistrement."))
            return manualExposureNs
        }
        val allowed = sensorExposureRangeNs()
        val target = when {
            ns == null -> null
            allowed == null -> ns
            else -> ns.coerceIn(allowed)
        }
        manualExposureNs = target
        lastBind?.let { bindInternal(it.previewView, it.mirrorPreview, it.onEvent) }
        return target
    }
    fun maxExposureCompensation(): Int? = setExposureCompensation(exposureIndexRange().last)

    private fun applyExposureLock(steps: Int) {
        runCatching {
            val cam = camera ?: return@runCatching
            if (cam.cameraInfo.exposureState.isExposureCompensationSupported) {
                cam.cameraControl.setExposureCompensationIndex(steps.coerceIn(exposureIndexRange()))
            }
        }
    }

    // ------------------------------------------------------- Mode basse lumière

    /** Valeur courante du mode basse lumière. */
    fun isLowLightMode(): Boolean = lowLightMode

    /**
     * Active/désactive le mode basse lumière. La liaison caméra est refaite pour appliquer
     * immédiatement le changement de résolution (impossible pendant un enregistrement).
     * @return true si l'état a été appliqué.
     */
    fun setLowLightMode(enabled: Boolean, onEvent: (CameraEvent) -> Unit = {}): Boolean {
        if (lowLightMode == enabled) return true
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            onEvent(CameraEvent.Info("Impossible de changer la résolution pendant l'enregistrement."))
            return false
        }
        lowLightMode = enabled
        val request = lastBind
        if (request != null) {
            bindInternal(request.previewView, request.mirrorPreview, request.onEvent)
        }
        return true
    }

    /**
     * Plage d'images demandée au capteur.
     *
     * Un capteur selfie est petit : en basse lumière il a besoin d'un temps d'exposition long.
     * Une plage FIXE à 30/30 l'empêche de descendre sous ~1/30 s. On demande donc, en basse
     * lumière sur la caméra frontale, une plage 15-30 : l'appareil peut alors exposer jusqu'à
     * 1/15 s (2× plus de lumière) au prix d'une légère saccade — acceptable pour un visage
     * qui parle face caméra.
     */
    private fun pickFpsRange(): android.util.Range<Int>? = runCatching {
        val info = camera?.cameraInfo ?: return@runCatching null
        val ranges = info.supportedFrameRateRanges
        val frontSelfieLowLight = lowLightMode &&
            lensFacing == CameraSelector.LENS_FACING_FRONT
        if (frontSelfieLowLight) {
            // On cherche la plage qui autorise le plus long temps d'exposition.
            ranges.minByOrNull { it.lower }?.let { return@runCatching it }
        }
        // Sinon : 30/30 (fluide, sans double exposition), puis 24/24, puis le plus proche.
        val fixed = ranges.filter { it.lower == it.upper }
        fixed.firstOrNull { it.upper == 30 }
            ?: fixed.firstOrNull { it.upper == 24 }
            ?: fixed.minByOrNull { it.upper }
    }.getOrNull()

    // ------------------------------------------ Diagnostic caméra frontale

    /**
     * Caractéristiques du capteur selfie, utiles quand l'image reste sombre :
     * temps d'exposition maximal, sensibilité (ISO) maximale et plage d'exposition.
     * Sur beaucoup de téléphones d'entrée/milieu de gamme, la caméra frontale plafonne à
     * 1/15 s et à un petit capteur : aucune application ne peut alors « inventer » de la
     * lumière, seuls un éclairage ou une exposition maximale aident.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun frontCameraDiagnostics(): String {
        val provider = cameraProvider ?: return "Caméra non initialisée"
        return runCatching {
            val info = provider.getCameraInfo(
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
            ) ?: return "Pas de caméra frontale"
            val chars = Camera2CameraInfo.from(info)
            val maxExposureNs = chars.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
            )?.upper
            val maxIso = chars.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.upper
            val evRange = info.exposureState.exposureCompensationRange
            buildString {
                append("Selfie : ")
                append(if (maxExposureNs != null) "expo max ${maxExposureNs / 1_000_000} ms" else "expo max ?")
                append(" · ")
                append(if (maxIso != null) "ISO max $maxIso" else "ISO max ?")
                append(" · ")
                append("expo ±${evRange.upper} crans")
            }.also { Log.i(TAG, it) }
        }.getOrElse { "Diagnostic indisponible" }
    }

    // ------------------------------------------------ Flash photo (basse lumière)

    /**
     * Capture une image fixe pendant l'enregistrement.
     *
     * Pourquoi : en très faible éclairage, une image fixe peut exposer bien plus longtemps
     * qu'une image de vidéo (30 i/s). On peut donc « prendre une photo lumineuse » puis
     * repartir de cette image pour la suite du montage.
     *
     * Limite assumée : la photo et la vidéo sont deux fichiers distincts. Fusionner la photo
     * dans la piste vidéo exigerait un transcodage complet (Media3 Transformer), qui n'est
     * pas inclus ici. Cette fonction sert donc de secours : elle produit une image nette et
     * lumineuse au lieu d'une vidéo sombre.
     *
     * @return le chemin du fichier image, ou null en cas d'échec.
     */
    fun capturePhoto(onResult: (String?, String?) -> Unit) {
        val capture = imageCapture ?: run {
            onResult(null, "Capture photo indisponible.")
            return
        }
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            "ScriptCam"
        ).apply { if (!exists()) mkdirs() }
        val file = File(directory, "ScriptCam_" + System.currentTimeMillis() + ".jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runCatching {
                        android.media.MediaScannerConnection.scanFile(
                            context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null
                        )
                    }
                    onResult(file.absolutePath, null)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Échec de la capture photo", exception)
                    onResult(null, "La photo n'a pas pu être prise.")
                }
            }
        )
    }

    // ------------------------------------------------------------- Zoom

    /** Zoom minimal proposé par l'objectif (souvent 1.0, parfois 0.5 en ultra grand-angle). */
    fun minZoom(): Float = runCatching { camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f }
        .getOrDefault(1f)

    /** Zoom maximal proposé par l'objectif. */
    fun maxZoom(): Float = runCatching { camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f }
        .getOrDefault(1f)

    /** Zoom courant exposé par CameraX (source de vérité après un pincement). */
    fun currentZoom(): Float = runCatching { camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: zoomRatio }
        .getOrDefault(zoomRatio)

    /**
     * Applique un facteur de zoom, borné aux capacités de l'objectif.
     * @return le zoom réellement appliqué (null si la caméra n'est pas prête).
     */
    fun setZoomRatio(ratio: Float): Float? {
        val cam = camera ?: return null
        val target = ratio.coerceIn(minZoom(), maxZoom())
        return runCatching {
            cam.cameraControl.setZoomRatio(target)
            zoomRatio = target
            target
        }.getOrNull()
    }

    /** Multiplie le zoom courant par [factor] (ex. 0.2 pour un pincement vers l'intérieur). */
    fun zoomBy(factor: Float): Float? = setZoomRatio(currentZoom() * factor)

    /** Revient à l'objectif natif (1x). */
    fun resetZoom(): Float? = setZoomRatio(1f)

    // ------------------------------------------------------------ Torche

    /** Vrai si la caméra active dispose d'une unité flash utilisable comme torche. */
    fun hasTorch(): Boolean = runCatching { camera?.cameraInfo?.hasFlashUnit() == true }
        .getOrDefault(false)

    /**
     * Allume ou éteint la lumière.
     *
     * - Caméra arrière : torche matérielle (continu, visible dans l'enregistrement).
     * - Caméra frontale : la plupart des téléphones n'ont pas de flash frontal. On bascule
     *   alors sur une « lampe d'appoint » : l'aperçu est éclairé en blanc pour déboucher le
     *   visage. Cette lumière d'écran n'apparaît pas dans le fichier vidéo — c'est une
     *   limite matérielle, pas un bug.
     *
     * @return l'état effectif de la lampe, ou null si aucune solution d'éclairage n'existe.
     */
    fun toggleTorch(): Boolean? {
        val cam = camera ?: return null
        val wanted = !torchEnabled
        if (cam.cameraInfo.hasFlashUnit()) {
            return runCatching {
                cam.cameraControl.enableTorch(wanted)
                torchEnabled = wanted
                wanted
            }.getOrNull()
        }
        // Pas de flash unit : la lampe d'appoint est gérée côté interface.
        torchEnabled = wanted
        return wanted
    }

    fun setTorch(enabled: Boolean): Boolean? {
        val cam = camera ?: return null
        return runCatching {
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(enabled)
            }
            torchEnabled = enabled
            enabled
        }.getOrNull()
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
        runCatching { if (camera?.cameraInfo?.hasFlashUnit() == true) camera?.cameraControl?.enableTorch(false) }
        torchEnabled = false
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
