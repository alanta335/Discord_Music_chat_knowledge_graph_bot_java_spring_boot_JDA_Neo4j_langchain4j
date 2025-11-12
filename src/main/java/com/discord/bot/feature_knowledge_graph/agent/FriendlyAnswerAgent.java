package com.discord.bot.feature_knowledge_graph.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Converts a raw agent/tool output into a short, friendly user-facing message.
 * Produces exactly one natural-language sentence (no code, JSON, or explanations).
 */
public interface FriendlyAnswerAgent {

    @Agent("Format a raw agent output into a friendly human reply.")
    @UserMessage("""
            You will receive three inputs:
            • userName: {{userName}}
            • question: {{question}}
            • rawOutput: {{rawOutput}}
            
            Your goal:
            Create one short, friendly, natural reply sentence for the user.
            
            Steps:
            1. Extract the username from `userName`.
            2. Understand what the user asked from `question` (formatted like "name : question").
            3. Extract the most likely answer from `rawOutput`:
               - Prefer the first text inside double quotes ("...").
               - If none, use the most meaningful word (e.g., blue, 42, Paris, etc.).
            4. Write a warm, concise response directly to the user:
               - Mention their name and, if relevant, the topic of their question.
               - Highlight the extracted answer with **bold**.
               - Optionally add one relevant emoji.
            5. If no clear answer is found, say:
               "Hey <username> — I couldn’t determine the answer from that result. Could you re-run or paste it again?"
            
            Output rules:
            • Output only one friendly human sentence — no JSON, code, or reasoning.
            • Avoid lists, explanations, or structured formatting.
            
            Example behaviors:
            
            Input:
            userName: alanta335
            question: "alanta335 : what is color I like?"
            rawOutput: "[DefaultContent { textSegment = TextSegment { text = \"red\" metadata = {} }, metadata = {} }]"
            → Output:
            "Hey alanta335 — I think the color you like is **red**. 🎨 Would you like me to remember that?"
            
            Input:
            userName: alice
            question: "alice : what color is my favourite?"
            rawOutput: "No quoted text here, but favorite=blue"
            → Output:
            "Hey alice — I think your favourite color is **blue**. 🎨"
            
            Input:
            userName: bob
            question: "bob : what do I like?"
            rawOutput: "completely unrelated text"
            → Output:
            "Hey bob — I couldn’t determine the answer from that result. Could you re-run or paste it again?"
            
             ⚠️ Output rule:
                • Return ONLY the final friendly reply sentence.
                • Do NOT explain your reasoning.
                • Do NOT show extraction steps, lists, or any intermediate text.
            """)
    String formatAnswer(@V("question") String question,
                        @V("rawOutput") String rawOutput,
                        @V("userName") String userName);
}