package com.wiki.agent.tool

import com.wiki.agent.service.WikiService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class WikiTools(private val wikiService: WikiService) {

    @Tool(name = "wiki_write_page", description = "Create or overwrite a wiki page. Path should be kebab-case .md filename (e.g. 'machine-learning-basics.md'). Content should include YAML frontmatter.")
    fun writePage(
        @ToolParam(description = "Page filename (e.g. 'machine-learning-basics.md')") path: String,
        @ToolParam(description = "Full page content including YAML frontmatter") content: String,
    ): String = wikiService.writePage(path, content)

    @Tool(name = "wiki_read_page", description = "Read a wiki page by filename. Returns full content including frontmatter.")
    fun readPage(
        @ToolParam(description = "Page filename (e.g. 'machine-learning-basics.md')") path: String,
    ): String = wikiService.readPage(path)

    @Tool(name = "wiki_delete_page", description = "Delete a wiki page. Cannot delete protected pages (index.md, log.md).")
    fun deletePage(
        @ToolParam(description = "Page filename to delete") path: String,
    ): String = wikiService.deletePage(path)

    @Tool(name = "wiki_list_pages", description = "List all wiki pages with titles and sizes.")
    fun listPages(): String = wikiService.listPages()

    @Tool(name = "wiki_update_index", description = "Overwrite index.md with new content. Used to maintain the master table of contents.")
    fun updateIndex(
        @ToolParam(description = "Full index.md content including frontmatter") content: String,
    ): String = wikiService.updateIndex(content)

    @Tool(name = "wiki_append_log", description = "Append a timestamped entry to log.md. Used to track all wiki operations.")
    fun appendLog(
        @ToolParam(description = "Operation type (e.g. 'create', 'update', 'delete', 'extract')") operation: String,
        @ToolParam(description = "Subject of the operation (e.g. page name or source URL)") subject: String,
        @ToolParam(description = "Additional details about the operation") details: String,
    ): String = wikiService.appendLog(operation, subject, details)

    @Tool(name = "wiki_search", description = "Case-insensitive keyword search across all wiki pages. Returns matching filenames and lines with context.")
    fun search(
        @ToolParam(description = "Search query string") query: String,
        @ToolParam(description = "Max number of results to return", required = false) limit: Int?,
    ): String = wikiService.search(query, limit ?: 20)

    @Tool(name = "wiki_status", description = "Get wiki status: page count, raw source count, last log entry, and wiki size.")
    fun status(): String = wikiService.status()
}
