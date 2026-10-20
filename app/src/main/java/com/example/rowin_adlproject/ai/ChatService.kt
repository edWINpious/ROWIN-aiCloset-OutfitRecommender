package com.example.rowin_adlproject.ai

import com.example.rowin_adlproject.data.ChatMessage
import com.example.rowin_adlproject.data.SessionMemory

/**
 * Practical #8 - AI chatbot service.
 *
 * Multi-turn stylist chat on Groq. The conversation is stateless per call:
 * the full message history is serialized into the prompt together with the
 * signed-in account's [SessionMemory] (profile + every clothing item + every
 * saved outfit), so the stylist reasons over everything the user owns the
 * moment the session loads.
 *
 * Scope: fashion only. Clearly off-topic questions are refused locally so the
 * chatbot never drifts into coding or general knowledge, even when the API is
 * unreachable.
 */
class ChatService(private val client: GroqClient = GroqClient()) {

    /**
     * Sends the conversation and returns the stylist's reply.
     * Falls back to a local heuristic reply when the API is unreachable
     * (no key / offline), so the chatbot always answers.
     */
    suspend fun reply(history: List<ChatMessage>, memory: SessionMemory): Result<String> {
        if (history.none { it.role == "user" }) {
            return Result.success("Tell me the occasion and I'll style you from your closet.")
        }
        val question = history.last().text
        offTopicReply(question)?.let { return Result.success(it) }

        val transcript = history.joinToString("\n") { "${it.role.uppercase()}: ${it.text}" }
        val prompt = "${memorySummary(memory)}\n\nConversation so far:\n$transcript\n\nASSISTANT:"
        return client.chat(SYSTEM_PROMPT, prompt, jsonMode = false)
            .mapCatching { it.trim() }
            .recoverCatching { offTopicReply(question) ?: localFallback(question, memory) }
    }

    /**
     * Everything the account owns, flattened into the prompt: profile, the
     * full closet, and each saved outfit with the pieces inside it.
     */
    private fun memorySummary(memory: SessionMemory): String = buildString {
        append("USER PROFILE: ")
        val profile = memory.profile
        append(
            if (profile == null) {
                "name unknown"
            } else {
                listOfNotNull(
                    profile.name.ifBlank { null },
                    profile.location?.takeIf { it.isNotBlank() },
                    profile.age.takeIf { it > 0 }?.let { "$it years old" },
                ).joinToString(", ").ifBlank { "name unknown" }
            },
        )
        append('\n')

        append("WARDROBE (${memory.items.size} items): ")
        append(
            memory.items.take(MAX_ITEMS).joinToString("; ") {
                buildString {
                    append(it.id).append(':').append(it.name)
                    append(" [").append(it.category.name.lowercase())
                    append(", ").append(it.color)
                    append(", ").append(it.season.name.lowercase().replace("_", " "))
                    append(", ").append(it.occasion.name.lowercase())
                    it.pattern?.takeIf(String::isNotBlank)?.let { p -> append(", ").append(p) }
                    it.style?.takeIf(String::isNotBlank)?.let { s -> append(", ").append(s) }
                    append(']')
                }
            },
        )
        if (memory.items.size > MAX_ITEMS) {
            append(" (plus ${memory.items.size - MAX_ITEMS} more)")
        }
        append('\n')

        append("SAVED OUTFITS (${memory.outfits.size}): ")
        append(
            memory.outfits.take(MAX_OUTFITS).joinToString("; ") { outfit ->
                val pieces = outfit.items.joinToString(" + ") { it.name }
                "${outfit.outfit.name} [${outfit.outfit.season.name.lowercase().replace("_", " ")}" +
                    "/${outfit.outfit.occasion.name.lowercase()}]: $pieces"
            },
        )
        if (memory.outfits.isEmpty()) append("none saved yet")
    }

    /**
     * Local fashion-only guard. Returns a canned refusal when the last message
     * is obviously not about clothing, and null when it should reach the model.
     */
    private fun offTopicReply(question: String): String? =
        if (OFF_TOPIC.containsMatchIn(question)) {
            "I'm ROWIN's style assistant, so I only help with clothing, outfits and " +
                "your wardrobe. Tell me what you're dressing for and I'll put a look " +
                "together from your closet."
        } else {
            null
        }

    /** Deterministic offline stylist used when Groq is unreachable. */
    private fun localFallback(question: String, memory: SessionMemory): String {
        val tops = memory.items.filter { it.category == com.example.rowin_adlproject.data.Category.TOPS }
        val bottoms = memory.items.filter { it.category == com.example.rowin_adlproject.data.Category.BOTTOMS }
        val shoes = memory.items.filter { it.category == com.example.rowin_adlproject.data.Category.SHOES }
        if (memory.items.isEmpty()) {
            return "Your wardrobe is still empty - add a few pieces in the closet and " +
                "I'll start building looks for \"$question\"."
        }
        return buildString {
            append("Here's my take on \"$question\": ")
            if (tops.isNotEmpty() && bottoms.isNotEmpty()) {
                append("try your ${tops.first().name} with ${bottoms.first().name}")
                if (shoes.isNotEmpty()) append(" and ${shoes.first().name}")
                append(". ")
            } else {
                append("start with the pieces you already have and build around one anchor item. ")
            }
            append("Neutral colors with one accent piece is a safe bet.")
        }
    }

    private companion object {
        const val MAX_ITEMS = 80
        const val MAX_OUTFITS = 40

        /** Catches obviously non-fashion asks before they ever reach the model. */
        val OFF_TOPIC = Regex(
            "(?i)\\b(" +
                "code|coding|programming|programmer|developer|software|compiler|debug|" +
                "python|javascript|typescript|java|kotlin|swift|golang|rust|c\\+\\+|sql|regex|" +
                "binary[ -]?tree|linked[ -]?list|algorithm|datastructure|data structure|" +
                "stack|queue|function|loop|array|variable|syntax|stack[ -]?overflow|" +
                "math|equation|algebra|calculus|physics|chemistry|biology|history|politics|" +
                "geography|physics|homework|essay|poem|lyrics|stock|crypto|bitcoin|" +
                "recipe|cook|chess|sports score|joke about|write a script" +
                ")\\b",
        )

        const val SYSTEM_PROMPT = """
            You are ROWIN's in-app AI stylist, a personal fashion and wardrobe expert.

            TOPIC SCOPE - strict:
            You answer ONLY questions about fashion, clothing, outfits, styling, wardrobes,
            colors, fabrics, fits, seasons, occasions, shopping for clothes, and personal
            appearance. If the user asks about anything else - programming, code, math,
            science, politics, history, news, opinions, or general knowledge - you must
            refuse in one short sentence saying you only help with style and clothing, then
            ask what they would like to wear. Never answer the off-topic question, not even
            briefly, and never produce code, pseudocode or technical explanations.

            ANSWERING:
            You receive the user's full profile, their entire wardrobe and their saved
            outfits, followed by the conversation so far. Answer the last message in 2-5
            short sentences. Always reference real items by the exact name they appear under
            in the wardrobe, and never invent items that are not listed. If the wardrobe or
            an outfit is empty, say so plainly instead of pretending pieces exist.
        """
    }
}
