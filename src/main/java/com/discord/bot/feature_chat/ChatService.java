package com.discord.bot.feature_chat;

import com.discord.bot.feature_knowledge_graph.service.KGService;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import opennlp.tools.util.StringUtil;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private static final int BATCH_SIZE = 100;
    private final KGService kgService;
    private final Neo4jText2CypherRetriever neo4jText2CypherRetriever;

    public void fetchAllMessages(TextChannel channel) {
        try {
            if (channel == null) {
                log.error("Channel not found");
                return;
            }

            log.info("Fetching messages from channel: {} ({})", channel.getName(), channel.getId());

            Message before = null;

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

                // Print and process messages immediately
                printBatch(batch);

                // Move pagination marker
                before = batch.getLast();
            }

            log.info("Finished fetching all messages from channel: {}", channel.getName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void printBatch(List<Message> batch) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        Set<String> info = new HashSet<>();

        for (Message msg : batch) {
            String timestamp = msg.getTimeCreated().format(formatter);
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
