package com.discord.bot.feature_knowledge_graph.domain;

import java.util.Map;

// Node
public record GraphNode(
        String id,
        String label,
        Map<String, Object> properties
) {
}
