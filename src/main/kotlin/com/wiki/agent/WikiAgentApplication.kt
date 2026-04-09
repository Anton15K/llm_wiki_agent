package com.wiki.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import com.wiki.agent.config.WikiProperties

@SpringBootApplication
@EnableConfigurationProperties(WikiProperties::class)
class WikiAgentApplication

fun main(args: Array<String>) {
    runApplication<WikiAgentApplication>(*args)
}
