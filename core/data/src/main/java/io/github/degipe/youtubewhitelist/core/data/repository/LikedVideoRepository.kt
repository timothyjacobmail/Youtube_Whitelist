package io.github.degipe.youtubewhitelist.core.data.repository

import io.github.degipe.youtubewhitelist.core.data.model.LibraryVideo
import kotlinx.coroutines.flow.Flow

interface LikedVideoRepository {
    fun getByProfile(profileId: String): Flow<List<LibraryVideo>>
    // Returns false when the video's original whitelisted source has been removed.
    suspend fun like(video: LibraryVideo): Boolean
    suspend fun remove(profileId: String, youtubeId: String)
}
