package com.example.rowin_adlproject.ai

import com.example.rowin_adlproject.data.AiAttributes
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.Occasion
import com.example.rowin_adlproject.data.Season
import org.json.JSONObject

/**
 * Feature #6 - AI Clothing Analysis.
 * Sends the wardrobe photo to Groq vision and maps the JSON reply onto
 * [AiAttributes] (type/category, color, pattern, style, material, season,
 * occasion). Falls back to nulls per-field so partial replies still work.
 */
class ClothingAnalyzer(private val client: GroqClient) {

    suspend fun analyze(base64Jpeg: String): Result<AiAttributes> {
        val result = client.vision(SYSTEM_PROMPT, USER_PROMPT, base64Jpeg)
        return result.mapCatching { json -> parse(json) }
    }

    internal fun parse(reply: String): AiAttributes {
        val json = runCatching { JSONObject(extractJson(reply)) }.getOrNull()
            ?: return AiAttributes(rawDescription = reply.take(500))
        return AiAttributes(
            category = json.optEnum<Category>("category"),
            colorName = json.optStringOrNull("color"),
            pattern = json.optStringOrNull("pattern"),
            style = json.optStringOrNull("style"),
            material = json.optStringOrNull("material"),
            season = json.optEnum<Season>("season"),
            occasion = json.optEnum<Occasion>("occasion"),
            rawDescription = json.optStringOrNull("description"),
        )
    }

    /** Models sometimes wrap JSON in ```json fences; strip anything around it. */
    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val value = optString(key, "").trim()
        return value.ifEmpty { null }
    }

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String): T? {
        val raw = optString(key, "").trim().uppercase().replace(' ', '_')
        // Exact match first; then accept the model's prefix of a known value
        // (e.g. "ALL_SEA"). Never guess from unrelated words - null instead.
        return enumValues<T>().firstOrNull { it.name == raw }
            ?: if (raw.isBlank()) null
            else enumValues<T>().firstOrNull { raw.startsWith(it.name.take(5)) }?.takeIf { raw.length >= 4 }
    }

    private companion object {
        const val SYSTEM_PROMPT = """
            You are ROWIN's fashion analysis engine. You receive one photo of a single
            clothing item and reply with ONLY a JSON object, no prose, in this schema:
            {
              "category": "TOPS|BOTTOMS|OUTERWEAR|SHOES|ACCESSORIES|OTHER",
              "color": "<primary color name>",
              "pattern": "<solid|striped|checked|floral|printed|...>",
              "style": "<casual|formal|streetwear|vintage|...>",
              "material": "<cotton|denim|leather|wool|...>",
              "season": "SPRING|SUMMER|AUTUMN|WINTER|ALL_SEASON",
              "occasion": "CASUAL|FORMAL|WORK|PARTY|SPORTS|OTHER",
              "description": "<one short sentence describing the item>"
            }
            Choose the closest allowed value when unsure. Use "OTHER" over guessing.
        """
        const val USER_PROMPT = "Analyze this clothing item and reply with the JSON object."
    }
}
