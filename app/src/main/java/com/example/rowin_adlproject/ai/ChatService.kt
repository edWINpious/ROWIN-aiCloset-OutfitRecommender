package com.example.rowin_adlproject.ai

import com.example.rowin_adlproject.data.ChatMessage
import org.json.JSONObject

/**
 * Practical #8 - AI chatbot service.
 *
 * Multi-turn stylist chat on Groq. The conversation is stateless per call:
 * the full message history is serialized into the prompt, and the wardrobe
 * summary is attached so answers are grounded in the user's real closet.
 */
class ChatService(private val client: GroqClient = GroqClient()) {

    /**
     * Sends the conversation and returns the stylist's reply.
     * Falls back to a local heuristic reply when the API is unreachable
     * (no key / offline), so the chatbot always answers.
     */
    suspend fun reply(history: List<ChatMessage>, wardrobe: List<com.example.rowin_adlproject.data.ClothingItemEntity>): Result<String> {
        if (history.none { it.role == "user" }) {
            return Result.success("Tell me the occasion and I'll style you from your closet.")
        }
        val wardrobeSummary = buildString {
            append("Wardrobe (${wardrobe.size} items): ")
            append(
                wardrobe.take(60).joinToString("; ") {
                    "${it.id}:${it.name} [${it.category.name.lowercase()}, ${it.color}, ${it.season.name.lowercase().replace("_", " ")}]"
                },
            )
        }
        val transcript = history.joinToString("\n") { "${it.role.uppercase()}: ${it.text}" }
        val prompt = "$wardrobeSummary\n\nConversation so far:\n$transcript\n\nASSISTANT:"
        return client.chat(SYSTEM_PROMPT, prompt, jsonMode = false)
            .mapCatching { it.trim() }
            .recoverCatching { localFallback(history.last().text, wardrobe) }
    }

    /** Deterministic offline stylist used when Groq is unreachable. */
    private fun localFallback(question: String, wardrobe: List<com.example.rowin_adlproject.data.ClothingItemEntity>): String {
        val tops = wardrobe.filter { it.category == com.example.rowin_adlproject.data.Category.TOPS }
        val bottoms = wardrobe.filter { it.category == com.example.rowin_adlproject.data.Category.BOTTOMS }
        val shoes = wardrobe.filter { it.category == com.example.rowin_adlproject.data.Category.SHOES }
        return buildString {
            append("I'm offline right now, so here's a quick local take: ")
            if (tops.isNotEmpty() && bottoms.isNotEmpty()) {
                append("try your ${tops.first().name} with ${bottoms.first().name}")
                if (shoes.isNotEmpty()) append(" and ${shoes.first().name}")
                append(". ")
            } else {
                append("add a few tops and bottoms and I can start combining them. ")
            }
            append("For \"$question\", neutral colors with one accent piece is a safe bet.")
        }
    }

    private companion object {
        const val SYSTEM_PROMPT = """
            You are ROWIN's in-app AI stylist chatbot. You get a JSON-ish wardrobe
            summary (id:name [category, color, season]) and a chat transcript.
            Answer the user's last message in 2-5 short sentences. Reference real
            items by name when suggesting outfits, give practical styling advice,
            and never invent wardrobe items that are not in the summary. If asked
            something unrelated to fashion, answer briefly and steer back to style.
        """
    }
}
