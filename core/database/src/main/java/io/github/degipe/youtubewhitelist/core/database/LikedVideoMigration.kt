package io.github.degipe.youtubewhitelist.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS liked_videos (
                kidProfileId TEXT NOT NULL,
                youtubeId TEXT NOT NULL,
                title TEXT NOT NULL,
                thumbnailUrl TEXT NOT NULL,
                channelTitle TEXT,
                sourceWhitelistItemId TEXT NOT NULL,
                sourceYoutubeId TEXT NOT NULL,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(kidProfileId, youtubeId),
                FOREIGN KEY(kidProfileId) REFERENCES kid_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(sourceWhitelistItemId) REFERENCES whitelist_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_liked_videos_sourceWhitelistItemId ON liked_videos(sourceWhitelistItemId)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS offline_videos (
                kidProfileId TEXT NOT NULL, youtubeId TEXT NOT NULL,
                title TEXT NOT NULL, thumbnailUrl TEXT NOT NULL, channelTitle TEXT,
                sourceWhitelistItemId TEXT NOT NULL, sourceYoutubeId TEXT NOT NULL,
                addedAt INTEGER NOT NULL, fileName TEXT NOT NULL, audioFileName TEXT,
                state TEXT NOT NULL, progress INTEGER NOT NULL, expiresAt INTEGER NOT NULL, error TEXT,
                PRIMARY KEY(kidProfileId, youtubeId),
                FOREIGN KEY(kidProfileId) REFERENCES kid_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(sourceWhitelistItemId) REFERENCES whitelist_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_videos_sourceWhitelistItemId ON offline_videos(sourceWhitelistItemId)")
    }
}
