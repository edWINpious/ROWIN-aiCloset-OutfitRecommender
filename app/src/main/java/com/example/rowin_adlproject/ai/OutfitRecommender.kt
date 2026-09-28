package com.example.rowin_adlproject.ai

import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.Occasion
import com.example.rowin_adlproject.data.Season
import org.json.JSONArray
import org.json.JSONObject

/**
 * Feature #21 - AI-Powered Outfit Recommendations.
 *
 * Primary path: the wardrobe is serialized and Groq proposes coherent outfits,
 * matched on color harmony, occasion and season.
 * Fallback path (no key / offline / API error): a deterministic local engine
 * builds the same shape from neutral-color rules, so the feature always works.
 */
class OutfitRecommender(private val client: GroqClient) {

    data class Suggestion(
        val name: String,
        val season: Season,
        val occasion: Occasion,
        val itemIds: List<Long>,
        val note: String,
        val fromAi: Boolean,
    )

    suspend fun recommend(
        wardrobe: List<ClothingItemEntity>,
        season: Season?,
        occasion: Occasion?,
        count: Int = 3,
    ): Result<List<Suggestion>> {
        if (wardrobe.size < MIN_WARDROBE_SIZE) {
            return Result.success(localRecommendations(wardrobe, season, occasion, count))
        }
        val wardrobeJson = JSONArray().apply {
            wardrobe.forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .put("name", item.name)
                        .put("category", item.category.name)
                        .put("color", item.color)
                        .put("season", item.season.name)
                        .put("occasion", item.occasion.name)
                        .put("pattern", item.pattern ?: "unknown")
                        .put("style", item.style ?: "unknown"),
                )
            }
        }
        val prompt = buildString {
            append("Wardrobe: ").append(wardrobeJson).append('\n')
            season?.let { append("Preferred season: ").append(it.name).append('\n') }
            occasion?.let { append("Preferred occasion: ").append(it.name).append('\n') }
            append("Propose $count complete outfits.")
        }
        return client.chat(SYSTEM_PROMPT, prompt)
            .mapCatching { reply -> parse(reply, wardrobe) }
            .recoverCatching { localRecommendations(wardrobe, season, occasion, count) }
    }

    internal fun parse(reply: String, wardrobe: List<ClothingItemEntity>): List<Suggestion> {
        val json = JSONObject(reply.substringAfter('{').let { reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1) })
        val outfits = json.optJSONArray("outfits") ?: return emptyList()
        val byId = wardrobe.associateBy { it.id }
        val suggestions = mutableListOf<Suggestion>()
        for (i in 0 until outfits.length()) {
            val o = outfits.getJSONObject(i)
            val ids = o.optJSONArray("itemIds") ?: continue
            val rawIds = (0 until ids.length())
                .mapNotNull { ids.optLong(it, -1L).takeIf { id -> id > 0 } }
            // Require every referenced id to exist - a hallucinated garment
            // invalidates the whole suggestion rather than partially matching.
            if (rawIds.isEmpty() || rawIds.any { !byId.containsKey(it) }) continue
            val validIds = rawIds
            suggestions += Suggestion(
                name = o.optString("name", "Outfit ${i + 1}"),
                season = o.optEnum<Season>("season") ?: Season.ALL_SEASON,
                occasion = o.optEnum<Occasion>("occasion") ?: Occasion.CASUAL,
                itemIds = validIds,
                note = o.optString("note", ""),
                fromAi = true,
            )
        }
        return suggestions
    }

    // ------------------------------------------------------------- fallback

    /**
     * Deterministic stylist: pick a top, a bottom, shoes and optionally
     * outerwear/accessories, preferring season/occasion matches and neutral
     * color harmony. Pure function - unit-testable without any network.
     */
    fun localRecommendations(
        wardrobe: List<ClothingItemEntity>,
        season: Season?,
        occasion: Occasion?,
        count: Int,
    ): List<Suggestion> {
        val tops = wardrobe.filter { it.category == Category.TOPS }
        val bottoms = wardrobe.filter { it.category == Category.BOTTOMS }
        val shoes = wardrobe.filter { it.category == Category.SHOES }
        val outer = wardrobe.filter { it.category == Category.OUTERWEAR }
        val accessories = wardrobe.filter { it.category == Category.ACCESSORIES }

        val suggestions = mutableListOf<Suggestion>()
        for (top in tops) {
            if (suggestions.size >= count) break
            val bottom = bottoms
                .filter { it.id != top.id }
                .maxByOrNull { scoreMatch(it, top, season, occasion) }
                ?.takeIf { scoreMatch(it, top, season, occasion) > 0 }
            val shoe = shoes
                .maxByOrNull { scoreMatch(it, top, season, occasion) }
                ?.takeIf { scoreMatch(it, top, season, occasion) > 0 }
            val jacket = outer
                .filter { it.season == top.season || it.season == Season.ALL_SEASON }
                .maxByOrNull { scoreMatch(it, top, season, occasion) }
            val accent = accessories
                .filter { it.season == top.season || it.season == Season.ALL_SEASON }
                .maxByOrNull { scoreMatch(it, top, season, occasion) }

            val items = buildList {
                add(top.id)
                bottom?.let { add(it.id) }
                shoe?.let { add(it.id) }
                jacket?.let { add(it.id) }
                accent?.let { add(it.id) }
            }
            if (items.size < 2) continue // need at least top + one more piece
            val name = "${top.color.replaceFirstChar { it.uppercase() }} ${top.name} Look"
                .take(40)
            suggestions += Suggestion(
                name = name,
                season = season ?: top.season,
                occasion = occasion ?: top.occasion,
                itemIds = items,
                note = "Matched from your closet by color and season.",
                fromAi = false,
            )
        }
        return suggestions
    }

    private fun scoreMatch(
        candidate: ClothingItemEntity,
        anchor: ClothingItemEntity,
        season: Season?,
        occasion: Occasion?,
    ): Int {
        var score = 0
        if (season == null || candidate.season == season || candidate.season == Season.ALL_SEASON) score += 2
        if (occasion == null || candidate.occasion == occasion || candidate.occasion == Occasion.OTHER) score += 2
        if (candidate.season == anchor.season) score += 1
        if (candidate.color.trim().equals(anchor.color.trim(), ignoreCase = true)) score -= 3
        // classic neutral pairing: dark bottoms + light tops and vice versa
        val candidateNeutral = NEUTRALS.any { candidate.color.contains(it, ignoreCase = true) }
        val anchorNeutral = NEUTRALS.any { anchor.color.contains(it, ignoreCase = true) }
        if (candidateNeutral && anchorNeutral) score += 2
        return score
    }

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String): T? {
        val raw = optString(key, "").trim().uppercase().replace(' ', '_')
        return enumValues<T>().firstOrNull { it.name == raw }
    }

    private companion object {
        const val MIN_WARDROBE_SIZE = 4
        val NEUTRALS = listOf("white", "black", "grey", "gray", "navy", "beige", "cream", "brown", "charcoal", "denim", "tan")

        const val SYSTEM_PROMPT = """
            You are ROWIN's AI stylist. You get a JSON wardrobe and optional season/
            occasion preferences. Reply with ONLY JSON:
            {
              "outfits": [
                {
                  "name": "<short outfit name>",
                  "season": "SPRING|SUMMER|AUTUMN|WINTER|ALL_SEASON",
                  "occasion": "CASUAL|FORMAL|WORK|PARTY|SPORTS|OTHER",
                  "itemIds": [<2-4 wardrobe ids that combine well>],
                  "note": "<one sentence on why this combination works>"
                }
              ]
            }
            Rules: match occasion and season; pair neutrals with one accent color;
            never reuse the same category twice in one outfit; only use given ids.
        """
    }
}
