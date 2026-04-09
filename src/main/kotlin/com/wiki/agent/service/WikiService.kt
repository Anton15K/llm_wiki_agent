package com.wiki.agent.service

import com.wiki.agent.config.WikiProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

@Service
class WikiService(private val props: WikiProperties) {

    private val log = LoggerFactory.getLogger(WikiService::class.java)
    private val wikiDir: Path get() = props.wikiPath
    private val rawDir: Path get() = props.rawPath

    init {
        Files.createDirectories(wikiDir)
        Files.createDirectories(rawDir)
        log.info("Wiki directory: {}", wikiDir.toAbsolutePath())
        log.info("Raw directory: {}", rawDir.toAbsolutePath())
    }

    // --- Page CRUD ---

    fun writePage(name: String, content: String): String {
        val path = resolvePagePath(name)
        path.writeText(content)
        log.info("Wrote page: {}", name)
        return "Page '$name' written (${content.length} chars)"
    }

    fun readPage(name: String): String {
        val path = resolvePagePath(name)
        if (!path.exists()) return "Page '$name' not found"
        return path.readText()
    }

    fun deletePage(name: String): String {
        if (name == "index.md" || name == "log.md") {
            return "Cannot delete protected page '$name'"
        }
        val path = resolvePagePath(name)
        if (!path.exists()) return "Page '$name' not found"
        path.deleteExisting()
        log.info("Deleted page: {}", name)
        return "Page '$name' deleted"
    }

    fun listPages(): String {
        val pages = wikiDir.listDirectoryEntries("*.md").sorted()
        if (pages.isEmpty()) return "No pages in wiki"
        return pages.joinToString("\n") { p ->
            val size = p.fileSize()
            val title = extractTitle(p)
            "- ${p.name} | $title | ${size}B"
        }
    }

    // --- Index & Log ---

    fun updateIndex(content: String): String {
        val path = wikiDir.resolve("index.md")
        path.writeText(content)
        log.info("Updated index.md")
        return "index.md updated (${content.length} chars)"
    }

    fun appendLog(operation: String, subject: String, details: String): String {
        val path = wikiDir.resolve("log.md")
        val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val entry = "| $timestamp | $operation | $subject | $details |"
        path.appendText("\n$entry")
        log.info("Appended to log: {} {}", operation, subject)
        return "Log entry appended: $operation $subject"
    }

    // --- Search ---

    fun search(query: String, limit: Int): String {
        val results = mutableListOf<String>()
        val queryLower = query.lowercase()
        for (page in wikiDir.listDirectoryEntries("*.md").sorted()) {
            val lines = page.readLines()
            val matches = lines.withIndex()
                .filter { it.value.lowercase().contains(queryLower) }
                .map { "  L${it.index + 1}: ${it.value.trim()}" }
            if (matches.isNotEmpty()) {
                results.add("${page.name}:\n${matches.joinToString("\n")}")
            }
        }
        if (results.isEmpty()) return "No results for '$query'"
        return results.take(limit).joinToString("\n\n")
    }

    // --- Status ---

    fun status(): String {
        val pageCount = wikiDir.listDirectoryEntries("*.md").size
        val rawCount = if (rawDir.exists()) {
            rawDir.listDirectoryEntries("*").count { it.isRegularFile() }
        } else 0
        val logPath = wikiDir.resolve("log.md")
        val lastLog = if (logPath.exists()) {
            logPath.readLines().lastOrNull { it.startsWith("|") && !it.contains("---") } ?: "No entries"
        } else "No log file"
        val wikiSize = wikiDir.listDirectoryEntries("*.md").sumOf { it.fileSize() }
        return """
            |Pages: $pageCount
            |Raw sources: $rawCount
            |Wiki size: ${wikiSize}B
            |Last log: $lastLog
        """.trimMargin()
    }

    // --- Helpers ---

    private fun resolvePagePath(name: String): Path {
        val safeName = if (name.endsWith(".md")) name else "$name.md"
        return wikiDir.resolve(safeName)
    }

    private fun extractTitle(page: Path): String {
        val lines = page.readLines()
        for (line in lines) {
            if (line.startsWith("title:")) {
                return line.substringAfter("title:").trim()
            }
        }
        return page.nameWithoutExtension
    }
}
