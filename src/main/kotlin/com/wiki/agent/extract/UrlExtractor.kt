package com.wiki.agent.extract

import com.wiki.agent.model.SourceDocument
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class UrlExtractor : Extractor {

    private val log = LoggerFactory.getLogger(UrlExtractor::class.java)
    private val executor = Executors.newCachedThreadPool()

    companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val OVERALL_TIMEOUT_SEC = 60L
    }

    override fun extract(source: String): SourceDocument {
        log.info("Fetching URL: {}", source)

        val future = executor.submit(Callable { doExtract(source) })
        try {
            return future.get(OVERALL_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw RuntimeException("URL extraction timed out after ${OVERALL_TIMEOUT_SEC}s for: $source", e)
        }
    }

    private fun doExtract(source: String): SourceDocument {
        val doc = Jsoup.connect(source)
            .userAgent("WikiAgent/1.0")
            .timeout(CONNECT_TIMEOUT_MS)
            .followRedirects(true)
            .maxBodySize(10 * 1024 * 1024) // 10 MB cap
            .get()

        val title = doc.title().ifBlank { source }

        // Collect all links before removing elements
        val links = doc.select("a[href]")
            .mapNotNull { el ->
                val href = el.absUrl("href").ifBlank { el.attr("href") }
                val text = el.text().trim()
                if (href.isNotBlank() && href.startsWith("http")) {
                    SourceDocument.Link(text = text.ifBlank { href }, url = href)
                } else null
            }
            .distinctBy { it.url }

        // Remove noisy elements for cleaner text
        doc.select("script, style, nav, footer, header, aside, .sidebar, .nav, .menu").remove()

        val body = doc.body()
        val textContent = body?.text() ?: ""

        // Append links section to content
        val fullContent = buildString {
            append(textContent)
            if (links.isNotEmpty()) {
                appendLine()
                appendLine()
                appendLine("## Links found on page (${links.size})")
                appendLine()
                for (link in links) {
                    appendLine("- [${link.text}](${link.url})")
                }
            }
        }

        return SourceDocument(
            title = title,
            content = fullContent,
            source = source,
            type = "url",
            metadata = mapOf("url" to source, "link_count" to links.size.toString()),
            links = links,
        )
    }
}
