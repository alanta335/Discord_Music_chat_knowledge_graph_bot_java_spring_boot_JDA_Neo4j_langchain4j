package com.discord.bot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai")
public record OpenAIProperties(
        String ApiKey
) {
}
