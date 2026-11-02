package com.scriptcam.app

import android.app.Application
import android.content.Context
import com.scriptcam.app.data.LocalStore
import com.scriptcam.app.data.SettingsRepository
import com.scriptcam.app.data.VideoExporter

/**
 * Point d'entrée applicatif. Conteneur de dépendances minimal (pas de framework d'injection) :
 * l'application reste petite, rapide à démarrer et facile à maintenir.
 */
class ScriptCamApp : Application() {

    val localStore: LocalStore by lazy { LocalStore(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val videoExporter: VideoExporter by lazy { VideoExporter(this) }
}

/** Accès pratique au conteneur depuis les ViewModels et l'UI. */
val Context.appContainer: ScriptCamApp
    get() = applicationContext as ScriptCamApp
