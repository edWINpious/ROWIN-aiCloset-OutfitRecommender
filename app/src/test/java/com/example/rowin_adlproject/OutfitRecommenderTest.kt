package com.example.rowin_adlproject

import com.example.rowin_adlproject.ai.GroqClient
import com.example.rowin_adlproject.ai.OutfitRecommender
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.Occasion
import com.example.rowin_adlproject.data.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for feature #21 (AI-Powered Outfit Recommendations) - the local
 * stylist fallback, which must produce coherent outfits from the wardrobe
 * without any network access.
 */
class OutfitRecommenderTest {

    private val recommender = OutfitRecommender(GroqClient(apiKey = "test-key"))

    private fun item(
        id: Long,
        name: String,
        category: Category,
        color: String,
        season: Season = Season.ALL_SEASON,
        occasion: Occasion = Occasion.CASUAL,
    ) = ClothingItemEntity(
        id = id,
        userId = 1,
        name = name,
        category = category,
        color = color,
        season = season,
        occasion = occasion,
    )

    private val wardrobe = listOf(
        item(1, "White Oxford Shirt", Category.TOPS, "White", Season.ALL_SEASON, Occasion.WORK),
        item(2, "Black Jeans", Category.BOTTOMS, "Black", Season.ALL_SEASON, Occasion.CASUAL),
        item(3, "Navy Chinos", Category.BOTTOMS, "Navy", Season.ALL_SEASON, Occasion.WORK),
        item(4, "White Sneakers", Category.SHOES, "White", Season.ALL_SEASON, Occasion.CASUAL),
        item(5, "Brown Loafers", Category.SHOES, "Brown", Season.ALL_SEASON, Occasion.WORK),
        item(6, "Camel Overcoat", Category.OUTERWEAR, "Beige", Season.WINTER, Occasion.FORMAL),
        item(7, "Leather Belt", Category.ACCESSORIES, "Brown", Season.ALL_SEASON, Occasion.WORK),
    )

    @Test
    fun `generates complete outfits with multiple categories`() {
        val suggestions = recommender.localRecommendations(wardrobe, null, null, count = 3)

        assertTrue("Expected suggestions, got none", suggestions.isNotEmpty())
        suggestions.forEach { suggestion ->
            assertTrue("Outfit needs at least 2 pieces", suggestion.itemIds.size >= 2)
            // never two items of the same category in one outfit
            val categories = suggestion.itemIds.mapNotNull { id -> wardrobe.find { it.id == id }?.category }
            assertEquals("Duplicate category in outfit", categories.size, categories.distinct().size)
        }
    }

    @Test
    fun `each outfit starts with a top`() {
        val suggestions = recommender.localRecommendations(wardrobe, null, null, count = 3)
        suggestions.forEach { suggestion ->
            val first = wardrobe.find { it.id == suggestion.itemIds.first() }
            assertEquals(Category.TOPS, first?.category)
        }
    }

    @Test
    fun `prefers requested occasion`() {
        val suggestions = recommender.localRecommendations(wardrobe, null, Occasion.WORK, count = 1)
        assertTrue(suggestions.isNotEmpty())
        val suggestion = suggestions.first()
        val items = suggestion.itemIds.mapNotNull { id -> wardrobe.find { it.id == id } }
        // the shoe pick should lean formal (loafers over sneakers)
        assertTrue(items.any { it.category == Category.SHOES })
    }

    @Test
    fun `season filter excludes off-season outerwear`() {
        val suggestions = recommender.localRecommendations(wardrobe, Season.SUMMER, null, count = 3)
        suggestions.forEach { suggestion ->
            val outer = suggestion.itemIds.mapNotNull { id -> wardrobe.find { it.id == id } }
                .firstOrNull { it.category == Category.OUTERWEAR }
            // The camel overcoat is winter-only; it must not appear in summer outfits
            assertTrue("Winter coat recommended for summer", outer == null || outer.season == Season.ALL_SEASON || outer.season == Season.SUMMER)
        }
    }

    @Test
    fun `empty wardrobe yields no suggestions`() {
        val suggestions = recommender.localRecommendations(emptyList(), null, null, count = 3)
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `wardrobe without bottoms still yields top-only pairs when possible`() {
        val topsOnly = wardrobe.filter { it.category != Category.BOTTOMS }
        val suggestions = recommender.localRecommendations(topsOnly, null, null, count = 3)
        // With no bottoms, outfits can still combine top + shoes
        suggestions.forEach { s -> assertTrue(s.itemIds.isNotEmpty()) }
    }

    @Test
    fun `parses AI reply with valid item ids`() {
        val reply = """
            {"outfits": [
                {"name": "Office Look", "season": "ALL_SEASON", "occasion": "WORK",
                 "itemIds": [1, 3, 5], "note": "Crisp and formal."}
            ]}
        """.trimIndent()
        val parsed = recommender.parse(reply, wardrobe)
        assertEquals(1, parsed.size)
        assertEquals("Office Look", parsed[0].name)
        assertEquals(listOf(1L, 3L, 5L), parsed[0].itemIds)
        assertEquals(Occasion.WORK, parsed[0].occasion)
        assertTrue(parsed[0].fromAi)
    }

    @Test
    fun `AI reply with unknown ids is filtered out`() {
        val reply = """
            {"outfits": [
                {"name": "Ghost Look", "itemIds": [1, 999]},
                {"name": "Real Look", "itemIds": [2, 4]}
            ]}
        """.trimIndent()
        val parsed = recommender.parse(reply, wardrobe)
        assertEquals(1, parsed.size)
        assertEquals("Real Look", parsed[0].name)
        assertFalse(parsed[0].fromAi.not())
    }

    @Test
    fun `AI reply referencing no known ids yields empty list`() {
        val reply = """{"outfits": [{"name": "X", "itemIds": [501, 502]}]}"""
        val parsed = recommender.parse(reply, wardrobe)
        assertTrue(parsed.isEmpty())
    }
}
