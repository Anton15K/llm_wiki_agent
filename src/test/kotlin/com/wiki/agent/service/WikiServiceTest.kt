package com.wiki.agent.service

import com.wiki.agent.config.WikiProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikiServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var service: WikiService

    @BeforeEach
    fun setUp() {
        val wikiPath = tempDir.resolve("wiki")
        val rawPath = tempDir.resolve("raw")
        service = WikiService(WikiProperties(wikiPath, rawPath))
    }

    @Test
    fun `write and read page`() {
        service.writePage("test-page.md", "# Hello\nContent here")
        val content = service.readPage("test-page.md")
        assertEquals("# Hello\nContent here", content)
    }

    @Test
    fun `read nonexistent page`() {
        val result = service.readPage("nope.md")
        assertContains(result, "not found")
    }

    @Test
    fun `delete page`() {
        service.writePage("to-delete.md", "temp")
        val result = service.deletePage("to-delete.md")
        assertContains(result, "deleted")
        assertContains(service.readPage("to-delete.md"), "not found")
    }

    @Test
    fun `cannot delete protected pages`() {
        assertContains(service.deletePage("index.md"), "Cannot delete")
        assertContains(service.deletePage("log.md"), "Cannot delete")
    }

    @Test
    fun `list pages`() {
        service.writePage("alpha.md", "aaa")
        service.writePage("beta.md", "bbb")
        val list = service.listPages()
        assertContains(list, "alpha.md")
        assertContains(list, "beta.md")
    }

    @Test
    fun `search finds matching content`() {
        service.writePage("page-a.md", "Kotlin is great")
        service.writePage("page-b.md", "Java is also good")
        val results = service.search("kotlin", 10)
        assertContains(results, "page-a.md")
        assertTrue { !results.contains("page-b.md") }
    }

    @Test
    fun `status returns page count`() {
        service.writePage("one.md", "1")
        service.writePage("two.md", "2")
        val status = service.status()
        assertContains(status, "Pages: 2")
    }

    @Test
    fun `append log`() {
        // Create log.md first
        service.updateIndex("---\ntitle: Index\n---\n# Index")
        val wikiPath = tempDir.resolve("wiki")
        wikiPath.resolve("log.md").toFile().writeText("| Timestamp | Op | Subject | Details |\n|---|---|---|---|")
        val result = service.appendLog("create", "test-page", "test details")
        assertContains(result, "create")
    }
}
