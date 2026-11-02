package com.scriptcam.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.scriptcam.app.camera.CameraController
import com.scriptcam.app.camera.CameraEvent
import com.scriptcam.app.data.StudioOrientation
import com.scriptcam.app.domain.Recording
import com.scriptcam.app.domain.ScrollMode
import com.scriptcam.app.domain.SentenceSplitter
import com.scriptcam.app.prompt.PromptEngine
import com.scriptcam.app.prompt.PromptState
import com.scriptcam.app.ui.AppViewModel
import com.scriptcam.app.ui.components.PrompterOverlay
import com.scriptcam.app.ui.components.RoundIconButton
import com.scriptcam.app.ui.components.SettingSlider
import com.scriptcam.app.ui.theme.Accent
import com.scriptcam.app.ui.theme.Ink700
import com.scriptcam.app.ui.theme.Ink800
import com.scriptcam.app.ui.theme.RecRed
import com.scriptcam.app.ui.theme.TextPrimary
import com.scriptcam.app.ui.theme.TextSecondary
import com.scriptcam.app.ui.theme.Warning
import com.scriptcam.app.util.formatDuration
import kotlinx.coroutines.delay
import java.util.UUID

/** Phase d'enregistrement côté interface. */
private enum class RecPhase { IDLE, COUNTDOWN, RECORDING, PAUSED, FINISHING }

/**
 * Studio vidéo : aperçu caméra plein écran + téléprompteur phrase par phrase.
 *
 * Architecture : le studio possède [PromptEngine] (logique pure testée) et
 * [CameraController] (CameraX). Les événements caméra sont traités par un gestionnaire
 * unique (`handleCameraEvent`) afin d'éviter toute perte de l'événement de finalisation.
 */
