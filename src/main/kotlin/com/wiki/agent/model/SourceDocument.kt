package com.wiki.agent.model

data class SourceDocument(
    val title: String,
    val content: String,
    val source: String,
    val type: String,
    val wordCount: Int = content.split("\\s+".toRegex()).size,
    val metadata: Map<String, String> = emptyMap(),
    val links: List<Link> = emptyList(),
) {
    data class Link(val text: String, val url: String)
}
