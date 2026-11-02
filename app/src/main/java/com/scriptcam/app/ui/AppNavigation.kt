package com.scriptcam.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scriptcam.app.domain.Recording
import com.scriptcam.app.ui.screens.EditorScreen
import com.scriptcam.app.ui.screens.HomeScreen
import com.scriptcam.app.ui.screens.LibraryScreen
import com.scriptcam.app.ui.screens.PrepareScreen
import com.scriptcam.app.ui.screens.PreviewScreen
import com.scriptcam.app.ui.screens.SettingsScreen
import com.scriptcam.app.ui.screens.StudioScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor"
    const val PREPARE = "prepare"
    const val STUDIO = "studio"
    const val PREVIEW = "preview"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
}

@Composable
fun ScriptCamNavHost(
    viewModel: AppViewModel,
    navController: NavHostController = rememberNavController()
) {
    val lastVideo by viewModel.lastVideo.collectAsState()
    val session by viewModel.session.collectAsState()
    // L'identifiant de la vidéo à prévisualiser est porté par la route ; le modèle complet
    // est résolu depuis la liste des enregistrements (ou lastVideo juste après un tournage).
    val recordings by viewModel.recordings.collectAsState()
    var previewRecording by remember { mutableStateOf<Recording?>(null) }

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNewText = { navController.navigate(Routes.EDITOR) },
                onOpenText = { text ->
                    viewModel.prepareSession(text)
                    navController.navigate(Routes.PREPARE)
                },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenLastVideo = {
                    previewRecording = lastVideo ?: recordings.firstOrNull()
                    if (previewRecording != null) navController.navigate(Routes.PREVIEW)
                }
            )
        }

        composable(
            route = "${Routes.EDITOR}?id={id}&text={text}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; defaultValue = ""; nullable = true },
                navArgument("text") { type = NavType.StringType; defaultValue = ""; nullable = true }
            )
        ) { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() }
            val initial = entry.arguments?.getString("text")?.takeIf { it.isNotBlank() }
            EditorScreen(
                viewModel = viewModel,
                textId = id,
                initialText = initial,
                onBack = { navController.popBackStack() },
                onStartShooting = { saved ->
                    viewModel.prepareSession(saved)
                    navController.navigate(Routes.PREPARE) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        composable(Routes.PREPARE) {
            PrepareScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onStartStudio = { navController.navigate(Routes.STUDIO) },
                onEditText = {
                    val id = session.scriptId
                    if (id != null) navController.navigate("${Routes.EDITOR}?id=$id") else navController.navigate(Routes.EDITOR)
                }
            )
        }

        composable(Routes.STUDIO) {
            StudioScreen(
                viewModel = viewModel,
                onExit = { navController.popBackStack() },
                onFinished = { recording ->
                    previewRecording = recording
                    navController.navigate(Routes.PREVIEW) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        composable(Routes.PREVIEW) {
            PreviewScreen(
                viewModel = viewModel,
                recording = previewRecording ?: lastVideo,
                onBack = {
                    previewRecording = null
                    navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onRetake = {
                    previewRecording = null
                    navController.navigate(Routes.STUDIO)
                },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) }
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenText = { text ->
                    viewModel.prepareSession(text)
                    navController.navigate(Routes.PREPARE)
                },
                onEditText = { text -> navController.navigate("${Routes.EDITOR}?id=${text.id}") },
                onOpenRecording = { rec ->
                    previewRecording = rec
                    navController.navigate(Routes.PREVIEW)
                },
                onNewText = { navController.navigate(Routes.EDITOR) },
                viewRecording = { }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }

    // Texte reçu via « Partager vers ScriptCam » depuis TikTok / une autre application.
    LaunchedEffect(viewModel.pendingSharedText) {
        val shared = viewModel.pendingSharedText?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        viewModel.clearSharedText()
        navController.navigate("${Routes.EDITOR}?text=${android.net.Uri.encode(shared)}")
    }
}
