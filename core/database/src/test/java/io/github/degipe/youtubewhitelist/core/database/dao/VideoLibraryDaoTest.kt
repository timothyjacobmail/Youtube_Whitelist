package io.github.degipe.youtubewhitelist.core.database.dao

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.database.MIGRATION_3_4
import io.github.degipe.youtubewhitelist.core.database.YouTubeWhitelistDatabase
import io.github.degipe.youtubewhitelist.core.database.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoLibraryDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: YouTubeWhitelistDatabase
    private val source = WhitelistItemEntity("source-1", "kid-1", WhitelistItemType.CHANNEL,
        "UC123", "Channel", "thumb")
    private val liked = LikedVideoEntity("kid-1", "video", "Video", "thumb", "Channel",
        "source-1", "UC123", 100)
    private val offline = OfflineVideoEntity("kid-1", "video", "Video", "thumb", "Channel",
        "source-1", "UC123", 100, "test-file", null, "queued", 0, 0, null)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, YouTubeWhitelistDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun teardown() { db.close(); context.deleteDatabase("library-migration-test.db") }

    private suspend fun seed() {
        db.parentAccountDao().insert(ParentAccountEntity("parent", "google", "email", "hash"))
        db.kidProfileDao().insert(KidProfileEntity("kid-1", "parent", "First"))
        db.kidProfileDao().insert(KidProfileEntity("kid-2", "parent", "Second"))
        db.whitelistItemDao().insert(source)
        db.whitelistItemDao().insert(source.copy(id = "source-2", kidProfileId = "kid-2"))
    }

    @Test fun `likes are unique and separate from offline copies`() = runTest {
        seed()
        db.likedVideoDao().insert(liked)
        db.likedVideoDao().insert(liked.copy(addedAt = 200))
        assertThat(db.likedVideoDao().getByProfile("kid-1").first()).containsExactly(liked)
        assertThat(db.offlineVideoDao().getByProfile("kid-1").first()).isEmpty()
        db.offlineVideoDao().insert(offline)
        db.offlineVideoDao().remove("kid-1", "video")
        assertThat(db.likedVideoDao().getByProfile("kid-1").first()).containsExactly(liked)
    }

    @Test fun `likes and offline deletes are scoped to each profile`() = runTest {
        seed()
        db.likedVideoDao().insert(liked)
        db.likedVideoDao().insert(liked.copy(kidProfileId = "kid-2", sourceWhitelistItemId = "source-2"))
        db.offlineVideoDao().insert(offline)
        db.offlineVideoDao().insert(offline.copy(kidProfileId = "kid-2", sourceWhitelistItemId = "source-2"))
        db.likedVideoDao().remove("kid-1", "video")
        db.offlineVideoDao().remove("kid-1", "video")
        assertThat(db.likedVideoDao().getByProfile("kid-2").first()).hasSize(1)
        assertThat(db.offlineVideoDao().getByProfile("kid-2").first()).hasSize(1)
    }

    @Test fun `removing approved source removes likes and offline access`() = runTest {
        seed()
        db.likedVideoDao().insert(liked)
        db.offlineVideoDao().insert(offline)
        db.whitelistItemDao().delete(source)
        assertThat(db.likedVideoDao().getByProfile("kid-1").first()).isEmpty()
        assertThat(db.offlineVideoDao().getByProfile("kid-1").first()).isEmpty()
    }

    @Test fun `stale worker cannot complete a replacement download`() = runTest {
        seed()
        db.offlineVideoDao().insert(offline.copy(fileName = "replacement"))
        db.offlineVideoDao().complete("kid-1", "video", "old-token", null, 123)
        assertThat(db.offlineVideoDao().get("kid-1", "video")!!.state).isEqualTo("queued")
        db.offlineVideoDao().complete("kid-1", "video", "replacement", "audio", 456)
        val ready = db.offlineVideoDao().get("kid-1", "video")!!
        assertThat(ready.state).isEqualTo("ready")
        assertThat(ready.audioFileName).isEqualTo("audio")
        assertThat(ready.expiresAt).isEqualTo(456)
    }

    @Test fun `version three upgrade preserves profiles whitelist and channel cache`() = runTest {
        db.close()
        db = Room.databaseBuilder(context, YouTubeWhitelistDatabase::class.java, "library-migration-test.db")
            .allowMainThreadQueries().build()
        seed()
        db.cachedChannelVideoDao().upsertAll(listOf(CachedChannelVideoEntity("UC123", "video", "Video", "thumb", "Channel", 0)))
        db.close()
        // Restore the exact previous schema: all v3 tables, without either v4 library table.
        SQLiteDatabase.openDatabase(context.getDatabasePath("library-migration-test.db").path,
            null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE liked_videos")
            it.execSQL("DROP TABLE offline_videos")
            it.version = 3
        }
        db = Room.databaseBuilder(context, YouTubeWhitelistDatabase::class.java, "library-migration-test.db")
            .addMigrations(MIGRATION_3_4).allowMainThreadQueries().build()
        assertThat(db.kidProfileDao().getProfileByIdOnce("kid-1")!!.name).isEqualTo("First")
        assertThat(db.whitelistItemDao().findByYoutubeId("kid-1", "UC123")).isEqualTo(source)
        assertThat(db.cachedChannelVideoDao().getVideosByChannel("UC123").first()).hasSize(1)
        db.likedVideoDao().insert(liked)
        db.offlineVideoDao().insert(offline)
        assertThat(db.likedVideoDao().getByProfile("kid-1").first()).hasSize(1)
        assertThat(db.offlineVideoDao().getByProfile("kid-1").first()).hasSize(1)
    }
}
