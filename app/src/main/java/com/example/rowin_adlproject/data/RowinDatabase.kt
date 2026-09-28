package com.example.rowin_adlproject.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * ROWIN local database (feature #3 in the practical list - SQLite/Room).
 *
 * Firebase note: to add cloud sync, keep Room as the offline source of truth
 * and mirror writes into Firestore from WardrobeRepository (see TODOs there).
 */
@Database(
    entities = [
        UserEntity::class,
        ClothingItemEntity::class,
        OutfitEntity::class,
        OutfitItemEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class RowinDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun clothingDao(): ClothingDao
    abstract fun outfitDao(): OutfitDao

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
                    .addMigrations(MIGRATION_1_2)
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
    }
}
