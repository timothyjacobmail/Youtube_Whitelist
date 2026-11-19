package io.github.degipe.youtubewhitelist.core.data.repository

import io.github.degipe.youtubewhitelist.core.data.model.LibraryVideo
import io.github.degipe.youtubewhitelist.core.data.model.OfflineVideo
import kotlinx.coroutines.flow.Flow

interface OfflineVideoRepository {
    fun getByProfile(profileId: String): Flow<List<OfflineVideo>>
    suspend fun download(video: LibraryVideo): Boolean
    suspend fun remove(profileId: String, youtubeId: String)
}
