package io.github.fanfeast.anonpdf.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.util.Locale

class L10nTest {

    private val translations = L10n.languages.map { it.first }.filter { it != "en" }
        .associateWith { L10n.forLocale(Locale.forLanguageTag(it)) }

    /** Every piece of text the table offers, called with sample arguments. */
    private fun Strings.everything(): Map<String, String> =
        Strings::class.java.methods
            .filter { it.returnType == String::class.java && it.declaringClass != Any::class.java }
            .associate { method -> method.name to call(method) }

    private fun Strings.call(method: Method): String {
        val args = method.parameterTypes.map { type ->
            when (type) {
                Int::class.javaPrimitiveType -> 3
                Long::class.javaPrimitiveType -> 3L
                Boolean::class.javaPrimitiveType -> true
                String::class.java -> "x"
                else -> error("${method.name} takes a ${type.simpleName}")
            }
        }
        return method.invoke(this, *args.toTypedArray()) as String
    }

    @Test
    fun `each language tag picks its own strings, and anything else is English`() {
        translations.values.forEach { assertNotSame(L10n.English, it) }
        assertEquals(L10n.English.cancel, L10n.forLocale(Locale.forLanguageTag("sv")).cancel)
        // A region does not stop a language being recognised.
        assertEquals(translations.getValue("pt"), L10n.forLocale(Locale.forLanguageTag("pt-PT")))
    }

    @Test
    fun `no language leaves any text blank or throws on a template`() {
        (translations + ("en" to L10n.English)).forEach { (tag, strings) ->
            strings.everything().forEach { (name, text) ->
                assertTrue("$tag.$name is blank", text.isNotBlank())
            }
        }
    }

    @Test
    fun `each translation covers nearly everything`() {
        val english = L10n.English.everything()
        translations.forEach { (tag, strings) ->
            val translated = strings.everything()
            val same = english.filter { (name, text) -> translated[name] == text }.keys
            // Some words really are the same (PDF, Format, Version, Position…), so
            // allow a little; a forgotten section would blow well past this.
            assertTrue(
                "$tag leaves ${same.size} of ${english.size} in English: $same",
                same.size <= english.size / 8,
            )
        }
    }

    @Test
    fun `page-number templates keep their placeholders`() {
        (translations.values + L10n.English).forEach { strings ->
            assertTrue(strings.numberPresetPage.contains("{n}"))
            assertTrue(strings.formatHint.contains("{n}") && strings.formatHint.contains("{total}"))
        }
    }
}
