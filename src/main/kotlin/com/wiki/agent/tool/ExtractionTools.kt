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
        return formatResult(doc.title, doc.type, doc.wordCount, doc.source, doc.content)
    }

    @Tool(
        name = "wiki_transcribe",
        description = "Transcribe audio or video files using OpenAI Whisper API. For video files, audio is extracted via ffmpeg first. Saves transcript to raw/ directory. Requires OPENAI_API_KEY."
    )
    fun transcribe(
        @ToolParam(description = "Path to audio file (.mp3, .wav, .m4a, .ogg) or video file (.mp4, .mkv, .webm)") filePath: String,
        @ToolParam(description = "Language code (e.g. 'en', 'es', 'de'). Auto-detected if omitted.", required = false) language: String?,
    ): String {
        val doc = extractionService.transcribe(filePath, language)
        return formatResult(doc.title, doc.type, doc.wordCount, doc.source, doc.content)
    }

    private fun formatResult(title: String, type: String, wordCount: Int, source: String, content: String): String =
        buildString {
            appendLine("Title: $title")
            appendLine("Type: $type")
            appendLine("Words: $wordCount")
            appendLine("Source: $source")
            appendLine()
            append(content)
        }
}
