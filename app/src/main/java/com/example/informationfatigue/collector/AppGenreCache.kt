package com.example.informationfatigue.collector

import android.content.Context

/**
 * Persistent local cache for app package -> genreId lookup.
 * null means the package is a non-target app (e.g., Play Store 404).
 */
class AppGenreCache(context: Context) {

    companion object {
        private const val PREFS_NAME = "app_genre_cache"
        private const val NOT_TARGET_VALUE = "__NOT_TARGET__"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun has(packageName: String): Boolean = prefs.contains(packageName)

    fun get(packageName: String): String? {
        val raw = prefs.getString(packageName, null) ?: return null
        return if (raw == NOT_TARGET_VALUE) null else raw
    }

    fun put(packageName: String, genreId: String) {
        prefs.edit().putString(packageName, genreId).apply()
    }

    fun putNotTarget(packageName: String) {
        prefs.edit().putString(packageName, NOT_TARGET_VALUE).apply()
    }
}
