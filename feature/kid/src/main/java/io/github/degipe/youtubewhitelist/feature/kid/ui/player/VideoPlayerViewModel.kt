package io.github.degipe.youtubewhitelist.feature.kid.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import io.github.degipe.youtubewhitelist.core.data.model.LibraryVideo
import io.github.degipe.youtubewhitelist.core.data.model.OfflineVideo
import io.github.degipe.youtubewhitelist.core.data.repository.OfflineVideoRepository
import io.github.degipe.youtubewhitelist.core.data.repository.LikedVideoRepository
import io.github.degipe.youtubewhitelist.core.data.repository.WatchHistoryRepository
import io.github.degipe.youtubewhitelist.core.data.repository.WhitelistRepository
import io.github.degipe.youtubewhitelist.core.data.sleep.SleepTimerManager
import io.github.degipe.youtubewhitelist.core.data.sleep.SleepTimerStatus
import io.github.degipe.youtubewhitelist.core.data.timelimit.TimeLimitChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoPlayerUiState(
    val videoId: String = "",
    val videoTitle: String = "",
    val youtubeId: String = "",
    val siblingVideos: List<WhitelistItem> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isLoading: Boolean = true,
    val playbackDelaySeconds: Int = 0,
    val isLiked: Boolean = false,
    val isLiking: Boolean = false,
    val likeError: String? = null,
    val offlineVideo: OfflineVideo? = null,
    val isUpdatingDownload: Boolean = false,
    val error: String? = null,
    val remainingTimeFormatted: String? = null,
    val isTimeLimitReached: Boolean = false,
    val isSleepTimerExpired: Boolean = false
)

