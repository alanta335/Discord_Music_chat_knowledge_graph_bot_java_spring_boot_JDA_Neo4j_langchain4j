package com.discord.bot.config;

import com.discord.bot.feature_knowledge_graph.agent.ChatToGraphAgent;
import com.discord.bot.feature_knowledge_graph.agent.FriendlyAnswerAgent;
import com.discord.bot.feature_knowledge_graph.agent.GraphToCypherQueryAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public ChatToGraphAgent chatToGraphAgent(ChatModel chatModel) {
        return AgenticServices
                .agentBuilder(ChatToGraphAgent.class)
                .chatModel(chatModel)
                .outputKey("chat-to-graph")
                .build();
    }

    @Bean
    public GraphToCypherQueryAgent graphToCypherQueryAgent(ChatModel chatModel) {
        return AgenticServices
                .agentBuilder(GraphToCypherQueryAgent.class)
                .chatModel(chatModel)
                .outputKey("graph-to-cypher")
                .build();
    }

    @Bean
    public FriendlyAnswerAgent friendlyAnswerAgent(ChatModel chatModel) {
        return AgenticServices
                .agentBuilder(FriendlyAnswerAgent.class)
                .chatModel(chatModel)
                .outputKey("friendly-answer")
                .build();
    }
}

