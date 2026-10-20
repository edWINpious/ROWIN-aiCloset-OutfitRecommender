package com.example.rowin_adlproject.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * ROWIN local database (feature #3 in the practical list - SQLite/Room).
 *
 * Room is the offline source of truth; [FirebaseSyncManager] mirrors it to
 * Cloud Firestore and streams remote changes back in.
 */
@Database(
    entities = [
        UserEntity::class,
        ClothingItemEntity::class,
        OutfitEntity::class,
        OutfitItemEntity::class,
        SyncTombstone::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class RowinDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun clothingDao(): ClothingDao
    abstract fun outfitDao(): OutfitDao
    abstract fun tombstoneDao(): TombstoneDao

    companion object {
        @Volatile
        private var instance: RowinDatabase? = null

        fun get(context: Context): RowinDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RowinDatabase::class.java,
                    "rowin.db",
                )
                    // v1 -> v2: users gain phone / location / avatarFileName.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN phone TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE users ADD COLUMN location TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE users ADD COLUMN avatarFileName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN remoteId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clothing_items_remoteId ON clothing_items(remoteId)")
            }
        }

        /**
         * v2 -> v3: Firestore moves from `users/{email}/items/{localRowId}` to
         * top-level `clothingItems` / `outfits` collections with generated ids.
         *
         * Old remote ids were local row numbers, which collide between phones
         * and accounts, so they are dropped and every row re-uploads with a
         * fresh id. Rows duplicated by the old push/snapshot race are merged
         * first - once their shared remote id is gone they could no longer be
         * told apart.
         */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                mergeDuplicateClothing(db)

                db.execSQL("ALTER TABLE users ADD COLUMN firebaseUid TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE users ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE users SET updatedAt = createdAt")

                db.execSQL("ALTER TABLE clothing_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE clothing_items SET updatedAt = createdAt, remoteId = NULL, synced = 0")

                db.execSQL("ALTER TABLE outfits ADD COLUMN remoteId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE outfits ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE outfits ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE outfits SET updatedAt = createdAt")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_outfits_remoteId ON outfits(remoteId)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_tombstones (" +
                        "remoteId TEXT NOT NULL, collection TEXT NOT NULL, userId INTEGER NOT NULL, " +
                        "PRIMARY KEY(remoteId))",
                )
            }
        }

        /** Keeps the oldest row per (userId, remoteId) and moves outfit links onto it. */
        private fun mergeDuplicateClothing(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val keepers = HashMap<String, Long>()
            val duplicates = mutableListOf<Pair<Long, Long>>() // duplicate id -> keeper id
            db.query(
                "SELECT id, userId, remoteId FROM clothing_items WHERE remoteId IS NOT NULL " +
                    "ORDER BY userId, remoteId, id",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val key = "${cursor.getLong(1)}|${cursor.getString(2)}"
                    val keeper = keepers[key]
                    if (keeper == null) keepers[key] = id else duplicates += id to keeper
                }
            }
            for ((duplicate, keeper) in duplicates) {
                // OR IGNORE: the outfit may already hold the keeper.
                db.execSQL(
                    "UPDATE OR IGNORE outfit_items SET clothingItemId = ? WHERE clothingItemId = ?",
                    arrayOf<Any>(keeper, duplicate),
                )
                db.execSQL("DELETE FROM outfit_items WHERE clothingItemId = ?", arrayOf<Any>(duplicate))
                db.execSQL("DELETE FROM clothing_items WHERE id = ?", arrayOf<Any>(duplicate))
            }
        }
    }
}
