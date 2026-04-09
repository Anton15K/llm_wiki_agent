package com.wiki.agent.extract

import com.wiki.agent.model.SourceDocument
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class UrlExtractor : Extractor {

    private val log = LoggerFactory.getLogger(UrlExtractor::class.java)

    override fun extract(source: String): SourceDocument {
        log.info("Fetching URL: {}", source)
        val doc = Jsoup.connect(source)
            .userAgent("WikiAgent/1.0")
            .timeout(30_000)
            .get()

        val title = doc.title().ifBlank { source }

        // Remove script, style, nav, footer elements for cleaner text
        doc.select("script, style, nav, footer, header, aside, .sidebar, .nav, .menu").remove()

        val body = doc.body()
        val content = body?.text() ?: ""

        return SourceDocument(
            title = title,
            content = content,
            source = source,
            type = "url",
            metadata = mapOf("url" to source),
        )
    }
}
