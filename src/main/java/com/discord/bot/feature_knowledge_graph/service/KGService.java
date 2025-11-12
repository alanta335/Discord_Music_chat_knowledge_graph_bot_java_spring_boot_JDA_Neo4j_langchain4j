package com.discord.bot.feature_knowledge_graph.service;

import com.discord.bot.feature_knowledge_graph.agent.ChatToGraphAgent;
import com.discord.bot.feature_knowledge_graph.agent.FriendlyAnswerAgent;
import com.discord.bot.feature_knowledge_graph.agent.GraphToCypherQueryAgent;
import com.discord.bot.feature_knowledge_graph.domain.GraphResult;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KGService {
    private final ChatToGraphAgent chatToGraphAgent;
    private final GraphToCypherQueryAgent graphToCypherQueryAgent;
    private final Neo4jClient neo4jClient;
    private final Neo4jText2CypherRetriever neo4jText2CypherRetriever;
    private final FriendlyAnswerAgent friendlyAnswerAgent;

    public List<String> getJsonGraph(final List<String> messages) {
        final GraphResult graphResult = chatToGraphAgent.extractGraphJson(messages);
        return graphToCypherQueryAgent.convertJsonGraphToCypherCommands(graphResult);
    }

    public String searchAnswerFromGraph(final String question, final String userName) {
        Query query = new Query(question);
        List<Content> contents = neo4jText2CypherRetriever.retrieve(query);
        return friendlyAnswerAgent.formatAnswer(question, contents.toString(), userName);
    }

    @Transactional
    public void createKNGraph(final List<String> cypherCommands) {
        try {
            // Implementation to create knowledge graph in the database using cypher commands
            for (String command : cypherCommands) {
                log.info("Executing Cypher Command: {}", command);
                // Execute command against the graph database
                neo4jClient.query(command)
                        .run();  // you can fetch result if needed
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
