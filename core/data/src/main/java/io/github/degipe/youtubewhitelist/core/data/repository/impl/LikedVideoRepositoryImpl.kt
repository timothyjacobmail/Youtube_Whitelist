package io.github.degipe.youtubewhitelist.core.data.repository.impl

import io.github.degipe.youtubewhitelist.core.common.di.IoDispatcher
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.data.model.LibraryVideo
import io.github.degipe.youtubewhitelist.core.data.repository.LikedVideoRepository
import io.github.degipe.youtubewhitelist.core.database.dao.LikedVideoDao
import io.github.degipe.youtubewhitelist.core.database.dao.WhitelistItemDao
import io.github.degipe.youtubewhitelist.core.database.entity.LikedVideoEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LikedVideoRepositoryImpl @Inject constructor(
    private val likedVideoDao: LikedVideoDao,
    private val whitelistItemDao: WhitelistItemDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LikedVideoRepository {
    override fun getByProfile(profileId: String) = likedVideoDao.getByProfile(profileId).map { videos ->
        videos.map { LibraryVideo(it.kidProfileId, it.youtubeId, it.title, it.thumbnailUrl,
            it.channelTitle, it.sourceYoutubeId, it.addedAt) }
    }

    override suspend fun like(video: LibraryVideo): Boolean = withContext(ioDispatcher) {
        val direct = whitelistItemDao.findByYoutubeId(video.kidProfileId, video.youtubeId)
        val source = direct?.takeIf { it.type == WhitelistItemType.VIDEO }
            ?: whitelistItemDao.findByYoutubeId(video.kidProfileId, video.sourceYoutubeId)
            ?: return@withContext false
        if (source.type == WhitelistItemType.VIDEO && source.youtubeId != video.youtubeId) {
            return@withContext false
        }
        likedVideoDao.insert(LikedVideoEntity(video.kidProfileId, video.youtubeId, video.title,
            video.thumbnailUrl, video.channelTitle, source.id, source.youtubeId, video.addedAt))
        true
    }

    override suspend fun remove(profileId: String, youtubeId: String) = withContext(ioDispatcher) {
        likedVideoDao.remove(profileId, youtubeId)
    }
}
