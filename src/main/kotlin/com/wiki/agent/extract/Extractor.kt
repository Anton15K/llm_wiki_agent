package com.wiki.agent.extract

import com.wiki.agent.model.SourceDocument

interface Extractor {
    fun extract(source: String): SourceDocument
}
