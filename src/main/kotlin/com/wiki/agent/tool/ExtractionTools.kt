package com.wiki.agent.tool

import com.wiki.agent.service.ExtractionService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class ExtractionTools(private val extractionService: ExtractionService) {

    @Tool(
        name = "wiki_extract",
        description = "Extract content from a URL, PDF file, or text file. Auto-detects type from the source string (URLs start with http, PDFs end in .pdf, otherwise text). Saves raw extraction to raw/ directory. Returns extracted text content with metadata."
    )
    fun extract(
        @ToolParam(description = "Source to extract: URL (https://...), file path to PDF (.pdf), or text file (.md/.txt)") source: String,
        @ToolParam(description = "Force source type: 'url', 'pdf', or 'text'. Auto-detected if omitted.", required = false) type: String?,
    ): String {
        val doc = extractionService.extract(source, type)
        return buildString {
            appendLine("Title: ${doc.title}")
            appendLine("Type: ${doc.type}")
            appendLine("Words: ${doc.wordCount}")
            appendLine("Source: ${doc.source}")
            appendLine()
            append(doc.content)
        }
    }
}
