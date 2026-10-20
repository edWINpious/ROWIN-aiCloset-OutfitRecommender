package com.example.rowin_adlproject

import com.example.rowin_adlproject.data.AlertsEngine
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.Occasion
import com.example.rowin_adlproject.data.Season
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Tests for practical #10 (AI alerts) - dedupe keys must be stable between refreshes. */
class AlertsEngineTest {

    private val now = TimeUnit.DAYS.toMillis(400)

    private fun item(id: Long, ageDays: Long) = ClothingItemEntity(
        id = id,
        userId = 1,
        name = "Item $id",
        category = Category.TOPS,
        color = "Black",
        season = Season.ALL_SEASON,
        occasion = Occasion.CASUAL,
        createdAt = now - TimeUnit.DAYS.toMillis(ageDays),
    )

    @Test
    fun `unworn alert always names the oldest piece`() {
        val wardrobe = listOf(item(1, ageDays = 40), item(2, ageDays = 90), item(3, ageDays = 60))
        repeat(5) {
            val stale = AlertsEngine.compute(wardrobe, now).single { it.dedupeKey.startsWith("stale_") }
            assertEquals("stale_2", stale.dedupeKey)
        }
    }
}
