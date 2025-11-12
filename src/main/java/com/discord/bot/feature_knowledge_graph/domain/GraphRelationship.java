package com.discord.bot.feature_knowledge_graph.domain;

// Relationship
public record GraphRelationship(
        String source,
        String target,
        String label
) {
}
