package com.example.rowin_adlproject.ui.components

import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.Occasion
import com.example.rowin_adlproject.data.Season

/** Human-readable labels for the model enums, as used across the UI. */
fun Category.displayName(): String = when (this) {
    Category.TOPS -> "Tops"
    Category.BOTTOMS -> "Bottoms"
    Category.OUTERWEAR -> "Outerwear"
    Category.SHOES -> "Shoes"
    Category.ACCESSORIES -> "Accessories"
    Category.OTHER -> "Other"
}

fun Season.displayName(): String = when (this) {
    Season.SPRING -> "Spring"
    Season.SUMMER -> "Summer"
    Season.AUTUMN -> "Autumn"
    Season.WINTER -> "Winter"
    Season.ALL_SEASON -> "All Season"
}

fun Occasion.displayName(): String = when (this) {
    Occasion.CASUAL -> "Casual"
    Occasion.FORMAL -> "Formal"
    Occasion.WORK -> "Work"
    Occasion.PARTY -> "Party"
    Occasion.SPORTS -> "Sports"
    Occasion.OTHER -> "Other"
}
