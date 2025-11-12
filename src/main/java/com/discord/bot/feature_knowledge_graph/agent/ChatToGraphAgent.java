package com.discord.bot.feature_knowledge_graph.agent;

import com.discord.bot.feature_knowledge_graph.domain.GraphResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface ChatToGraphAgent {

    @Agent("Parse a list of chat messages and output a graph in json format representation with nodes, relationships, labels, properties and paths. Do not output code")
    @UserMessage("""
                You will receive input as a **list of chat lines**, where each line is formatted like:
            
                    author | message
            
                Example list element:
                    "alanta335 | i like red color"
            
                Your tasks:
                1. Identify the primary author (they appear as the author in all lines).
                2. For each message line: do *not* output the full message text. Instead, extract meaningful facts or assumptions about the author (preferences, events, actions).
                3. Build a knowledge-graph representation including:
                   • Nodes: each node has an “id”, a “label”, and optionally “properties” if you have data.
                   • Relationships: each has “source” (node id), “target” (node id), and “label”.
                   • Labels: choose descriptive label names for nodes and relationships.
                   • Properties: include only non-null properties (omit unknowns).
                   • Paths: list meaningful paths from the author node through relationships to entity/event nodes (e.g., "author-1 -> LIKES_COLOR -> colorPref-1").
            
                Structure your output exactly as valid JSON with these top-level keys:
                {
                  "nodes": [ … ],
                  "relationships": [ … ],
                  "paths": [ … ]
                }
            
                Example input list:
                [
                  "alanta335 | i like red color",
                  "alanta335 | my favourite anime is death note",
                  "alanta335 | i am late for school"
                ]
            
                Example output:
                {
                  "nodes": [
                    {
                      "id": "author-1",
                      "label": "Author",
                      "properties": {
                        "username": "alanta335"
                      }
                    },
                    {
                      "id": "colorPref-1",
                      "label": "ColorPreference",
                      "properties": {
                        "color": "red"
                      }
                    },
                    {
                      "id": "animePref-1",
                      "label": "Anime",
                      "properties": {
                        "title": "Death Note"
                      }
                    },
                    {
                      "id": "event-1",
                      "label": "Event",
                      "properties": {
                        "description": "late for school"
                      }
                    }
                  ],
                  "relationships": [
                    {
                      "source": "author-1",
                      "target": "colorPref-1",
                      "label": "LIKES_COLOR"
                    },
                    {
                      "source": "author-1",
                      "target": "animePref-1",
                      "label": "FAVOURITE_ANIME"
                    },
                    {
                      "source": "author-1",
                      "target": "event-1",
                      "label": "EXPERIENCED_EVENT"
                    }
                  ],
                  "paths": [
                    "author-1 -> LIKES_COLOR -> colorPref-1",
                    "author-1 -> FAVOURITE_ANIME -> animePref-1",
                    "author-1 -> EXPERIENCED_EVENT -> event-1"
                  ]
                }
            
                Now parse the following list of messages and output accordingly.
                Messages:
                {{chatLines}}
            """)
    GraphResult extractGraphJson(@V("chatLines") List<String> chatLines);
}