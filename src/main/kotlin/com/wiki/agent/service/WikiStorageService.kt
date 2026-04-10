package com.wiki.agent.service

import com.wiki.agent.config.WikiProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

@Service
class WikiStorageService(private val props: WikiProperties) {

    private val log = LoggerFactory.getLogger(WikiStorageService::class.java)
    private val activeFile: Path get() = props.storagesPath.resolve(".active")

    @Volatile
    private var activeName: String = "default"

    init {
        Files.createDirectories(props.storagesPath)
        if (activeFile.exists()) {
            val saved = activeFile.readText().trim()
            if (saved == "default" || props.storagesPath.resolve(saved).exists()) {
                activeName = saved
                log.info("Loaded active storage: {}", activeName)
            } else {
                log.warn("Saved active storage '{}' not found, falling back to default", saved)
            }
        }
        // Ensure active storage dirs exist (no-op for default since WikiService handles those)
        if (activeName != "default") {
            Files.createDirectories(activeWikiPath())
            Files.createDirectories(activeRawPath())
        }
    }

    fun activeWikiPath(): Path =
        if (activeName == "default") props.wikiPath
        else props.storagesPath.resolve(activeName).resolve("wiki")

    fun activeRawPath(): Path =
        if (activeName == "default") props.rawPath
        else props.storagesPath.resolve(activeName).resolve("raw")

    fun activeName(): String = activeName

    fun createStorage(name: String): String {
        if (name == "default") return "ERROR: 'default' storage already exists"
        if (!name.matches(Regex("[a-z0-9][a-z0-9-]*"))) {
            return "ERROR: Invalid name '$name' — use lowercase letters, numbers, and hyphens only, starting with a letter or digit"
        }
        val wikiPath = props.storagesPath.resolve(name).resolve("wiki")
        if (wikiPath.exists()) return "Storage '$name' already exists"
        Files.createDirectories(wikiPath)
        Files.createDirectories(props.storagesPath.resolve(name).resolve("raw"))
        log.info("Created storage: {}", name)
        return "Storage '$name' created"
    }

    fun listStorages(): String {
        val names = mutableListOf("default")
        if (props.storagesPath.exists()) {
            props.storagesPath.listDirectoryEntries()
                .filter { it.isDirectory() }
                .map { it.name }
                .sorted()
                .forEach { names.add(it) }
        }
        // Deduplicate in case storagesPath contains a dir named "default"
        return names.distinct().joinToString("\n") { name ->
            val marker = if (name == activeName) " (active)" else ""
            "- $name$marker"
        }
    }

    fun useStorage(name: String): String {
        if (name != "default" && !props.storagesPath.resolve(name).exists()) {
            return "ERROR: Storage '$name' not found. Use wiki_create_storage to create it first."
        }
        activeName = name
        Files.createDirectories(props.storagesPath)
        activeFile.writeText(name)
        Files.createDirectories(activeWikiPath())
        Files.createDirectories(activeRawPath())
        log.info("Switched active storage to: {}", name)
        return "Switched to storage '$name'"
    }

    fun currentStorage(): String {
        val wikiPath = activeWikiPath()
        val pageCount = if (wikiPath.exists()) wikiPath.listDirectoryEntries("*.md").size else 0
        return "Active storage: $activeName (${wikiPath.toAbsolutePath()}, $pageCount pages)"
    }
}
