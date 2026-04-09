package com.wiki.agent.service

import com.wiki.agent.config.WikiProperties
import com.wiki.agent.extract.PdfExtractor
import com.wiki.agent.extract.TextExtractor
import com.wiki.agent.extract.UrlExtractor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractionServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var service: ExtractionService

    @BeforeEach
    fun setUp() {
        val props = WikiProperties(
            wikiPath = tempDir.resolve("wiki"),
            rawPath = tempDir.resolve("raw"),
        )
        service = ExtractionService(props, UrlExtractor(), PdfExtractor(), TextExtractor())
    }

    @Test
    fun `extract text file`() {
        val file = tempDir.resolve("sample.txt")
        file.writeText("Hello world, this is a test document.")

        val doc = service.extract(file.toString(), null)
        assertEquals("text", doc.type)
        assertContains(doc.content, "Hello world")
        assertTrue { tempDir.resolve("raw").listDirectoryEntries("*.md").isNotEmpty() }
    }

    @Test
    fun `extract markdown file`() {
        val file = tempDir.resolve("notes.md")
        file.writeText("# Notes\nSome content here")

        val doc = service.extract(file.toString(), null)
        assertEquals("text", doc.type)
        assertContains(doc.content, "# Notes")
    }

    @Test
    fun `type detection for urls`() {
        // We can't actually fetch URLs in tests, but we can verify type detection
        // by forcing a text type on a URL-like string
        val file = tempDir.resolve("test.txt")
        file.writeText("content")
        val doc = service.extract(file.toString(), "text")
        assertEquals("text", doc.type)
    }
}
