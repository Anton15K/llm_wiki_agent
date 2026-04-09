package com.wiki.agent.extract

import com.wiki.agent.model.SourceDocument
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

@Component
class PdfExtractor : Extractor {

    private val log = LoggerFactory.getLogger(PdfExtractor::class.java)
    private val tika = Tika()

    override fun extract(source: String): SourceDocument {
        log.info("Extracting PDF: {}", source)
        val path = Path.of(source)
        val content = tika.parseToString(path)

        return SourceDocument(
            title = path.nameWithoutExtension,
            content = content,
            source = path.toAbsolutePath().toString(),
            type = "pdf",
            metadata = mapOf("filename" to path.name),
        )
    }
}
