package com.wiki.agent.tool

import com.wiki.agent.service.WikiService
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class WikiTools(private val wikiService: WikiService) {

    private val log = LoggerFactory.getLogger(WikiTools::class.java)
    private val executor = Executors.newCachedThreadPool()

    companion object {
        private const val TOOL_TIMEOUT_SEC = 30L
    }

    @Tool(name = "wiki_write_page", description = "Create or overwrite a wiki page. Path should be kebab-case .md filename (e.g. 'machine-learning-basics.md'). Content should include YAML frontmatter.")
    fun writePage(
        @ToolParam(description = "Page filename (e.g. 'machine-learning-basics.md')") path: String,
        @ToolParam(description = "Full page content including YAML frontmatter") content: String,
    ): String = runWithTimeout("writePage($path)") { wikiService.writePage(path, content) }

    @Tool(name = "wiki_read_page", description = "Read a wiki page by filename. Returns full content including frontmatter.")
    fun readPage(
        @ToolParam(description = "Page filename (e.g. 'machine-learning-basics.md')") path: String,
    ): String = runWithTimeout("readPage($path)") { wikiService.readPage(path) }

    @Tool(name = "wiki_delete_page", description = "Delete a wiki page. Cannot delete protected pages (index.md, log.md).")
    fun deletePage(
        @ToolParam(description = "Page filename to delete") path: String,
    ): String = runWithTimeout("deletePage($path)") { wikiService.deletePage(path) }

    @Tool(name = "wiki_list_pages", description = "List all wiki pages with titles and sizes.")
    fun listPages(): String = runWithTimeout("listPages") { wikiService.listPages() }

    @Tool(name = "wiki_update_index", description = "Overwrite index.md with new content. Used to maintain the master table of contents.")
    fun updateIndex(
        @ToolParam(description = "Full index.md content including frontmatter") content: String,
    ): String = runWithTimeout("updateIndex") { wikiService.updateIndex(content) }

    @Tool(name = "wiki_append_log", description = "Append a timestamped entry to log.md. Used to track all wiki operations.")
    fun appendLog(
        @ToolParam(description = "Operation type (e.g. 'create', 'update', 'delete', 'extract')") operation: String,
        @ToolParam(description = "Subject of the operation (e.g. page name or source URL)") subject: String,
        @ToolParam(description = "Additional details about the operation") details: String,
    ): String = runWithTimeout("appendLog($operation, $subject)") { wikiService.appendLog(operation, subject, details) }

    @Tool(name = "wiki_search", description = "Case-insensitive keyword search across all wiki pages. Returns matching filenames and lines with context.")
    fun search(
        @ToolParam(description = "Search query string") query: String,
        @ToolParam(description = "Max number of results to return", required = false) limit: Int?,
    ): String = runWithTimeout("search($query)") { wikiService.search(query, limit ?: 20) }

    @Tool(name = "wiki_status", description = "Get wiki status: page count, raw source count, last log entry, and wiki size.")
    fun status(): String = runWithTimeout("status") { wikiService.status() }

    private fun runWithTimeout(operation: String, block: () -> String): String {
        val future = executor.submit(Callable { block() })
        return try {
            future.get(TOOL_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            log.error("Tool timed out after {}s: {}", TOOL_TIMEOUT_SEC, operation)
            "ERROR: Operation timed out after ${TOOL_TIMEOUT_SEC}s — $operation"
        } catch (e: Exception) {
            val cause = e.cause ?: e
            log.error("Tool failed: {} — {}", operation, cause.message, cause)
            "ERROR: ${cause.message}"
        }
    }
}
