package com.discord.bot.config;

import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jGraph;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfig {
    @Value("${spring.neo4j.authentication.username}")
    private String neo4jUsername;
    @Value("${spring.neo4j.authentication.password}")
    private String neo4jPassword;
    @Value("${spring.neo4j.uri}")
    private String neo4jUri;

    @Bean
    public Neo4jGraph neo4jGraph() {
        return Neo4jGraph.builder()
                .withBasicAuth(neo4jUri, neo4jUsername, neo4jPassword)
                .build();
    }

    @Bean
    public Neo4jText2CypherRetriever neo4jText2CypherRetriever(Neo4jGraph neo4jGraph, ChatModel chatLanguageModel) {
        return Neo4jText2CypherRetriever.builder()
                .graph(neo4jGraph)
                .chatModel(chatLanguageModel)
                .build();
    }
}
