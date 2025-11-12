package com.discord.bot;

import com.discord.bot.feature_music.service.MusicService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan("com.discord.bot.properties")
public class BotApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(BotApplication.class, args);
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Discord Music Bot...");
            try {
                MusicService musicService = context.getBean(MusicService.class);
                musicService.shutdown();
                
                JDA jda = context.getBean(JDA.class);
                jda.shutdown();
                
                log.info("Discord Music Bot shutdown complete");
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }));
    }
}
