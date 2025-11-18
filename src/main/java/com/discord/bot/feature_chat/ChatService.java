package com.discord.bot.feature_chat;

import com.discord.bot.feature_chat.domain.MsgData;
import com.discord.bot.feature_chat.service.ChannelTrackingService;
import com.discord.bot.feature_knowledge_graph.service.KGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import opennlp.tools.util.StringUtil;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    public static final int KG_BATCH_SIZE = 20;
    private static final int BATCH_SIZE = 100;
    private final KGService kgService;
    private final ChannelTrackingService channelTrackingService;

    /**
     * Fetches all new messages from the provided TextChannel and processes them.
     * Uses pagination to retrieve messages in batches and updates the channel's
     * last-processed timestamp only if new messages were processed.
     */
    public void fetchAllMessages(final TextChannel channel) {
        if (channel == null) {
            log.error("Channel not found");
            return;
        }

        final String channelId = channel.getId();
        log.info("Fetching messages from channel: {} ({})", channel.getName(), channelId);

        try {
            final var optionalLastProcessed = channelTrackingService.getLastProcessedTimestamp(channelId);
            optionalLastProcessed.ifPresentOrElse(
                    ts -> log.info("Found last processed timestamp: {}. Only fetching messages after this time.", ts),
                    () -> log.info("No previous timestamp found. Fetching all messages from channel.")
            );

            OffsetDateTime latestProcessedTimestamp = null;
            Message before = null;

            while (true) {
                final List<Message> batch = fetchBatch(channel, before);
                if (batch.isEmpty()) {
                    log.info("Reached start of channel history (no more messages).");
                    break;
                }

                // Filter messages only newer than last processed timestamp (if present).
                final List<Message> messagesToProcess = filterNewMessages(batch, optionalLastProcessed);

                if (messagesToProcess.isEmpty()) {
                    log.info("All messages in this batch were already processed. Stopping fetch.");
                    break;
                }

                // Process messages and update the latestProcessedTimestamp if needed.
                final OffsetDateTime latestBatchProcessedTimestamp = processMessagesInBatch(messagesToProcess);
                if (latestBatchProcessedTimestamp != null && (latestProcessedTimestamp == null || latestBatchProcessedTimestamp.isAfter(latestProcessedTimestamp))) {
                    latestProcessedTimestamp = latestBatchProcessedTimestamp;
                }

                // Move pagination marker to the oldest message in the raw batch (not just the filtered list)
                before = batch.getLast();

                // If we have a last processed timestamp and the oldest message in this batch
                // is at or before that timestamp, further paging will only yield old messages.
                if (optionalLastProcessed.isPresent()) {
                    var cutoff = optionalLastProcessed.get();
                    Message oldestInBatch = batch.getLast();
                    if (!oldestInBatch.getTimeCreated().isAfter(cutoff)) {
                        log.info("Reached previously processed messages. Stopping fetch.");
                        break;
                    }
                }
            }

            if (latestProcessedTimestamp != null) {
                channelTrackingService.updateLastProcessedTimestamp(channelId, latestProcessedTimestamp);
                log.info("Updated last processed timestamp for channel {} to {}", channelId, latestProcessedTimestamp);
            } else {
                log.info("No new messages processed for channel {}. Timestamp unchanged.", channelId);
            }

            log.info("Finished fetching all messages from channel: {}", channel.getName());
        } catch (Exception e) {
            log.error("Error in fetchAllMessages", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetches a batch of messages using the proper JDA history calls.
     * If 'before' is null, retrieves the most recent messages; otherwise retrieves messages before 'before'.
     */
    private List<Message> fetchBatch(TextChannel channel, Message before) {
        try {
            final int size = BATCH_SIZE;
            if (before == null) {
                // retrievePast returns a List<Message>
                return channel.getHistory().retrievePast(size).complete();
            } else {
                // getHistoryBefore returns a MessageHistory - extract messages via getRetrievedHistory()
                var history = channel.getHistoryBefore(before, size).complete();
                return history.getRetrievedHistory();
            }
        } catch (Exception e) {
            log.error("Error fetching message batch for channel {}: {}", channel.getId(), e.getMessage(), e);
            // Return empty list to signal caller to stop; avoid throwing here to allow timestamp update logic to run.
            return List.of();
        }
    }

    /**
     * Returns only messages strictly after the optional lastProcessed timestamp.
     */
    private List<Message> filterNewMessages(final List<Message> batch, final Optional<OffsetDateTime> lastProcessedOpt) {
        if (batch.isEmpty() || lastProcessedOpt.isEmpty()) {
            return batch;
        }
        final var cutoff = lastProcessedOpt.get();
        return batch.stream()
                .filter(m -> m.getTimeCreated().isAfter(cutoff))
                .toList();
    }


    /**
     * Process a batch of messages and return the latest timestamp
     *
     * @param batch The batch of messages to process
     * @return The timestamp of the latest message in the batch, or null if batch is empty
     */
    private OffsetDateTime processMessagesInBatch(final List<Message> batch) {
        if (batch.isEmpty()) {
            return null;
        }

        final List<MsgData> extracted = batch.stream()
                .map(msg -> {
                    log.debug("message: {}", msg.toString());
                    return new MsgData(
                            msg.getAuthor().getName() + " | " + msg.getContentDisplay(),
                            msg.getTimeCreated(),
                            msg.getAuthor().getName(),
                            msg.getContentDisplay()
                    );
                })
                .toList();

        // Collect info set
        final Set<String> info = extracted.stream()
                .map(MsgData::key).collect(Collectors.toSet());

        // Latest timestamp
        final OffsetDateTime latestTimestamp = extracted.stream()
                .map(MsgData::ts)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        // Break the collected info into smaller sub-batches before sending to KG service
        final List<String> all = info.stream().toList();
        convertListToChunk(all)
                .forEach(subBatch -> {
                    try {
                        final var cypher = kgService.getJsonGraph(subBatch);
                        kgService.createKNGraph(cypher);
                    } catch (Exception e) {
                        log.error("Error generating KG for sub-batch", e);
                    }
                });

        return latestTimestamp;
    }

    private <T> Stream<List<T>> convertListToChunk(final List<T> list) {
        final int size = list.size();
        final int numberOfChunks = (size + ChatService.KG_BATCH_SIZE - 1) / ChatService.KG_BATCH_SIZE;

        return IntStream.range(0, numberOfChunks)
                .mapToObj(i -> list.subList(i * ChatService.KG_BATCH_SIZE,
                        Math.min((i + 1) * ChatService.KG_BATCH_SIZE, size)));
    }

    public String getAnswerToQuestion(SlashCommandInteractionEvent event) {
        final String question = event.getOption("question").getAsString();
        if (StringUtil.isEmpty(question)) {
            return "Please provide a question to ask.";
        }
        final String userName = event.getUser().getName();
        return kgService.searchAnswerFromGraph(question, userName);
    }
}
