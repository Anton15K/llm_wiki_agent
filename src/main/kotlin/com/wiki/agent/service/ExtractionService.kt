package com.wiki.agent.service

import com.wiki.agent.config.WikiProperties
import com.wiki.agent.extract.PdfExtractor
import com.wiki.agent.extract.TextExtractor
import com.wiki.agent.extract.UrlExtractor
import com.wiki.agent.model.SourceDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.writeText

@Service
class ExtractionService(
    private val props: WikiProperties,
    private val urlExtractor: UrlExtractor,
    private val pdfExtractor: PdfExtractor,
    private val textExtractor: TextExtractor,
) {

    private val log = LoggerFactory.getLogger(ExtractionService::class.java)

    fun extract(source: String, type: String?): SourceDocument {
        val resolvedType = type ?: detectType(source)
        log.info("Extracting source='{}' type='{}'", source, resolvedType)

        val doc = when (resolvedType) {
            "url" -> urlExtractor.extract(source)
            "pdf" -> pdfExtractor.extract(source)
            "text" -> textExtractor.extract(source)
            else -> throw IllegalArgumentException("Unsupported source type: $resolvedType")
        }

        saveRaw(doc)
        return doc
    }

    private fun detectType(source: String): String = when {
        source.startsWith("http://") || source.startsWith("https://") -> "url"
        source.endsWith(".pdf", ignoreCase = true) -> "pdf"
        source.endsWith(".md") || source.endsWith(".txt") -> "text"
        else -> "text"
    }

    private fun saveRaw(doc: SourceDocument) {
        Files.createDirectories(props.rawPath)
        val filename = sanitizeFilename(doc.title) + "-${Instant.now().epochSecond}.md"
        val rawFile = props.rawPath.resolve(filename)
        val rawContent = buildString {
            appendLine("---")
            appendLine("source: ${doc.source}")
            appendLine("type: ${doc.type}")
            appendLine("extracted: ${Instant.now()}")
            appendLine("word_count: ${doc.wordCount}")
            for ((k, v) in doc.metadata) {
                appendLine("$k: $v")
            }
            appendLine("---")
            appendLine()
            append(doc.content)
        }
        rawFile.writeText(rawContent)
        log.info("Saved raw source: {}", rawFile)
    }

    private fun sanitizeFilename(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(60)
}
