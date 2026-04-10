package com.wiki.agent.tool

import com.wiki.agent.service.ExtractionService
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class ExtractionTools(private val extractionService: ExtractionService) {

    private val log = LoggerFactory.getLogger(ExtractionTools::class.java)
    private val executor = Executors.newCachedThreadPool()

    companion object {
        private const val EXTRACT_TIMEOUT_SEC = 120L
        private const val TRANSCRIBE_TIMEOUT_SEC = 600L
    }

    @Tool(
        name = "wiki_extract",
        description = "Extract content from a URL, PDF file, or text file. Auto-detects type from the source string (URLs start with http, PDFs end in .pdf, otherwise text). Saves raw extraction to raw/ directory. Returns extracted content and a suggested frontmatter block — use it as-is when calling wiki_write_page so that the source URL is preserved in the page metadata."
    )
    fun extract(
        @ToolParam(description = "Source to extract: URL (https://...), file path to PDF (.pdf), or text file (.md/.txt)") source: String,
        @ToolParam(description = "Force source type: 'url', 'pdf', or 'text'. Auto-detected if omitted.", required = false) type: String?,
    ): String {
        return runWithTimeout(EXTRACT_TIMEOUT_SEC, "extract($source)") {
            val doc = extractionService.extract(source, type)

            buildString {
                appendLine("=== Suggested frontmatter (use when writing the wiki page) ===")
                appendLine("---")
                appendLine("title: ${doc.title}")
                appendLine("source: ${doc.source}")
                appendLine("source_type: ${doc.type}")
                appendLine("word_count: ${doc.wordCount}")
                if (doc.links.isNotEmpty()) appendLine("link_count: ${doc.links.size}")
                appendLine("tags: []")
                appendLine("---")
                appendLine()
                appendLine("=== Extracted content ===")
                append(doc.content)
            }
        }
    }

    @Tool(
        name = "wiki_transcribe",
        description = "Transcribe audio or video files using OpenAI Whisper API. For video files, audio is extracted via ffmpeg first. Saves transcript to raw/ directory. Requires OPENAI_API_KEY. Returns transcript and a suggested frontmatter block — use it as-is when calling wiki_write_page so that the source file path is preserved in the page metadata."
    )
    fun transcribe(
        @ToolParam(description = "Path to audio file (.mp3, .wav, .m4a, .ogg) or video file (.mp4, .mkv, .webm)") filePath: String,
        @ToolParam(description = "Language code (e.g. 'en', 'es', 'de'). Auto-detected if omitted.", required = false) language: String?,
    ): String {
        return runWithTimeout(TRANSCRIBE_TIMEOUT_SEC, "transcribe($filePath)") {
            val doc = extractionService.transcribe(filePath, language)

            buildString {
                appendLine("=== Suggested frontmatter (use when writing the wiki page) ===")
                appendLine("---")
                appendLine("title: ${doc.title}")
                appendLine("source: ${doc.source}")
                appendLine("source_type: ${doc.type}")
                appendLine("word_count: ${doc.wordCount}")
                appendLine("tags: []")
                appendLine("---")
                appendLine()
                appendLine("=== Transcript ===")
                append(doc.content)
            }
        }
    }

    private fun runWithTimeout(timeoutSec: Long, operation: String, block: () -> String): String {
        val future = executor.submit(Callable { block() })
        return try {
            future.get(timeoutSec, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            log.error("Operation timed out after {}s: {}", timeoutSec, operation)
            "ERROR: Operation timed out after ${timeoutSec}s — $operation"
        } catch (e: Exception) {
            val cause = e.cause ?: e
            log.error("Operation failed: {} — {}", operation, cause.message, cause)
            "ERROR: ${cause.message}"
        }
    }
}
