package com.example.rowin_adlproject.data

/**
 * Clothing category. Stored in Room as the enum name (see Converters).
 */
enum class Category { TOPS, BOTTOMS, OUTERWEAR, SHOES, ACCESSORIES, OTHER }

/**
 * Season the item is meant for. ALL_SEASON covers year-round pieces.
 */
enum class Season { SPRING, SUMMER, AUTUMN, WINTER, ALL_SEASON }

/**
 * Occasion the item suits.
 */
enum class Occasion { CASUAL, FORMAL, WORK, PARTY, SPORTS, OTHER }

/**
 * Extra attributes the Groq vision model infers from a clothing photo
 * (feature #6 - AI Clothing Analysis). Kept separate from the user-editable
 * fields so re-analyzing never clobbers manual edits.
 */
data class AiAttributes(
    val category: Category? = null,
    val colorName: String? = null,
    val pattern: String? = null,
    val style: String? = null,
    val material: String? = null,
    val season: Season? = null,
    val occasion: Occasion? = null,
    val rawDescription: String? = null,
)
