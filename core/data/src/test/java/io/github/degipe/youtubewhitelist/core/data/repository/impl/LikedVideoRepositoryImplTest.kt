package io.github.degipe.youtubewhitelist.core.data.repository.impl

import com.google.common.truth.Truth.assertThat
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.data.model.LibraryVideo
import io.github.degipe.youtubewhitelist.core.database.dao.LikedVideoDao
import io.github.degipe.youtubewhitelist.core.database.dao.WhitelistItemDao
import io.github.degipe.youtubewhitelist.core.database.entity.WhitelistItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LikedVideoRepositoryImplTest {
    private val likedDao = mockk<LikedVideoDao>(relaxed = true)
    private val whitelistDao = mockk<WhitelistItemDao>()
    private val dispatcher = StandardTestDispatcher()
    private val repository = LikedVideoRepositoryImpl(likedDao, whitelistDao, dispatcher)
    private val video = LibraryVideo("kid", "video", "Video", "thumb", "Channel", "source", 100)
    private val source = WhitelistItemEntity("source-row", "kid", WhitelistItemType.CHANNEL, "source", "Channel", "thumb")

    @Test fun `likes resolve current profiles approved source`() = runTest(dispatcher) {
        coEvery { whitelistDao.findByYoutubeId("kid", "video") } returns null
        coEvery { whitelistDao.findByYoutubeId("kid", "source") } returns source
        assertThat(repository.like(video)).isTrue()
        coVerify { likedDao.insert(match { it.sourceWhitelistItemId == "source-row" && it.kidProfileId == "kid" }) }
    }
    @Test fun `removed source cannot become a liked video`() = runTest(dispatcher) {
        coEvery { whitelistDao.findByYoutubeId("kid", any()) } returns null
        assertThat(repository.like(video)).isFalse()
        coVerify(exactly = 0) { likedDao.insert(any()) }
    }
    @Test fun `another approved video cannot authorize this video`() = runTest(dispatcher) {
        coEvery { whitelistDao.findByYoutubeId("kid", "video") } returns null
        coEvery { whitelistDao.findByYoutubeId("kid", "source") } returns source.copy(type = WhitelistItemType.VIDEO)
        assertThat(repository.like(video)).isFalse()
        coVerify(exactly = 0) { likedDao.insert(any()) }
    }
}
