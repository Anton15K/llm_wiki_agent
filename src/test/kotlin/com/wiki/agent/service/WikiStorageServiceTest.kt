package com.wiki.agent.service

import com.wiki.agent.config.WikiProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WikiStorageServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var props: WikiProperties
    private lateinit var service: WikiStorageService

    @BeforeEach
    fun setUp() {
        props = WikiProperties(
            wikiPath = tempDir.resolve("wiki"),
            rawPath = tempDir.resolve("raw"),
            storagesPath = tempDir.resolve("storages"),
        )
        service = WikiStorageService(props)
    }

    // --- Active storage defaults ---

    @Test
    fun `default active storage uses configured wiki and raw paths`() {
        assertEquals("default", service.activeName())
        assertEquals(props.wikiPath, service.activeWikiPath())
        assertEquals(props.rawPath, service.activeRawPath())
    }

    // --- Create storage ---

    @Test
    fun `create storage creates wiki and raw directories`() {
        service.createStorage("my-notes")
        assertTrue(props.storagesPath.resolve("my-notes").resolve("wiki").exists())
        assertTrue(props.storagesPath.resolve("my-notes").resolve("raw").exists())
    }

    @Test
    fun `create storage returns success message`() {
        val result = service.createStorage("project-x")
        assertContains(result, "project-x")
        assertFalse(result.startsWith("ERROR"))
    }

    @Test
    fun `create duplicate storage returns error`() {
        service.createStorage("dupe")
        val result = service.createStorage("dupe")
        assertContains(result, "already exists")
    }

    @Test
    fun `create storage with invalid name returns error`() {
        assertContains(service.createStorage("Has Spaces"), "ERROR")
        assertContains(service.createStorage("UPPER"), "ERROR")
        assertContains(service.createStorage("has/slash"), "ERROR")
    }

    @Test
    fun `cannot create storage named default`() {
        val result = service.createStorage("default")
        assertContains(result, "ERROR")
    }

    @Test
    fun `valid storage names are accepted`() {
        assertFalse(service.createStorage("notes").startsWith("ERROR"))
        assertFalse(service.createStorage("work-2024").startsWith("ERROR"))
        assertFalse(service.createStorage("project1").startsWith("ERROR"))
    }

    // --- List storages ---

    @Test
    fun `list storages always includes default`() {
        val result = service.listStorages()
        assertContains(result, "default")
    }

    @Test
    fun `list storages marks active storage`() {
        val result = service.listStorages()
        assertContains(result, "default (active)")
    }

    @Test
    fun `list storages includes created storages`() {
        service.createStorage("work")
        service.createStorage("personal")
        val result = service.listStorages()
        assertContains(result, "work")
        assertContains(result, "personal")
    }

    @Test
    fun `list storages updates active marker after switch`() {
        service.createStorage("other")
        service.useStorage("other")
        val result = service.listStorages()
        assertContains(result, "other (active)")
        assertFalse(result.contains("default (active)"))
    }

    // --- Use storage ---

    @Test
    fun `use storage switches active name`() {
        service.createStorage("alt")
        service.useStorage("alt")
        assertEquals("alt", service.activeName())
    }

    @Test
    fun `use storage updates wiki and raw paths`() {
        service.createStorage("alt")
        service.useStorage("alt")
        assertEquals(props.storagesPath.resolve("alt").resolve("wiki"), service.activeWikiPath())
        assertEquals(props.storagesPath.resolve("alt").resolve("raw"), service.activeRawPath())
    }

    @Test
    fun `use nonexistent storage returns error`() {
        val result = service.useStorage("ghost")
        assertContains(result, "ERROR")
        assertEquals("default", service.activeName())
    }

    @Test
    fun `use default switches back to original paths`() {
        service.createStorage("alt")
        service.useStorage("alt")
        service.useStorage("default")
        assertEquals("default", service.activeName())
        assertEquals(props.wikiPath, service.activeWikiPath())
    }

    // --- Persistence across restarts ---

    @Test
    fun `active storage persists across service restart`() {
        service.createStorage("persistent")
        service.useStorage("persistent")

        // Simulate restart by creating a new instance with the same props
        val restarted = WikiStorageService(props)
        assertEquals("persistent", restarted.activeName())
    }

    @Test
    fun `missing storage in active file falls back to default on restart`() {
        service.createStorage("temp")
        service.useStorage("temp")
        // Remove the storage directory to simulate it being deleted externally
        props.storagesPath.resolve("temp").toFile().deleteRecursively()

        val restarted = WikiStorageService(props)
        assertEquals("default", restarted.activeName())
    }

    // --- Current storage ---

    @Test
    fun `current storage includes active name`() {
        val result = service.currentStorage()
        assertContains(result, "default")
    }

    @Test
    fun `current storage reflects switched storage`() {
        service.createStorage("work")
        service.useStorage("work")
        val result = service.currentStorage()
        assertContains(result, "work")
    }

    // --- Integration: WikiService uses active storage ---

    @Test
    fun `WikiService writes to active storage directory`() {
        service.createStorage("isolated")
        service.useStorage("isolated")

        val wikiService = WikiService(service)
        wikiService.writePage("hello.md", "content")

        assertTrue(props.storagesPath.resolve("isolated").resolve("wiki").resolve("hello.md").exists())
        assertFalse(props.wikiPath.resolve("hello.md").exists())
    }

    @Test
    fun `switching storage changes which pages WikiService sees`() {
        // Write a page in default storage
        val wikiService = WikiService(service)
        wikiService.writePage("default-page.md", "in default")

        // Switch to new storage and write a different page
        service.createStorage("second")
        service.useStorage("second")
        wikiService.writePage("second-page.md", "in second")

        // Only second-page.md is visible now
        val list = wikiService.listPages()
        assertContains(list, "second-page.md")
        assertFalse(list.contains("default-page.md"))

        // Switch back — default-page.md reappears
        service.useStorage("default")
        val defaultList = wikiService.listPages()
        assertContains(defaultList, "default-page.md")
        assertFalse(defaultList.contains("second-page.md"))
    }
}
