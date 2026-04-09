package com.wiki.agent.config

import com.wiki.agent.tool.ExtractionTools
import com.wiki.agent.tool.WikiTools
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ToolConfig {

    @Bean
    fun toolCallbackProvider(
        wikiTools: WikiTools,
        extractionTools: ExtractionTools,
    ): ToolCallbackProvider =
        MethodToolCallbackProvider.builder()
            .toolObjects(wikiTools, extractionTools)
            .build()
}
