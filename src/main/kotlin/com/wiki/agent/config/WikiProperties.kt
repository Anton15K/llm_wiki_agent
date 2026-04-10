package com.wiki.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties(prefix = "wiki")
data class WikiProperties(
    val wikiPath: Path = Path.of("./wiki"),
    val rawPath: Path = Path.of("./raw"),
    val storagesPath: Path = Path.of("./storages"),
    val openaiApiKey: String = "",
)
