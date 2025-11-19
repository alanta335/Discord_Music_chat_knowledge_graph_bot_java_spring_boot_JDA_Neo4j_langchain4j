package com.discord.bot.event_listener;

import com.discord.bot.feature_chat.ChatService;
import com.discord.bot.feature_music.service.GuildMusicManager;
import com.discord.bot.feature_music.service.MusicService;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageEventListener extends ListenerAdapter {
    private final AudioPlayerManager audioPlayerManager;
    private final MusicService musicService;
    private final ChatService chatService;
//    @Override
//    public void onMessageReceived(MessageReceivedEvent event) {
//        if (event.getAuthor().isBot()) return;
//
//        String content = event.getMessage().getContentRaw();
//        if (content.equalsIgnoreCase("!ping")) {
//            MessageChannel channel = event.getChannel();
//            channel.sendMessage("pong").queue();
//        }
//    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        try {
            String commandName = event.getName();
            log.info("Received slash command: {} from user: {}", commandName, event.getUser().getName());

            switch (commandName) {
                case "play" -> handlePlayCommand(event);
                case "skip" -> handleSkipCommand(event);
                case "stop" -> handleStopCommand(event);
                case "queue" -> handleQueueCommand(event);
                case "ping" -> handlePingCommand(event);
                case "ask" -> handleAskCommand(event);
                case "fetch" -> handleFetchCommand(event);
                default -> {
                    log.warn("Unknown command: {}", commandName);
                    event.reply("Unknown command: " + commandName).setEphemeral(true).queue();
                }
            }
        } catch (Exception e) {
            log.error("Error handling slash command: {}", event.getName(), e);
            event.reply("An error occurred while processing your command.").setEphemeral(true).queue();
        }
    }

    private void handleAskCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        final String answer = chatService.getAnswerToQuestion(event);
        event.getHook().sendMessage(answer).queue();
    }

    private void handlePlayCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command must be used in a guild.").setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            event.reply("Could not identify the member.").setEphemeral(true).queue();
            return;
        }

        VoiceChannel voiceChannel = member.getVoiceState() != null ?
                (VoiceChannel) member.getVoiceState().getChannel() : null;

        if (voiceChannel == null) {
            event.reply("You need to be in a voice channel to use this command.").setEphemeral(true).queue();
            return;
        }

        String query = event.getOption("song").getAsString();
        if (query.trim().isEmpty()) {
            event.reply("Please provide a song name or URL.").setEphemeral(true).queue();
            return;
        }

        // Set up music manager and connect to voice channel
        GuildMusicManager musicManager = musicService.getOrCreateMusicManager(guild);
        AudioManager audioManager = guild.getAudioManager();

        audioManager.setSendingHandler(musicManager.getSendHandler());
        audioManager.openAudioConnection(voiceChannel);

        // Load and play the track
        audioPlayerManager.loadItemOrdered(musicManager, query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.getScheduler().queue(track);
                event.reply("🎵 Playing: **" + track.getInfo().title + "**").queue();
                log.info("Track loaded and queued: {}", track.getInfo().title);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                int tracksAdded = 0;
                for (AudioTrack track : playlist.getTracks()) {
                    musicManager.getScheduler().queue(track);
                    tracksAdded++;
                }
                event.reply("📀 Playlist queued: **" + playlist.getName() + "** (" + tracksAdded + " tracks)").queue();
                log.info("Playlist loaded: {} with {} tracks", playlist.getName(), tracksAdded);
            }

            @Override
            public void noMatches() {
                event.reply("❌ No matches found for: " + query).queue();
                log.warn("No matches found for query: {}", query);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.reply("❌ Could not play: " + exception.getMessage()).queue();
                log.error("Failed to load track: {}", query, exception);
            }
        });
    }

    private void handleSkipCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command must be used in a guild.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager musicManager = musicService.getOrCreateMusicManager(guild);
        musicManager.getScheduler().nextTrack();
        event.reply("⏭️ Skipped to next track.").queue();
        log.info("Track skipped in guild: {}", guild.getName());
    }

    private void handleStopCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command must be used in a guild.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager musicManager = musicService.getOrCreateMusicManager(guild);
        musicManager.getScheduler().stop();
        guild.getAudioManager().closeAudioConnection();
        event.reply("⏹️ Stopped music and cleared queue.").queue();
        log.info("Music stopped in guild: {}", guild.getName());
    }

    private void handleQueueCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command must be used in a guild.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager musicManager = musicService.getOrCreateMusicManager(guild);
        String queueInfo = musicManager.getScheduler().getQueueInfo();
        event.reply(queueInfo).queue();
    }

    private void handlePingCommand(SlashCommandInteractionEvent event) {
        long gatewayPing = event.getJDA().getGatewayPing();
        event.reply("🏓 Pong! Gateway ping: " + gatewayPing + "ms").queue();
        log.info("Ping command executed, gateway ping: {}ms", gatewayPing);
    }

    private void handleFetchCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        try {
            event.getChannel();
            if (!event.getChannel().getType().isMessage()) {
                event.getHook().sendMessage("❌ This command can only be used in a text channel.").queue();
                return;
            }

            chatService.fetchAllMessages(event.getChannel().asTextChannel());
            event.getHook().sendMessage("✅ Successfully fetched all messages from this channel.").queue();
            log.info("Fetch command executed by user: {}", event.getUser().getName());
        } catch (Exception e) {
            log.error("Error fetching messages", e);
            event.getHook().sendMessage("❌ An error occurred while fetching messages.").queue();
        }
    }
}
