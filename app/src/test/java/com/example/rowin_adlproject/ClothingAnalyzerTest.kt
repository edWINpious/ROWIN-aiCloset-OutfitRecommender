package com.example.rowin_adlproject

import com.example.rowin_adlproject.ai.ClothingAnalyzer
import com.example.rowin_adlproject.ai.GroqClient
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.Occasion
import com.example.rowin_adlproject.data.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for feature #6 (AI Clothing Analysis) JSON parsing - the Groq
 * reply mapping layer, which must be robust to fenced JSON and partial data.
 */
class ClothingAnalyzerTest {

    private val analyzer = ClothingAnalyzer(GroqClient(apiKey = "test-key"))

    @Test
    fun `parses a full valid reply`() {
        val reply = """
            {
              "category": "TOPS",
              "color": "Navy Blue",
              "pattern": "solid",
              "style": "casual",
              "material": "cotton",
              "season": "ALL_SEASON",
              "occasion": "CASUAL",
              "description": "A navy cotton t-shirt."
            }
        """.trimIndent()

        val attrs = analyzer.parse(reply)

        assertEquals(Category.TOPS, attrs.category)
        assertEquals("Navy Blue", attrs.colorName)
        assertEquals("solid", attrs.pattern)
        assertEquals("casual", attrs.style)
        assertEquals("cotton", attrs.material)
        assertEquals(Season.ALL_SEASON, attrs.season)
        assertEquals(Occasion.CASUAL, attrs.occasion)
        assertNotNull(attrs.rawDescription)
    }

    @Test
    fun `parses reply wrapped in markdown code fences`() {
        val reply = """
            ```json
            {"category": "SHOES", "color": "White", "season": "SUMMER", "occasion": "SPORTS"}
            ```
        """.trimIndent()

        val attrs = analyzer.parse(reply)

        assertEquals(Category.SHOES, attrs.category)
        assertEquals("White", attrs.colorName)
        assertEquals(Season.SUMMER, attrs.season)
        assertEquals(Occasion.SPORTS, attrs.occasion)
    }

    @Test
    fun `normalizes space-separated enum values`() {
        val reply = """{"season": "ALL SEASON", "occasion": "PARTY TIME"}"""
        val attrs = analyzer.parse(reply)
        assertEquals(Season.ALL_SEASON, attrs.season)
        // "PARTY TIME" clearly means the PARTY occasion
        assertEquals(Occasion.PARTY, attrs.occasion)
    }

    @Test
    fun `unrelated occasion text degrades to null`() {
        val reply = """{"occasion": "Business Casual"}"""
        val attrs = analyzer.parse(reply)
        // No enum shares a prefix with "BUSINESS_CASUAL" - do not guess
        assertNull(attrs.occasion)
    }

    @Test
    fun `unknown category falls back to null not crash`() {
        val reply = """{"category": "SPACESHIP", "color": "Red"}"""
        val attrs = analyzer.parse(reply)
        assertNull(attrs.category)
        assertEquals("Red", attrs.colorName)
    }

    @Test
    fun `non-json reply yields raw description only`() {
        val attrs = analyzer.parse("Sorry, I could not analyze that image.")
        assertNull(attrs.category)
        assertNull(attrs.colorName)
        assertTrue(attrs.rawDescription?.contains("Sorry") == true)
    }

    @Test
    fun `partial reply fills only provided fields`() {
        val reply = """{"color": "Black"}"""
        val attrs = analyzer.parse(reply)
        assertEquals("Black", attrs.colorName)
        assertNull(attrs.category)
        assertNull(attrs.season)
        assertNull(attrs.occasion)
    }
}