@Composable
fun StudioScreen(
    viewModel: AppViewModel,
    onExit: () -> Unit,
    onFinished: (Recording) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val settings by viewModel.settings.collectAsState()
    val session by viewModel.session.collectAsState()

    // ---------------------------------------------------------------- Prompt
    val sentences = remember(session.scriptContent) { SentenceSplitter.split(session.scriptContent) }
    val engine = remember {
        PromptEngine(
            sentences = sentences,
            mode = settings.scrollMode,
            wordsPerMinute = settings.wordsPerMinute,
            sentencesAtOnce = settings.sentencesAtOnce
        )
    }
    val promptState: PromptState by engine.state.collectAsState()

    LaunchedEffect(settings.scrollMode, settings.wordsPerMinute, settings.sentencesAtOnce) {
        engine.setMode(settings.scrollMode)
        engine.setWordsPerMinute(settings.wordsPerMinute)
        engine.setSentencesAtOnce(settings.sentencesAtOnce)
    }
    LaunchedEffect(sentences) { engine.setSentences(sentences) }

    // Horloge du téléprompteur : une itération par image affichée.
    LaunchedEffect(promptState.isPlaying) {
        var previous = withFrameMillis { it }
        while (engine.current.isPlaying) {
            val now = withFrameMillis { it }
            engine.tick((now - previous).coerceIn(0L, 120L))
            previous = now
        }
    }

    // ----------------------------------------------------------- Permissions
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudio by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCamera = result[Manifest.permission.CAMERA] ?: hasCamera
        hasAudio = result[Manifest.permission.RECORD_AUDIO] ?: hasAudio
    }
    LaunchedEffect(Unit) {
        if (!hasCamera || !hasAudio) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    // ---------------------------------------------------------------- Caméra
    val cameraController = remember { CameraController(context, lifecycleOwner) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(RecPhase.IDLE) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var countdown by remember { mutableStateOf(0) }
    var showTuning by remember { mutableStateOf(false) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }

    // Zoom / torche
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(1f) }
    var torchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var lowLight by remember { mutableStateOf(settings.lowLightMode) }
    var exposureSteps by remember { mutableStateOf(settings.exposureSteps) }
    var exposureRange by remember { mutableStateOf(0..0) }
    /** Intensité de la lampe d'appoint frontale (0 = éteinte). */
    var fillLightLevel by remember { mutableStateOf(0) }
    var frontDiagnostics by remember { mutableStateOf<String?>(null) }

    // Mode basse lumière : mémorisé pour la liaison, et appliqué même s'il change à chaud.
    LaunchedEffect(settings.lowLightMode) {
        lowLight = settings.lowLightMode
        cameraController.setLowLightMode(settings.lowLightMode) { evt ->
            if (evt is CameraEvent.Info) infoMessage = evt.message
        }
    }

    DisposableEffect(hasCamera) {
        if (hasCamera) {
            // La préférence a déjà été poussée dans le contrôleur par le LaunchedEffect
            // ci-dessus : la liaison utilise donc la bonne résolution dès le départ.
            cameraController.bind(
                previewView = previewView,
                preferFront = session.useFrontCamera,
                mirrorPreview = true
            ) { event ->
                if (event is CameraEvent.Failure) cameraError = event.message
                if (event is CameraEvent.Info) infoMessage = event.message
            }
        }
        onDispose { cameraController.unbind() }
    }

    // Capacités de l'objectif courant (zoom et flash).
    // CameraX termine la liaison de façon asynchrone : on interroge les capacités
    // jusqu'à obtenir une réponse exploitable (utile sur les appareils lents).
    LaunchedEffect(hasCamera) {
        if (!hasCamera) return@LaunchedEffect
        var attempts = 0
        while (attempts < 12) {
            delay(300)
            minZoom = cameraController.minZoom()
            maxZoom = cameraController.maxZoom()
            zoomRatio = cameraController.currentZoom()
            hasFlash = cameraController.hasTorch()
            torchOn = cameraController.torchEnabled
            lowLight = cameraController.isLowLightMode()
            exposureRange = cameraController.exposureIndexRange()
            attempts++
            if (maxZoom > minZoom + 0.01f || hasFlash || exposureRange.last > 0) break
        }
    }

    // Applique la compensation d'exposition mémorisée dès que la caméra est prête.
    LaunchedEffect(exposureRange, hasCamera) {
        if (hasCamera && exposureRange.last > exposureRange.first) {
            cameraController.setExposureCompensation(exposureSteps)?.let { exposureSteps = it }
        }
    }

    /** Gestionnaire unique de tous les événements d'enregistrement. */
    fun handleCameraEvent(event: CameraEvent) {
        when (event) {
            is CameraEvent.Started -> {
                phase = RecPhase.RECORDING
                elapsedMs = 0L
                engine.restart(autoPlay = true)
            }
            is CameraEvent.Finalized -> {
                phase = RecPhase.IDLE
                val uri = event.video.uri
                if (uri == Uri.EMPTY) {
                    cameraError = "La vidéo n'a pas pu être enregistrée (fichier vide)."
                } else {
                    val recording = Recording(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                        scriptTitle = session.scriptTitle.ifBlank { null },
                        durationMs = event.video.durationMs,
                        sizeBytes = event.video.sizeBytes,
                        savedToGallery = event.video.savedInGallery
                    )
                    savedUri = uri
                    viewModel.registerRecording(recording)
                    onFinished(recording)
                }
            }
            is CameraEvent.Failure -> {
                phase = RecPhase.IDLE
                cameraError = event.message
            }
            is CameraEvent.Info -> infoMessage = event.message
        }
    }

    fun beginRecording() {
        cameraError = null
        cameraController.startRecording(session.scriptTitle.ifBlank { null }, ::handleCameraEvent)
        phase = RecPhase.RECORDING
        elapsedMs = 0L
    }

    /** Photo lumineuse de secours (utile quand la vidéo sort trop sombre). */
    fun takeBrightPhoto() {
        cameraController.capturePhoto { path, error ->
            if (path != null) {
                infoMessage = "Photo lumineuse enregistrée (galerie : Pictures/ScriptCam)."
            } else {
                infoMessage = error ?: "Photo impossible."
            }
        }
    }

    fun requestRecording() {
        engine.restart(autoPlay = false)
        if (settings.countdownSeconds > 0) {
            phase = RecPhase.COUNTDOWN
            countdown = settings.countdownSeconds
        } else {
            beginRecording()
        }
    }

    // Compte à rebours : 3, 2, 1 puis démarrage de l'enregistrement.
    LaunchedEffect(phase) {
        if (phase != RecPhase.COUNTDOWN) return@LaunchedEffect
        for (remaining in settings.countdownSeconds downTo 0) {
            countdown = remaining
            if (remaining > 0) delay(1000)
        }
        beginRecording()
    }

    // Chronomètre (progression du temps réel, gelé pendant la pause)
    LaunchedEffect(phase) {
        if (phase != RecPhase.RECORDING) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (phase == RecPhase.RECORDING) {
            delay(100)
            val now = System.currentTimeMillis()
            elapsedMs += (now - last)
            last = now
        }
    }

    // Écran allumé
    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Plein écran immersif
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Orientation demandée par l'utilisateur
    DisposableEffect(session.orientation) {
        val activity = context as? Activity
        activity?.requestedOrientation = when (session.orientation) {
            StudioOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            StudioOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            StudioOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(Unit) { fillLightLevel = 35 }

    LaunchedEffect(infoMessage) {
        if (infoMessage != null) {
            delay(3200)
            infoMessage = null
        }
    }

    // -------------------------------------------------------------------- UI
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (hasCamera) {
            // Pincement à deux doigts = zoom optique/numérique en direct.
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(hasCamera) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (maxZoom > minZoom) {
                                val target = (cameraController.currentZoom() * zoomChange)
                                    .coerceIn(minZoom, maxZoom)
                                cameraController.setZoomRatio(target)?.let { zoomRatio = it }
                            }
                        }
                    }
            )
        } else {
            PermissionPlaceholder {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        }

        // Lampe d'appoint : la caméra frontale n'a presque jamais de flash, on éclaire donc
        // le visage avec un voile blanc (non enregistré dans la vidéo).
        if (torchOn && !hasFlash && hasCamera) {
            // Dosage : 35 % suffit à éclairer un visage proche sans cramer l'image.
            // Le niveau est réglable dans les réglages rapides.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = fillLightLevel / 100f))
            )
        }

        // Dégradés pour la lisibilité du texte et des commandes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.5f),
                        0.22f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )

        if (hasCamera && !showTuning) {
            PrompterOverlay(state = promptState, settings = settings, modifier = Modifier.fillMaxSize())
        }

        // ------------------------------------------------------ Barre du haut
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(
                onClick = {
                    if (phase == RecPhase.RECORDING || phase == RecPhase.PAUSED) {
                        cameraController.stopRecording()
                    }
                    cameraController.unbind()
                    onExit()
                },
                size = 44,
                background = Color.Black.copy(alpha = 0.5f)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = TextPrimary)
            }

            Spacer(Modifier.weight(1f))

            if (phase != RecPhase.IDLE) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (phase == RecPhase.PAUSED) TextSecondary else RecRed)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatDuration(elapsedMs), style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    if (phase == RecPhase.PAUSED) {
                        Spacer(Modifier.width(8.dp))
                        Text("PAUSE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            RoundIconButton(
                onClick = {
                    val enabled = !lowLight
                    if (cameraController.setLowLightMode(enabled) { evt ->
                            if (evt is CameraEvent.Info) infoMessage = evt.message
                        }) {
                        lowLight = enabled
                        viewModel.updateSettings { it.copy(lowLightMode = enabled) }
                        infoMessage = if (enabled) {
                            "Mode basse lumière : 720p, image plus lumineuse."
                        } else {
                            "Mode qualité : retour au 1080p."
                        }
                    }
                },
                size = 44,
                background = if (lowLight) Accent.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LightMode,
                    contentDescription = "Mode basse lumière",
                    tint = if (lowLight) Color.Black else TextPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(
                onClick = {
                    val newState = cameraController.toggleTorch()
                    when (newState) {
                        null -> infoMessage = "Lumière indisponible sur cette caméra."
                        else -> {
                            torchOn = newState
                            if (!hasFlash) {
                                infoMessage = if (newState) {
                                    "Lampe d'appoint activée (la caméra frontale n'a pas de flash)."
                                } else {
                                    "Lampe d'appoint désactivée."
                                }
                            } else {
                                infoMessage = if (newState) "Torche allumée." else "Torche éteinte."
                            }
                        }
                    }
                },
                size = 44,
                background = if (torchOn) Accent.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f)
            ) {
                Icon(
                    imageVector = if (torchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = "Lampe",
                    tint = if (torchOn) Color.Black else TextPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(
                onClick = {
                    if (phase == RecPhase.RECORDING || phase == RecPhase.PAUSED) {
                        infoMessage = "Arrête l'enregistrement pour changer de caméra."
                    } else {
                        torchOn = false
                        cameraController.switchCamera(previewView, mirrorPreview = true) { event ->
                            if (event is CameraEvent.Failure) cameraError = event.message
                        }
                        zoomRatio = 1f
                        hasFlash = cameraController.hasTorch()
                        minZoom = cameraController.minZoom()
                        maxZoom = cameraController.maxZoom()
                    }
                },
                size = 44,
                background = Color.Black.copy(alpha = 0.5f)
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Changer de caméra", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(
                onClick = { showTuning = !showTuning },
                size = 44,
                background = Color.Black.copy(alpha = 0.5f)
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Réglages rapides", tint = TextPrimary)
            }
        }

        // ------------------------------------------------- Réglages rapides
        AnimatedVisibility(
            visible = showTuning,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink800.copy(alpha = 0.97f))
                    .padding(16.dp)
            ) {
                Text("Réglages rapides", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                SettingSlider(
                    label = "Position du texte",
                    value = settings.verticalPosition,
                    valueRange = 0f..0.9f,
                    valueLabel = "${(settings.verticalPosition * 100).toInt()} %",
                    onValueChange = { v -> viewModel.updateSettings { it.copy(verticalPosition = v) } }
                )
                SettingSlider(
                    label = "Taille du texte",
                    value = settings.fontSizeSp.toFloat(),
                    valueRange = 16f..56f,
                    valueLabel = "${settings.fontSizeSp} pt",
                    onValueChange = { v -> viewModel.updateSettings { it.copy(fontSizeSp = v.toInt()) } }
                )
                SettingSlider(
                    label = "Transparence du fond",
                    value = settings.backgroundAlpha,
                    valueRange = 0f..1f,
                    valueLabel = "${(settings.backgroundAlpha * 100).toInt()} %",
                    onValueChange = { v -> viewModel.updateSettings { it.copy(backgroundAlpha = v) } }
                )
                if (!hasFlash) {
                    SettingSlider(
                        label = "Lampe d'appoint (frontale)",
                        value = fillLightLevel.toFloat(),
                        valueRange = 0f..100f,
                        valueLabel = "$fillLightLevel %",
                        onValueChange = { v ->
                            fillLightLevel = v.toInt()
                            if (fillLightLevel > 0 && !torchOn) {
                                torchOn = true
                                cameraController.setTorch(true)
                            }
                        }
                    )
                }
                if (exposureRange.last > exposureRange.first) {
                    SettingSlider(
                        label = "Luminosité de l'image",
                        value = exposureSteps.toFloat(),
                        valueRange = exposureRange.first.toFloat()..exposureRange.last.toFloat(),
                        valueLabel = if (exposureSteps == 0) "Auto" else
                            String.format(java.util.Locale.FRANCE, "%+d EV", exposureSteps),
                        onValueChange = { v ->
                            cameraController.setExposureCompensation(v.toInt())?.let { applied ->
                                exposureSteps = applied
                                viewModel.updateSettings { it.copy(exposureSteps = applied) }
                            }
                        }
                    )
                }
                SettingSlider(
                    label = "Rythme de lecture",
                    value = settings.wordsPerMinute.toFloat(),
                    valueRange = 80f..260f,
                    valueLabel = "${settings.wordsPerMinute} mots/min",
                    onValueChange = { v -> viewModel.updateSettings { it.copy(wordsPerMinute = v.toInt()) } }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Selfie sombre : active le mode basse lumière (soleil), monte « Luminosité de l'image » de +1 à +2, puis la lampe d'appoint si besoin.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent
                )
                frontDiagnostics?.let { diag ->
                    Spacer(Modifier.height(4.dp))
                    Text(diag, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ink800)
                        .clickable { frontDiagnostics = cameraController.frontCameraDiagnostics() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Diagnostic caméra selfie",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Faible luminosité : préfère le mode basse lumière (720p) à une luminosité maximale, qui introduit du grain.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pince à deux doigts sur l'image pour zoomer · icône réglages pour refermer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        // ---------------------------------------------------- Compte à rebours
        if (phase == RecPhase.COUNTDOWN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = countdown.coerceAtLeast(1).toString(),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp),
                        color = Accent
                    )
                    Text("Prépare-toi…", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
            }
        }

        // ---------------------------------------------------------- Messages
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 58.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            infoMessage?.let {
                ToastBubble(text = it, color = Accent)
                Spacer(Modifier.height(6.dp))
            }
            cameraError?.let {
                ToastBubble(text = it, color = RecRed)
                Spacer(Modifier.height(6.dp))
            }
            if (!hasAudio) {
                ToastBubble(text = "Micro non autorisé : la vidéo sera muette.", color = Warning)
            }
        }

        // ---------------------------------------------------- Commandes du bas
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom : glissière discrète (1x → zoom max de l'objectif) + retour rapide à 1x.
            if (hasCamera && maxZoom > minZoom + 0.01f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = zoomRatio.coerceIn(minZoom, maxZoom),
                        onValueChange = { v -> cameraController.setZoomRatio(v)?.let { zoomRatio = it } },
                        valueRange = minZoom..maxZoom,
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = String.format(java.util.Locale.FRANCE, "%.1f×", zoomRatio),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (zoomRatio > 1.02f) Accent else TextSecondary,
                        modifier = Modifier.clickable {
                            cameraController.resetZoom()?.let { zoomRatio = it }
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = if (promptState.total == 0) "" else
                    "Phrase ${promptState.humanIndex} sur ${promptState.total}",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(promptState.overallProgress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Accent)
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(onClick = { engine.previous() }, enabled = promptState.hasPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Phrase précédente", tint = TextPrimary)
                }
                RoundIconButton(
                    onClick = { engine.togglePlayPause() },
                    size = 60,
                    background = Color.Black.copy(alpha = 0.55f)
                ) {
                    Icon(
                        imageVector = if (promptState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (promptState.isPlaying) "Pause" else "Lecture",
                        tint = Accent,
                        modifier = Modifier.size(30.dp)
                    )
                }
                RoundIconButton(onClick = { engine.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Phrase suivante", tint = TextPrimary)
                }
                RoundIconButton(onClick = { engine.restart(autoPlay = true) }) {
                    Icon(Icons.Default.Replay, contentDescription = "Recommencer", tint = TextPrimary)
                }
                RoundIconButton(onClick = { takeBrightPhoto() }) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = "Prendre une photo lumineuse",
                        tint = TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when (phase) {
                RecPhase.FINISHING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Finalisation…", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }

                RecPhase.RECORDING, RecPhase.PAUSED -> Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIconButton(
                        onClick = {
                            if (phase == RecPhase.PAUSED) {
                                cameraController.resumeRecording { }
                                phase = RecPhase.RECORDING
                            } else {
                                cameraController.pauseRecording { }
                                phase = RecPhase.PAUSED
                            }
                        },
                        size = 54,
                        background = Ink700.copy(alpha = 0.92f)
                    ) {
                        Icon(
                            imageVector = if (phase == RecPhase.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (phase == RecPhase.PAUSED) "Reprendre" else "Pause",
                            tint = TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(RecRed)
                            .clickable {
                                phase = RecPhase.FINISHING
                                engine.pause()
                                cameraController.stopRecording()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Arrêter et enregistrer",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                else -> Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(RecRed)
                            .clickable { requestRecording() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = "Enregistrer",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = when (phase) {
                    RecPhase.COUNTDOWN -> "Enregistrement imminent…"
                    RecPhase.RECORDING -> "Enregistrement en cours — lis ton texte"
                    RecPhase.PAUSED -> "Enregistrement en pause"
                    RecPhase.FINISHING -> "Sauvegarde de la vidéo…"
                    RecPhase.IDLE -> when (settings.scrollMode) {
                        ScrollMode.SMART -> "Mode intelligent · ${settings.wordsPerMinute} mots/min"
                        ScrollMode.AUTO -> "Mode automatique"
                        ScrollMode.MANUAL -> "Mode manuel · ▸▸ pour avancer"
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PermissionPlaceholder(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "ScriptCam a besoin de la caméra et du micro pour filmer.",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Accent)
                .clickable(onClick = onRequest)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Autoriser l'accès", style = MaterialTheme.typography.labelLarge, color = Color.Black)
        }
    }
}

@Composable
private fun ToastBubble(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ink800.copy(alpha = 0.95f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
