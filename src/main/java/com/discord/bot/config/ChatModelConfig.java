package com.discord.bot.config;

import com.discord.bot.properties.OpenAIProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatModelConfig {
    private final OpenAIProperties openAIProperties;

    @Bean
    public ChatModel openAiClient() {
        return OpenAiChatModel.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .apiKey(openAIProperties.ApiKey())
                .modelName("meta-llama/llama-4-maverick-17b-128e-instruct")
                .temperature(0.0)
                .build();
    }
}
