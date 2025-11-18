package com.discord.bot.feature_knowledge_graph.service;

import com.discord.bot.feature_knowledge_graph.agent.ChatToGraphAgent;
import com.discord.bot.feature_knowledge_graph.agent.FriendlyAnswerAgent;
import com.discord.bot.feature_knowledge_graph.agent.GraphToCypherQueryAgent;
import com.discord.bot.feature_knowledge_graph.domain.GraphResult;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KGService {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000; // 1 second

    private final ChatToGraphAgent chatToGraphAgent;
    private final GraphToCypherQueryAgent graphToCypherQueryAgent;
    private final Neo4jClient neo4jClient;
    private final Neo4jText2CypherRetriever neo4jText2CypherRetriever;
    private final FriendlyAnswerAgent friendlyAnswerAgent;

    public List<String> getJsonGraph(final List<String> messages) {
        GraphResult graphResult = null;
        int retryCount = 0;

        while (retryCount <= MAX_RETRIES) {
            try {
                graphResult = chatToGraphAgent.extractGraphJson(messages);
                break; // Success, exit retry loop
            } catch (RateLimitException | UndeclaredThrowableException e) {
                retryCount++;
                if (retryCount > MAX_RETRIES) {
                    log.error("Rate limit exceeded after {} retries. Giving up.", MAX_RETRIES, e);
                    throw new RuntimeException("Rate limit exceeded. Please try again later.", e);
                }
                log.warn("Rate limit exceeded. Retrying in {}ms (attempt {}/{})", RETRY_DELAY_MS, retryCount, MAX_RETRIES);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            } catch (Exception e) {
                // For non-rate-limit exceptions, don't retry
                log.error("Error extracting graph JSON", e);
                throw new RuntimeException("Failed to extract graph JSON", e);
            }
        }

        if (graphResult == null) {
            throw new RuntimeException("Failed to extract graph JSON after retries");
        }

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
