package com.prehmus.selli.domain.notes

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewParserTest {
    @Test
    fun `prefers open graph title and image over plain title`() {
        val html = """
            <html><head>
              <title>Fallback Title</title>
              <meta property="og:title" content="Pasta Carbonara Rezept">
              <meta property="og:image" content="https://example.com/pasta.jpg">
            </head></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://example.com")

        val preview = parseLinkPreview(document)

        assertEquals("Pasta Carbonara Rezept", preview.title)
        assertEquals("https://example.com/pasta.jpg", preview.imageUrl)
    }

    @Test
    fun `falls back to plain title when no open graph tag exists`() {
        val html = "<html><head><title>Nur ein Titel</title></head></html>"
        val document = Jsoup.parse(html, "https://example.com")

        val preview = parseLinkPreview(document)

        assertEquals("Nur ein Titel", preview.title)
        assertNull(preview.imageUrl)
    }

    @Test
    fun `missing title and image yields nulls, never throws`() {
        val document = Jsoup.parse("<html><head></head><body></body></html>", "https://example.com")

        val preview = parseLinkPreview(document)

        assertNull(preview.title)
        assertNull(preview.imageUrl)
    }
}
