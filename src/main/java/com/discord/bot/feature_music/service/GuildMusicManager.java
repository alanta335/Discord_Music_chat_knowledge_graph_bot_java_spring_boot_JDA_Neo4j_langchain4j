package com.discord.bot.feature_music.service;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;


public class GuildMusicManager {
    private final AudioPlayer player;
    private final TrackScheduler scheduler;
    private final LavaPlayerSendHandler sendHandler;

    public GuildMusicManager(AudioPlayerManager manager) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(player);
        this.player.addListener(scheduler);
        this.sendHandler = new LavaPlayerSendHandler(player);
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public net.dv8tion.jda.api.audio.AudioSendHandler getSendHandler() {
        return sendHandler;
    }
}

