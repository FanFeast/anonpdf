package io.github.fanfeast.anonpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldFormatTest {

    @Test
    fun `form designer names read as words`() {
        assertEquals("First name", FieldFormat.readableName("txtFirstName"))
        assertEquals("First name", FieldFormat.readableName("topmostSubform[0].Page1[0].first_name[0]"))
        assertEquals("Date of birth", FieldFormat.readableName("date-of-birth"))
        assertEquals("Agree", FieldFormat.readableName("chkAgree"))
        assertEquals("Address line2", FieldFormat.readableName("Address_line2"))
    }

    @Test
    fun `a name that tidies to nothing stays as it was`() {
        assertEquals("[0]", FieldFormat.readableName("[0]"))
    }

    @Test
    fun `what the standard fonts cannot draw becomes a question mark`() {
        assertEquals("Café ??", FieldFormat.drawable("Café \u65e5\u672c"))
        assertEquals("two\nlines", FieldFormat.drawable("two\nlines"))
    }
}
