package com.discord.bot.feature_music.service;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService {
    private final AudioPlayerManager audioPlayerManager;
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();

    public GuildMusicManager getOrCreateMusicManager(Guild guild) {
        // computeIfAbsent ensures one manager per guild
        return musicManagers.computeIfAbsent(guild.getIdLong(), guildId -> {
            log.info("Creating new music manager for guild: {}", guild.getName());
            GuildMusicManager manager = new GuildMusicManager(audioPlayerManager);
            // set sending handler for this guild
            guild.getAudioManager().setSendingHandler(manager.getSendHandler());
            return manager;
        });
    }

    /**
     * Remove music manager for a guild (cleanup when bot leaves)
     */
    public void removeMusicManager(Guild guild) {
        GuildMusicManager manager = musicManagers.remove(guild.getIdLong());
        if (manager != null) {
            log.info("Removed music manager for guild: {}", guild.getName());
            // Clean up resources
            manager.getScheduler().stop();
        }
    }

    /**
     * Get music manager for a guild (returns null if not exists)
     */
    public GuildMusicManager getMusicManager(Guild guild) {
        return musicManagers.get(guild.getIdLong());
    }

    /**
     * Check if a guild has an active music manager
     */
    public boolean hasMusicManager(Guild guild) {
        return musicManagers.containsKey(guild.getIdLong());
    }

    /**
     * Get the number of active music managers
     */
    public int getActiveManagerCount() {
        return musicManagers.size();
    }

    /**
     * Clean up all music managers (shutdown)
     */
    public void shutdown() {
        log.info("Shutting down music service, cleaning up {} managers", musicManagers.size());
        musicManagers.values().forEach(manager -> manager.getScheduler().stop());
        musicManagers.clear();
    }
}

