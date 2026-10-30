package com.example.tvlauncher.data

import android.content.Context
import com.example.tvlauncher.setup.SetupOptions

class SetupPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Falso até concluir; volta a ser falso se os termos forem atualizados (TERMS_VERSION). */
    val isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_DONE, false) && prefs.getInt(KEY_TERMS_VERSION, 0) >= TERMS_VERSION

    /** Tag BCP-47 aceita pelo TMDB, ex.: "pt-BR". */
    val language: String get() = prefs.getString(KEY_LANGUAGE, null) ?: SetupOptions.defaultLanguage()

    /** ISO 3166-1, usado em watch_region, ex.: "BR". */
    val region: String get() = prefs.getString(KEY_REGION, null) ?: SetupOptions.defaultRegion()

    /** Opt-in do usuário nos termos. Desligado = nenhuma chamada ao TMDB. */
    val recommendationsEnabled: Boolean get() = prefs.getBoolean(KEY_RECS, true)

    /** Sem UI: override opcional da chave embutida no build (uso em desenvolvimento). */
    val tmdbApiKey: String get() = prefs.getString(KEY_API_KEY, "").orEmpty()

    fun saveChoices(language: String, region: String, recommendations: Boolean) {
        prefs.edit()
            .putString(KEY_LANGUAGE, language)
            .putString(KEY_REGION, region)
            .putBoolean(KEY_RECS, recommendations)
            .putInt(KEY_TERMS_VERSION, TERMS_VERSION)
            .commit()
    }

    /** commit(): sobrevive a um desligamento logo após o assistente. */
    fun markComplete() {
        prefs.edit().putBoolean(KEY_DONE, true).commit()
    }

    companion object {
        /** Aumente ao alterar termos ou privacidade para pedir novo aceite. */
        const val TERMS_VERSION = 1

        private const val NAME = "launcher_setup"
        private const val KEY_DONE = "setup_complete"
        private const val KEY_TERMS_VERSION = "terms_version"
        private const val KEY_LANGUAGE = "tmdb_language"
        private const val KEY_REGION = "tmdb_region"
        private const val KEY_RECS = "recommendations_enabled"
        private const val KEY_API_KEY = "tmdb_api_key"
    }
}
