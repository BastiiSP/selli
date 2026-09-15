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

    @Test
    fun `findet og image in der name Variante`() {
        val html = """
            <html><head>
              <meta name="og:image" content="https://example.com/name-image.jpg">
            </head></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://example.com")

        val preview = parseLinkPreview(document)

        assertEquals("https://example.com/name-image.jpg", preview.imageUrl)
    }

    @Test
    fun `verwendet twitter image wenn kein og image existiert`() {
        val html = """
            <html><head>
              <meta name="twitter:image" content="https://example.com/twitter-image.jpg">
            </head></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://example.com")

        val preview = parseLinkPreview(document)

        assertEquals("https://example.com/twitter-image.jpg", preview.imageUrl)
    }

    @Test
    fun `verwendet image src Link als Fallback`() {
        val html = """
            <html><head>
              <link rel="image_src" href="https://example.com/link-image.jpg">
            </head></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://example.com")

        val preview = parseLinkPreview(document)

        assertEquals("https://example.com/link-image.jpg", preview.imageUrl)
    }

    @Test
    fun `loest relativen Bildpfad gegen die Base URI auf`() {
        val html = """
            <html><head>
              <meta property="og:image" content="/img/pasta.jpg">
            </head></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://example.com")

        val preview = parseLinkPreview(document)

        assertEquals("https://example.com/img/pasta.jpg", preview.imageUrl)
    }

    @Test
    fun `bevorzugt og image vor twitter image`() {
        val html = """
            <html><head>
              <meta name="twitter:image" content="https://example.com/twitter-image.jpg">
              <meta property="og:image" content="https://example.com/og-image.jpg">
            </head></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://example.com")

        val preview = parseLinkPreview(document)

        assertEquals("https://example.com/og-image.jpg", preview.imageUrl)
    }
}
