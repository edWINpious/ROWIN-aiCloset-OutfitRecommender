package com.example.rowin_adlproject.data

import androidx.room.TypeConverter

/**
 * Room enum converters: enums persist as their name string so the DB stays
 * human-readable and new enum values can be appended without migrations.
 */
class Converters {
    @TypeConverter fun categoryToString(value: Category?): String? = value?.name
    @TypeConverter fun stringToCategory(value: String?): Category? =
        value?.let { runCatching { Category.valueOf(it) }.getOrNull() }

    @TypeConverter fun seasonToString(value: Season?): String? = value?.name
    @TypeConverter fun stringToSeason(value: String?): Season? =
        value?.let { runCatching { Season.valueOf(it) }.getOrNull() }

    @TypeConverter fun occasionToString(value: Occasion?): String? = value?.name
    @TypeConverter fun stringToOccasion(value: String?): Occasion? =
        value?.let { runCatching { Occasion.valueOf(it) }.getOrNull() }
}
