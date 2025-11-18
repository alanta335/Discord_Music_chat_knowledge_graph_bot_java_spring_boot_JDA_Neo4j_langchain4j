package com.discord.bot.feature_chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelTrackingService {
    private final Neo4jClient neo4jClient;

    /**
     * Get the last processed message timestamp for a channel
     *
     * @param channelId The Discord channel ID
     * @return Optional containing the last processed timestamp, or empty if not found
     */
    public Optional<OffsetDateTime> getLastProcessedTimestamp(String channelId) {
        try {
            return neo4jClient.query("""
                            MATCH (c:Channel {id: $channelId})
                            RETURN c.lastProcessedTimestamp AS timestamp
                            """)
                    .bind(channelId).to("channelId")
                    .fetchAs(OffsetDateTime.class)
                    .mappedBy((typeSystem, record) -> {
                        Value timestamp = record.get("timestamp");
                        if (Objects.isNull(timestamp) || timestamp.isNull()) return null;
                        //If stored as string
                        try {
                            return OffsetDateTime.parse(timestamp.asString());
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .one();
        } catch (Exception e) {
            log.error("Error getting last processed timestamp for channel: {}", channelId, e);
            return Optional.empty();
        }
    }

    /**
     * Update the last processed message timestamp for a channel
     *
     * @param channelId The Discord channel ID
     * @param timestamp The timestamp of the last processed message
     */
    @Transactional
    public void updateLastProcessedTimestamp(String channelId, OffsetDateTime timestamp) {
        try {
            // Store as ISO-8601 string for compatibility
            String timestampStr = timestamp.toString();
            neo4jClient.query("""
                            MERGE (c:Channel {id: $channelId})
                            SET c.lastProcessedTimestamp = $timestamp
                            """)
                    .bind(channelId).to("channelId")
                    .bind(timestampStr).to("timestamp")
                    .run();

            log.debug("Updated last processed timestamp for channel {} to {}", channelId, timestamp);
        } catch (Exception e) {
            log.error("Error updating last processed timestamp for channel: {}", channelId, e);
            throw new RuntimeException("Failed to update channel timestamp", e);
        }
    }
}

