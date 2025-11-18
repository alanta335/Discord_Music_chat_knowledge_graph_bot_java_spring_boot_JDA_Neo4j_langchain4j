package com.discord.bot.feature_chat;

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
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private static final int BATCH_SIZE = 100;
    private final KGService kgService;
    private final ChannelTrackingService channelTrackingService;

    public void fetchAllMessages(TextChannel channel) {
        try {
            if (channel == null) {
                log.error("Channel not found");
                return;
            }

            String channelId = channel.getId();
            log.info("Fetching messages from channel: {} ({})", channel.getName(), channelId);

            // Get the last processed timestamp for this channel
            Optional<OffsetDateTime> lastProcessedTimestamp = channelTrackingService.getLastProcessedTimestamp(channelId);
            
            if (lastProcessedTimestamp.isPresent()) {
                log.info("Found last processed timestamp: {}. Only fetching messages after this time.", lastProcessedTimestamp.get());
            } else {
                log.info("No previous timestamp found. Fetching all messages from channel.");
            }

            Message before = null;
            OffsetDateTime latestProcessedTimestamp = null;

            // Loop until Discord returns an empty batch (no more messages)
            while (true) {
                List<Message> batch;
                try {
                    // Synchronously fetch up to 100 messages (JDA handles rate limits internally)
                    batch = (before == null)
                            ? channel.getHistory().retrievePast(BATCH_SIZE).complete()
                            : channel.getHistoryBefore(before, BATCH_SIZE).complete().getRetrievedHistory();
                } catch (Exception e) {
                    log.error("Error fetching messages: {}", e.getMessage(), e);
                    break;
                }

                if (batch.isEmpty()) {
                    log.info("Reached start of channel history.");
                    break;
                }

                // Filter messages to only process those after the last processed timestamp
                List<Message> messagesToProcess = batch;
                if (lastProcessedTimestamp.isPresent()) {
                    OffsetDateTime cutoffTime = lastProcessedTimestamp.get();
                    messagesToProcess = batch.stream()
                            .filter(msg -> msg.getTimeCreated().isAfter(cutoffTime))
                            .toList();
                    
                    if (messagesToProcess.isEmpty()) {
                        log.info("All messages in this batch were already processed. Stopping fetch.");
                        break;
                    }
                }

                // Process messages and track the latest timestamp
                OffsetDateTime batchLatestTimestamp = processBatch(messagesToProcess);
                if (batchLatestTimestamp != null) {
                    if (latestProcessedTimestamp == null || batchLatestTimestamp.isAfter(latestProcessedTimestamp)) {
                        latestProcessedTimestamp = batchLatestTimestamp;
                    }
                }

                // Move pagination marker
                before = batch.getLast();
                
                // If we've processed all new messages and reached the last processed timestamp, stop
                if (lastProcessedTimestamp.isPresent() && !messagesToProcess.isEmpty()) {
                    // Check if the oldest message in the batch is before or equal to the last processed timestamp
                    Message oldestInBatch = batch.getLast();
                    if (!oldestInBatch.getTimeCreated().isAfter(lastProcessedTimestamp.get())) {
                        log.info("Reached previously processed messages. Stopping fetch.");
                        break;
                    }
                }
            }

            // Update the last processed timestamp
            if (latestProcessedTimestamp != null) {
                channelTrackingService.updateLastProcessedTimestamp(channelId, latestProcessedTimestamp);
                log.info("Updated last processed timestamp for channel {} to {}", channelId, latestProcessedTimestamp);
            }

            log.info("Finished fetching all messages from channel: {}", channel.getName());
        } catch (Exception e) {
            log.error("Error in fetchAllMessages", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Process a batch of messages and return the latest timestamp
     * @param batch The batch of messages to process
     * @return The timestamp of the latest message in the batch, or null if batch is empty
     */
    private OffsetDateTime processBatch(List<Message> batch) {
        if (batch.isEmpty()) {
            return null;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Set<String> info = new HashSet<>();
        OffsetDateTime latestTimestamp = null;

        for (Message msg : batch) {
            OffsetDateTime msgTimestamp = msg.getTimeCreated();
            if (latestTimestamp == null || msgTimestamp.isAfter(latestTimestamp)) {
                latestTimestamp = msgTimestamp;
            }
            
            String timestamp = msgTimestamp.format(formatter);
            String author = msg.getAuthor().getName();
            String content = msg.getContentDisplay();
            // Print to console — could easily be replaced with writing to file or DB
            log.info("[{}] {} | {}", timestamp, author, content);
            info.add(author + " | " + content);
        }
        
        // Break the collected info into smaller sub-batches before sending to KG service
        final int KG_BATCH_SIZE = 20;
        List<String> all = info.stream().toList();
        for (int i = 0; i < all.size(); i += KG_BATCH_SIZE) {
            List<String> subBatch = all.subList(i, Math.min(i + KG_BATCH_SIZE, all.size()));
            try {
                List<String> cypherQueryList = kgService.getJsonGraph(subBatch);
                kgService.createKNGraph(cypherQueryList);
            } catch (Exception e) {
                log.error("Error generating KG for sub-batch: {}", e.getMessage(), e);
            }
        }
        
        return latestTimestamp;
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
