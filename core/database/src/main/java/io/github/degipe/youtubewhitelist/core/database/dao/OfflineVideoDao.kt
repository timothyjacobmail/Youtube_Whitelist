package io.github.degipe.youtubewhitelist.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.degipe.youtubewhitelist.core.database.entity.OfflineVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineVideoDao {
    @Query("SELECT * FROM offline_videos WHERE kidProfileId = :profileId ORDER BY addedAt DESC, youtubeId ASC")
    fun getByProfile(profileId: String): Flow<List<OfflineVideoEntity>>
    @Query("SELECT * FROM offline_videos")
    suspend fun getAll(): List<OfflineVideoEntity>
    @Query("SELECT * FROM offline_videos WHERE kidProfileId = :profileId AND youtubeId = :youtubeId")
    suspend fun get(profileId: String, youtubeId: String): OfflineVideoEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: OfflineVideoEntity)
    @Query("UPDATE offline_videos SET state = :state, progress = :progress, error = :error WHERE kidProfileId = :profileId AND youtubeId = :youtubeId AND fileName = :fileName")
    suspend fun updateState(profileId: String, youtubeId: String, fileName: String, state: String, progress: Int, error: String?)
    @Query("UPDATE offline_videos SET state = 'ready', progress = 100, expiresAt = :expiresAt, audioFileName = :audioFileName, error = NULL WHERE kidProfileId = :profileId AND youtubeId = :youtubeId AND fileName = :fileName")
    suspend fun complete(profileId: String, youtubeId: String, fileName: String, audioFileName: String?, expiresAt: Long)
    @Query("DELETE FROM offline_videos WHERE kidProfileId = :profileId AND youtubeId = :youtubeId")
    suspend fun remove(profileId: String, youtubeId: String)
}
