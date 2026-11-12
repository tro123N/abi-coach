package de.abicoach

import de.abicoach.memory.TextExtract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the text out of the file types that can be uploaded besides PDF and photos. */
class TextExtractTest {
    @Test
    fun wordParagraphsBecomeLines() {
        val xml = "<w:document><w:body><w:p><w:r><w:t>Brexit &amp; the UK</w:t></w:r></w:p><w:p><w:r><w:t>Second</w:t><w:br/><w:t>line</w:t></w:r></w:p></w:body></w:document>"
        assertEquals("Brexit & the UK\nSecond\nline", TextExtract.plainText(xml, Regex("</w:p>")))
    }

    @Test
    fun slideTextIsRead() {
        val xml = "<p:sld><a:p><a:r><a:t>The American Dream</a:t></a:r></a:p><a:p><a:r><a:t>myth or reality?</a:t></a:r></a:p></p:sld>"
        assertEquals("The American Dream\nmyth or reality?", TextExtract.plainText(xml, Regex("</a:p>")))
    }

    @Test
    fun entitiesAreResolvedOnlyOnce() {
        assertEquals("a &lt; b", TextExtract.plainText("<p>a &amp;lt; b</p>", Regex("</p>")))
    }

    @Test
    fun webPageWithoutScriptsAndStyles() {
        val html = "<html><head><title>x</title><style>p{color:red}</style></head><body><h1>Globalisation</h1><script>var a = 1;</script><p>Chances &amp; risks.</p><ul><li>one</li><li>two</li></ul></body></html>"
        assertEquals("Globalisation\nChances & risks.\none\ntwo", TextExtract.htmlText(html))
    }

    @Test
    fun rtfBecomesPlainText() {
        val rtf = "{\\rtf1\\ansi{\\fonttbl{\\f0 Arial;}}{\\colortbl;\\red0\\green0\\blue0;}\\pard\\f0\\fs24 Task 1: Summarise the text.\\par Don\\'92t forget the M\\u252?ller quote.\\par}"
        assertEquals("Task 1: Summarise the text.\nDon’t forget the Müller quote.", TextExtract.rtfText(rtf))
    }

    @Test
    fun textIsToldFromBinary() {
        assertTrue(TextExtract.looksLikeText("Notes on Nigeria\n- oil\n- Nollywood äöü".toByteArray()))
        assertFalse(TextExtract.looksLikeText(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 8, 0, 1, 2, 3)))
        assertFalse(TextExtract.looksLikeText(ByteArray(0)))
    }
}
