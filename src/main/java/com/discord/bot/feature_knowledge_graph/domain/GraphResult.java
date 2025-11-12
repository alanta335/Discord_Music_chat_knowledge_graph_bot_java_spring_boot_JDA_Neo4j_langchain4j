package com.discord.bot.feature_knowledge_graph.domain;

import java.util.List;
import java.util.Map;

// The overall result from the agent
public record GraphResult(
        List<GraphNode> nodes,
        List<GraphRelationship> relationships,
        List<String> paths
) {
}
