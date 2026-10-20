package com.example.rowin_adlproject.data

/**
 * Everything the signed-in account owns, captured as one immutable snapshot.
 *
 * The AI chat (practical #8) is grounded in this object rather than re-querying
 * a single table, so the stylist "remembers" the whole closet - clothing,
 * saved outfits and the profile - from the moment the session is loaded.
 *
 * [userId] is 0 while signed out, which is how [isLoaded] reports "no session".
 */
data class SessionMemory(
    val userId: Long = 0L,
    val profile: UserEntity? = null,
    val items: List<ClothingItemEntity> = emptyList(),
    val outfits: List<OutfitWithItems> = emptyList(),
) {
    val isLoaded: Boolean get() = userId != 0L

    fun itemById(id: Long): ClothingItemEntity? = items.firstOrNull { it.id == id }
}
