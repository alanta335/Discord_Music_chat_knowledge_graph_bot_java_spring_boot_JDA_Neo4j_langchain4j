package com.discord.bot.config;

import com.discord.bot.event_listener.ChatMessageEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class JdaConfig {

    @Value("${discord.token}")
    private String token;

    private final ChatMessageEventListener chatMessageEventListener;

    @Bean
    public JDA jda() {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Discord token is not set. Set DISCORD_BOT_TOKEN env var or discord.token property.");
        }

        log.info("Initializing JDA with token: {}...", token.substring(0, Math.min(10, token.length())));

        final EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES,
                GatewayIntent.GUILD_MESSAGE_REACTIONS,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
                GatewayIntent.SCHEDULED_EVENTS,
                GatewayIntent.MESSAGE_CONTENT
        );

        try {
            JDABuilder builder = JDABuilder.createDefault(token)
                    .addEventListeners(chatMessageEventListener)
                    .setEnabledIntents(intents);

            JDA jda = builder.build();

            // Register slash commands
            registerSlashCommands(jda);

            log.info("JDA initialized successfully");
            return jda;
        } catch (Exception e) {
            log.error("Failed to initialize JDA", e);
            throw new RuntimeException("Failed to initialize Discord bot", e);
        }
    }

    private void registerSlashCommands(JDA jda) {
        try {
            CommandListUpdateAction commands = jda.updateCommands();
            commands.addCommands(
                    Commands.slash("play", "Play a song or add it to the queue")
                            .addOption(OptionType.STRING, "song", "Song name or URL", true),
                    Commands.slash("skip", "Skip the current song"),
                    Commands.slash("stop", "Stop the music and clear the queue"),
                    Commands.slash("queue", "Show the current queue"),
                    Commands.slash("ping", "Check if the bot is responding"),
                    Commands.slash("ask", "Ask a question to the bot")
                            .addOption(OptionType.STRING, "question", "Your question", true)
            );
            commands.queue(
                    success -> log.info("Successfully registered {} slash commands", success.size()),
                    error -> log.error("Failed to register slash commands", error)
            );
        } catch (Exception e) {
            log.error("Error registering slash commands", e);
        }
    }
}
