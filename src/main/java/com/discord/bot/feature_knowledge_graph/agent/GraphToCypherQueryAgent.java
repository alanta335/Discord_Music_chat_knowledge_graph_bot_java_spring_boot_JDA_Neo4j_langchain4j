package com.discord.bot.feature_knowledge_graph.agent;

import com.discord.bot.feature_knowledge_graph.domain.GraphResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface GraphToCypherQueryAgent {
    @Agent("Convert a JSON knowledge graph into a list of Cypher commands to create/merge the nodes and relationships in a Neo4j database.")
    @UserMessage("""
                You will receive a GraphResult object containing `nodes`, `relationships`, and `paths`.
                Produce a JSON array (list) of Cypher statements as plain strings to create/merge those nodes and relationships.
                Rules:
                1. For each node emit a `MERGE` that identifies the node by an `id` property and includes its label and non-null properties.
                2. For each relationship emit a statement that `MATCH`es the source and target nodes by `id` and then `MERGE`s the relationship with its label and any non-null properties.
                3. Do not output any explanatory text — only a list of Cypher command strings.
                4. Omit properties that are null or unknown.
                Example statement forms:
                  MERGE (n:Label {id: 'node-id'}) SET n.prop = 'value'
                  MATCH (a {id: 'author-1'}), (b {id: 'colorPref-1'}) MERGE (a)-[:LIKES_COLOR]->(b)
                Now convert the following graph:
                {{jsonGraph}}
            """)
    List<String> convertJsonGraphToCypherCommands(@V("jsonGraph") GraphResult jsonGraph);
}
