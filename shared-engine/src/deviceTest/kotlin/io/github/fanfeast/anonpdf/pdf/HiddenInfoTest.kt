package io.github.fanfeast.anonpdf.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Calendar

class HiddenInfoTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = TestPlatform.scratchDir("hiddeninfotest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun name(value: String) = COSName.getPDFName(value)

    private fun script(code: String) = COSDictionary().apply {
        setItem(COSName.TYPE, name("Action"))
        setItem(COSName.S, name("JavaScript"))
        setString(name("JS"), code)
    }

    /** One page of visible text, plus every kind of hidden information there is. */
    private fun loadedPdf(): File {
        val file = File(workDir, "loaded.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 22f)
                stream.newLineAtOffset(64f, 700f)
                stream.showText("Visible text stays")
                stream.endText()
            }

            document.documentInformation.apply {
                author = "Jane Doe"
                title = "Quarterly figures"
                creator = "Some Word Processor"
                producer = "Some PDF Library"
                creationDate = Calendar.getInstance()
                setCustomMetadataValue("Department", "Finance")
            }
            val catalog = document.documentCatalog.cosObject
            catalog.setItem(COSName.METADATA, PDMetadata(document).cosObject)
            catalog.setItem(name("PieceInfo"), COSDictionary())

            // An attached file and a document-level script, in the names tree.
            val names = COSDictionary()
            names.setItem(name("EmbeddedFiles"), COSDictionary().apply {
                setItem(COSName.NAMES, COSArray().apply {
                    add(COSString("figures.xlsx"))
                    add(COSDictionary())
                })
            })
            names.setItem(name("JavaScript"), COSDictionary().apply {
                setItem(COSName.NAMES, COSArray().apply {
                    add(COSString("init"))
                    add(script("app.alert('hi')"))
                })
            })
            catalog.setItem(COSName.NAMES, names)
            catalog.setItem(COSName.OPEN_ACTION, script("this.print()"))

            // A page-open trigger, a sticky note with its author, an attached
            // file on the page, and a plain web link that must survive.
            page.cosObject.setItem(COSName.AA, COSDictionary().apply { setItem(name("O"), script("1")) })
            val note = COSDictionary().apply {
                setItem(COSName.SUBTYPE, name("Text"))
                setItem(COSName.RECT, PDRectangle(10f, 10f, 20f, 20f).cosArray)
                setString(COSName.T, "Jane Doe")
                setString(COSName.CONTENTS, "Check this")
            }
            val attachment = COSDictionary().apply {
                setItem(COSName.SUBTYPE, name("FileAttachment"))
                setItem(COSName.RECT, PDRectangle(40f, 10f, 20f, 20f).cosArray)
            }
            val link = COSDictionary().apply {
                setItem(COSName.SUBTYPE, name("Link"))
                setItem(COSName.RECT, PDRectangle(70f, 10f, 20f, 20f).cosArray)
                setItem(COSName.A, COSDictionary().apply {
                    setItem(COSName.S, name("URI"))
                    setString(name("URI"), "https://example.org")
                })
            }
            page.cosObject.setItem(COSName.ANNOTS, COSArray().apply {
                add(note)
                add(attachment)
                add(link)
            })

            // A form field with a calculate script, and a bookmark that runs one.
            val field = COSDictionary().apply {
                setItem(COSName.FT, name("Tx"))
                setString(COSName.T, "total")
                setItem(COSName.AA, COSDictionary().apply { setItem(name("C"), script("2")) })
            }
            catalog.setItem(COSName.ACRO_FORM, COSDictionary().apply {
                setItem(COSName.FIELDS, COSArray().apply { add(field) })
            })
            val bookmark = COSDictionary().apply {
                setString(COSName.TITLE, "Start")
                setItem(COSName.A, script("3"))
            }
            catalog.setItem(COSName.OUTLINES, COSDictionary().apply {
                setItem(COSName.FIRST, bookmark)
                setItem(COSName.LAST, bookmark)
            })

            document.save(file)
        }
        return file
    }

    @Test
    fun inspectReportsEveryKindOfHiddenInfo() = runBlocking {
        val info = HiddenInfoCleaner.inspect(loadedPdf())!!

        assertEquals("Jane Doe", info.author)
        assertEquals("Quarterly figures", info.title)
        assertEquals("Some Word Processor", info.creatorApp)
        assertEquals("Some PDF Library", info.producerApp)
        assertNotNull(info.created)
        assertEquals(1, info.customProperties)
        assertTrue(info.hasXmp)
        assertTrue(info.hasPrivateData)
        // One in the names tree, one on the page.
        assertEquals(2, info.attachments)
        // Names-tree script, open action, page trigger, field trigger, bookmark.
        assertEquals(5, info.scripts)
        assertEquals(setOf("Jane Doe"), info.commentAuthors)
    }

    @Test
    fun cleanRemovesItAllAndKeepsThePage() = runBlocking {
        val cleaned = File(workDir, "cleaned.pdf")
        HiddenInfoCleaner.clean(loadedPdf(), cleaned)

        val after = HiddenInfoCleaner.inspect(cleaned)!!
        assertTrue("left behind: $after", after.isEmpty)

        PDDocument.load(cleaned).use { document ->
            assertEquals(1, document.numberOfPages)
            assertTrue(PDFTextStripper().getText(document).contains("Visible text stays"))
            val annots = document.getPage(0).annotations
            // The note keeps its text but loses its author; the attachment goes;
            // the web link is the user's to see and stays.
            assertEquals(listOf("Text", "Link"), annots.map { it.subtype })
            assertEquals("Check this", annots[0].contents)
            assertNull(annots[0].cosObject.getString(COSName.T))
            assertEquals("total", (document.documentCatalog.acroForm.fields.single()).partialName)
            assertNull(document.documentCatalog.cosObject.getDictionaryObject(COSName.OPEN_ACTION))
        }
    }

    @Test
    fun aPlainDocumentHasNothingToOffer() = runBlocking {
        val file = File(workDir, "plain.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.A4))
            document.save(file)
        }
        val info = HiddenInfoCleaner.inspect(file)!!
        assertTrue("found: $info", info.isEmpty)
    }

    @Test
    fun aLockedFileIsLeftAlone() = runBlocking {
        val locked = File(workDir, "locked.pdf")
        PdfOps.protect(loadedPdf(), locked, ProtectOptions(userPassword = "secret"))
        assertNull(HiddenInfoCleaner.inspect(locked))
        assertFalse(locked.length() == 0L)
    }
}
