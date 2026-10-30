package com.example.tvlauncher.setup

import java.util.Locale

object SetupOptions {

    class Option(val code: String, val label: String)

    private val LANGUAGE_TAGS = listOf(
        "pt-BR", "pt-PT", "en-US", "es-MX", "es-ES", "fr-FR", "de-DE", "it-IT", "ja-JP", "ko-KR",
    )
    private val REGION_CODES = listOf(
        "BR", "PT", "US", "CA", "MX", "AR", "CL", "CO", "ES", "GB", "FR", "DE", "IT", "JP",
    )

    /** Cada idioma no seu próprio nome, para ser reconhecível em qualquer idioma do aparelho. */
    val languages: List<Option> by lazy {
        LANGUAGE_TAGS.map { tag ->
            val locale = Locale.forLanguageTag(tag)
            Option(tag, locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) })
        }
    }

    fun regions(): List<Option> {
        val ui = Locale.getDefault()
        return REGION_CODES.map { code ->
            Option(code, Locale.Builder().setRegion(code).build().getDisplayCountry(ui))
        }
    }

    fun defaultLanguage(): String {
        val device = Locale.getDefault()
        return LANGUAGE_TAGS.firstOrNull { it.equals(device.toLanguageTag(), ignoreCase = true) }
            ?: LANGUAGE_TAGS.firstOrNull { it.startsWith(device.language + "-", ignoreCase = true) }
            ?: "pt-BR"
    }

    fun defaultRegion(): String = Locale.getDefault().country.takeIf { it in REGION_CODES } ?: "BR"
}
