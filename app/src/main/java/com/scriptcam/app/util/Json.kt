package com.scriptcam.app.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Petit utilitaire JSON basé sur org.json (présent dans le framework Android).
 * Évite d'ajouter une dépendance de sérialisation pour deux modèles simples.
 */
internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

internal fun JSONArray.toListOfObjects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONObject.putIfNotNull(key: String, value: Any?): JSONObject =
    if (value == null) this else put(key, value)