@HiltViewModel(assistedFactory = VideoPlayerViewModel.Factory::class)
class VideoPlayerViewModel @AssistedInject constructor(
    private val whitelistRepository: WhitelistRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val timeLimitChecker: TimeLimitChecker,
    private val sleepTimerManager: SleepTimerManager,
    private val likedVideoRepository: LikedVideoRepository,
    private val offlineVideoRepository: OfflineVideoRepository,
    @Assisted("profileId") private val profileId: String,
    @Assisted("videoId") private val videoId: String,
    @Assisted("videoTitle") private val initialVideoTitle: String,
    @Assisted("channelTitle") private val channelTitle: String?,
    @Assisted("sourceYoutubeId") private val sourceYoutubeId: String? = null
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("profileId") profileId: String,
            @Assisted("videoId") videoId: String,
            @Assisted("videoTitle") videoTitle: String,
            @Assisted("channelTitle") channelTitle: String?,
            @Assisted("sourceYoutubeId") sourceYoutubeId: String?
        ): VideoPlayerViewModel
    }

    private val _uiState = MutableStateFlow(VideoPlayerUiState(videoId = videoId))
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var siblingsJob: Job? = null
    private var playbackDelayJob: Job? = null
    private var likedIds: Set<String> = emptySet()
    private var offlineVideos: List<OfflineVideo> = emptyList()

    init {
        loadVideo()
        loadSiblings()
        observeTimeLimit()
        observeSleepTimer()
        viewModelScope.launch {
            likedVideoRepository.getByProfile(profileId).collect { saved ->
                likedIds = saved.map { it.youtubeId }.toSet()
                _uiState.value = _uiState.value.copy(isLiked = _uiState.value.youtubeId in likedIds)
            }
        }
        viewModelScope.launch {
            offlineVideoRepository.getByProfile(profileId).collect { videos ->
                offlineVideos = videos
                _uiState.value = _uiState.value.copy(offlineVideo = videos.firstOrNull {
                    it.video.youtubeId == _uiState.value.youtubeId
                })
            }
        }
    }

    fun toggleOffline() {
        val state = _uiState.value
        if (state.isUpdatingDownload || state.youtubeId.isBlank()) return
        _uiState.value = state.copy(isUpdatingDownload = true, likeError = null)
        viewModelScope.launch {
            try {
                if (state.offlineVideo?.state in listOf("ready", "queued", "downloading")) {
                    offlineVideoRepository.remove(profileId, state.youtubeId)
                } else if (!offlineVideoRepository.download(libraryVideo(state))) {
                    _uiState.value = _uiState.value.copy(likeError = "This video's approved source is no longer available.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(likeError = "Couldn't update the offline download. Please try again.")
            } finally {
                _uiState.value = _uiState.value.copy(isUpdatingDownload = false)
            }
        }
    }

    private fun libraryVideo(state: VideoPlayerUiState): LibraryVideo {
        val sibling = state.siblingVideos.firstOrNull { it.youtubeId == state.youtubeId }
        return LibraryVideo(profileId, state.youtubeId, state.videoTitle,
            sibling?.thumbnailUrl ?: "https://i.ytimg.com/vi/${state.youtubeId}/hqdefault.jpg",
            sibling?.channelTitle ?: channelTitle,
            if (sibling != null) state.youtubeId else sourceYoutubeId ?: state.youtubeId,
            System.currentTimeMillis())
    }

    fun toggleLiked() {
        val state = _uiState.value
        if (state.isLiking || state.youtubeId.isBlank()) return
        _uiState.value = state.copy(isLiking = true, likeError = null)
        viewModelScope.launch {
            try {
                if (state.isLiked) {
                    likedVideoRepository.remove(profileId, state.youtubeId)
                } else {
                    val saved = likedVideoRepository.like(libraryVideo(state))
                    if (!saved && _uiState.value.youtubeId == state.youtubeId) {
                        _uiState.value = _uiState.value.copy(likeError = "This video's approved source is no longer available.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (_uiState.value.youtubeId == state.youtubeId) {
                    _uiState.value = _uiState.value.copy(likeError = "Couldn't update liked videos. Please try again.")
                }
            } finally {
                _uiState.value = _uiState.value.copy(isLiking = false)
            }
        }
    }

    private fun loadVideo() {
        _uiState.value = _uiState.value.copy(
            videoTitle = initialVideoTitle,
            youtubeId = videoId,
            isLoading = false
        )
        startPlaybackDelay()
    }

    private fun startPlaybackDelay() {
        playbackDelayJob?.cancel()
        // Set this synchronously so the player cannot autoplay before the coroutine runs.
        _uiState.value = _uiState.value.copy(playbackDelaySeconds = PLAYBACK_DELAY_SECONDS)
        playbackDelayJob = viewModelScope.launch {
            for (remaining in PLAYBACK_DELAY_SECONDS - 1 downTo 0) {
                delay(1_000L)
                _uiState.value = _uiState.value.copy(playbackDelaySeconds = remaining)
            }
        }
    }

    private fun loadSiblings() {
        if (channelTitle == null) return

        siblingsJob?.cancel()
        siblingsJob = viewModelScope.launch {
            whitelistRepository.getVideosByChannelTitle(profileId, channelTitle)
                .collect { siblings ->
                    val currentIdx = siblings.indexOfFirst { it.youtubeId == _uiState.value.youtubeId }
                    _uiState.value = _uiState.value.copy(
                        siblingVideos = siblings,
                        currentIndex = currentIdx,
                        hasNext = currentIdx >= 0 && currentIdx < siblings.size - 1,
                        hasPrevious = currentIdx > 0
                    )
                }
        }
    }

    private fun observeTimeLimit() {
        viewModelScope.launch {
            timeLimitChecker.getTimeLimitStatus(profileId).collect { status ->
                _uiState.value = _uiState.value.copy(
                    remainingTimeFormatted = status.remainingSeconds?.let { formatRemaining(it) },
                    isTimeLimitReached = status.isLimitReached
                )
            }
        }
    }

    private fun observeSleepTimer() {
        viewModelScope.launch {
            sleepTimerManager.state.collect { sleepState ->
                _uiState.value = _uiState.value.copy(
                    isSleepTimerExpired = sleepState.status == SleepTimerStatus.EXPIRED
                            && sleepState.profileId == profileId
                )
            }
        }
    }

    fun onVideoEnded(watchedSeconds: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            watchHistoryRepository.recordWatch(
                profileId = profileId,
                videoId = state.youtubeId,
                videoTitle = state.videoTitle,
                watchedSeconds = watchedSeconds
            )

            if (state.hasNext) {
                navigateToIndex(state.currentIndex + 1)
            }
        }
    }

    fun playNext() {
        val state = _uiState.value
        if (state.hasNext) {
            navigateToIndex(state.currentIndex + 1)
        }
    }

    fun playPrevious() {
        val state = _uiState.value
        if (state.hasPrevious) {
            navigateToIndex(state.currentIndex - 1)
        }
    }

    fun playVideoAt(index: Int) {
        navigateToIndex(index)
    }

    private fun navigateToIndex(index: Int) {
        val siblings = _uiState.value.siblingVideos
        if (index < 0 || index >= siblings.size) return

        val nextItem = siblings[index]
        if (nextItem.youtubeId == _uiState.value.youtubeId) return
        _uiState.value = _uiState.value.copy(
            videoId = nextItem.youtubeId,
            videoTitle = nextItem.title,
            youtubeId = nextItem.youtubeId,
            isLiked = nextItem.youtubeId in likedIds,
            offlineVideo = offlineVideos.firstOrNull { it.video.youtubeId == nextItem.youtubeId },
            likeError = null,
            currentIndex = index,
            hasNext = index < siblings.size - 1,
            hasPrevious = index > 0
        )
        startPlaybackDelay()
    }

    private fun formatRemaining(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private companion object {
        const val PLAYBACK_DELAY_SECONDS = 20
    }
}
