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
 * Bevorzugt Open-Graph-Tags und fällt auf weitere verbreitete Link-Metadaten zurück.
 */
fun parseLinkPreview(document: Document): LinkPreview {
    val title = document.firstNonBlankAttribute(
        "content",
        "meta[property=og:title]",
        "meta[name=og:title]",
        "meta[name=twitter:title]",
        "meta[property=twitter:title]",
    ) ?: document.title().takeUnless(String::isBlank)
    val imageUrl = document.firstResolvedAttribute(
        "meta[property=og:image]" to "content",
        "meta[name=og:image]" to "content",
        "meta[property=og:image:url]" to "content",
        "meta[name=twitter:image]" to "content",
        "meta[name=twitter:image:src]" to "content",
        "meta[property=twitter:image]" to "content",
        "meta[property=twitter:image:src]" to "content",
        "link[rel=image_src]" to "href",
        "meta[itemprop=image]" to "content",
    )
    return LinkPreview(title = title, imageUrl = imageUrl)
}

private fun Document.firstNonBlankAttribute(
    attribute: String,
    vararg selectors: String,
): String? = selectors.asSequence()
    .flatMap { selector ->
        select(selector).asSequence()
    }
    .mapNotNull { element -> element.attr(attribute).takeUnless(String::isBlank) }
    .firstOrNull()

private fun Document.firstResolvedAttribute(
    vararg candidates: Pair<String, String>,
): String? = candidates.asSequence()
    .flatMap { (selector, attribute) ->
        select(selector).asSequence().map { element ->
            element.attr("abs:$attribute")
                .ifBlank { element.attr(attribute) }
                .takeUnless(String::isBlank)
        }
    }
    .filterNotNull()
    .firstOrNull()
