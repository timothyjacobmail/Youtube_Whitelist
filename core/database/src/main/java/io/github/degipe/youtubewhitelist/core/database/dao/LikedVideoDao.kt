package io.github.degipe.youtubewhitelist.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.degipe.youtubewhitelist.core.database.entity.LikedVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedVideoDao {
    @Query("SELECT * FROM liked_videos WHERE kidProfileId = :profileId ORDER BY addedAt DESC, youtubeId ASC")
    fun getByProfile(profileId: String): Flow<List<LikedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(video: LikedVideoEntity)

    @Query("DELETE FROM liked_videos WHERE kidProfileId = :profileId AND youtubeId = :youtubeId")
    suspend fun remove(profileId: String, youtubeId: String)
}
