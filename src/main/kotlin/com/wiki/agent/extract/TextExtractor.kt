package com.wiki.agent.extract

import com.wiki.agent.model.SourceDocument
import org.springframework.stereotype.Component
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

@Component
class TextExtractor : Extractor {

    override fun extract(source: String): SourceDocument {
        val path = Path.of(source)
        val content = path.readText()
        return SourceDocument(
            title = path.nameWithoutExtension,
            content = content,
            source = path.toAbsolutePath().toString(),
            type = "text",
            metadata = mapOf("filename" to path.name),
        )
    }
}
