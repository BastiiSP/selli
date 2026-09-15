package com.prehmus.selli.domain.notes

import org.jsoup.nodes.Document

data class LinkPreview(
    val title: String?,
    val imageUrl: String?,
)

/** Lädt Titel/Vorschaubild einer URL. Wirft nie — Fehler/Timeout liefern `null`. */
fun interface LinkPreviewFetcher {
    suspend fun fetch(url: String): LinkPreview?
}

/**
 * Reine Extraktion aus einem bereits geladenen HTML-Dokument — getrennt vom Netzwerk-Fetch
 * (siehe `JsoupLinkPreviewFetcher`), damit sie ohne echten HTTP-Request testbar ist.
 * Bevorzugt Open-Graph-Tags, fällt auf `<title>` zurück.
 */
fun parseLinkPreview(document: Document): LinkPreview {
    val ogTitle = document.select("meta[property=og:title]").attr("content").takeUnless(String::isBlank)
    val title = ogTitle ?: document.title().takeUnless(String::isBlank)
    val ogImage = document.select("meta[property=og:image]")
    val imageUrl = ogImage.attr("abs:content")
        .ifBlank { ogImage.attr("content") }
        .takeUnless(String::isBlank)
    return LinkPreview(title = title, imageUrl = imageUrl)
}
