package io.github.fanfeast.anonpdf.i18n

import java.util.Locale

/** The app's text, in the language the device (or, on Android, this app) is set to. */
val S: Strings get() = L10n.forLocale(Locale.getDefault())

object L10n {

    /** Every language the app speaks, by tag, with its own name for itself. */
    val languages: List<Pair<String, String>> = listOf(
        "en" to "English",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "hi" to "हिन्दी",
        "pt" to "Português (Brasil)",
        "ru" to "Русский",
        "zh" to "中文（简体）",
    )

    /** By language alone: Portuguese is Brazilian, Chinese is simplified, whatever the region. */
    fun forLocale(locale: Locale): Strings = when (locale.language) {
        "de" -> StringsDe
        "es" -> StringsEs
        "fr" -> StringsFr
        "hi" -> StringsHi
        "pt" -> StringsPt
        "ru" -> StringsRu
        "zh" -> StringsZh
        else -> English
    }

    /** The base strings, for comparing a translation against. */
    val English: Strings = object : Strings() {}
}
