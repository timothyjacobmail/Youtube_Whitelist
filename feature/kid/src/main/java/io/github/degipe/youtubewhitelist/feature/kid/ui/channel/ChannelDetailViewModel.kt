package io.github.degipe.youtubewhitelist.feature.kid.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.data.model.PlaylistVideo
import io.github.degipe.youtubewhitelist.core.data.repository.ChannelVideoCacheRepository
import io.github.degipe.youtubewhitelist.core.data.repository.YouTubeApiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ChannelDetailUiState(
    val channelTitle: String = "",
    val videos: List<PlaylistVideo> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMorePages: Boolean = false,
    val showingPopular: Boolean = true
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChannelDetailViewModel.Factory::class)
class ChannelDetailViewModel @AssistedInject constructor(
    private val youTubeApiRepository: YouTubeApiRepository,
    private val channelVideoCacheRepository: ChannelVideoCacheRepository,
    @Assisted("channelId") private val channelId: String,
    @Assisted("channelTitle") private val channelTitle: String
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("channelId") channelId: String,
            @Assisted("channelTitle") channelTitle: String
        ): ChannelDetailViewModel
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var uploadsPlaylistId: String? = null
    private var nextPageToken: String? = null
    private val showingPopular = MutableStateFlow(true)
    private var loadJob: Job? = null

    private data class ControlState(
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        val hasMorePages: Boolean = false
    )

    private val _controlState = MutableStateFlow(ControlState())

    private val videosFlow = combine(_searchQuery, showingPopular) { query, popular -> query to popular }
        .debounce(300)
        .flatMapLatest { (query, popular) ->
            if (popular) {
                channelVideoCacheRepository.getPopularVideos(channelId).map { videos ->
                    videos.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
                }
            } else if (query.isBlank()) {
                channelVideoCacheRepository.getVideos(channelId)
            } else {
                channelVideoCacheRepository.searchVideos(channelId, query)
            }
        }

    val uiState: StateFlow<ChannelDetailUiState> = combine(
        videosFlow,
        _controlState,
        showingPopular
    ) { videos, ctrl, popular ->
        ChannelDetailUiState(
            channelTitle = channelTitle,
            videos = videos,
            isLoading = ctrl.isLoading,
            isLoadingMore = ctrl.isLoadingMore,
            error = ctrl.error,
            hasMorePages = ctrl.hasMorePages,
            showingPopular = popular
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ChannelDetailUiState(channelTitle = channelTitle)
    )

    init {
        loadChannelVideos()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        loadChannelVideos(forceRefresh = true)
    }

    fun showPopular(popular: Boolean) {
        if (showingPopular.value == popular) return
        showingPopular.value = popular
        loadChannelVideos()
    }

    fun loadMore() {
        val token = nextPageToken
        if (showingPopular.value || token == null || _controlState.value.isLoadingMore) return

        _controlState.value = _controlState.value.copy(isLoadingMore = true)
        loadJob = viewModelScope.launch {
            val playlistId = uploadsPlaylistId ?: return@launch
            when (val result = youTubeApiRepository.getPlaylistItemsPage(playlistId, token)) {
                is AppResult.Success -> {
                    val page = result.data
                    channelVideoCacheRepository.cacheVideos(channelId, page.videos)
                    nextPageToken = page.nextPageToken
                    _controlState.value = _controlState.value.copy(
                        isLoadingMore = false,
                        hasMorePages = page.nextPageToken != null
                    )
                }
                is AppResult.Error -> {
                    _controlState.value = _controlState.value.copy(
                        isLoadingMore = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onClearSearch() {
        _searchQuery.value = ""
    }

    private fun loadChannelVideos(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        _controlState.value = ControlState(isLoading = true)
        nextPageToken = null
        loadJob = viewModelScope.launch {
            if (showingPopular.value) {
                val saved = channelVideoCacheRepository.getPopularVideos(channelId).first()
                if (saved.isNotEmpty() && !forceRefresh) {
                    _controlState.value = ControlState(isLoading = false)
                    return@launch
                }
                // Keep saved results visible while refreshing, including on failure.
                _controlState.value = ControlState(isLoading = saved.isEmpty())
                when (val result = youTubeApiRepository.getPopularChannelVideos(channelId)) {
                    is AppResult.Success -> {
                        channelVideoCacheRepository.replacePopularVideos(channelId, result.data)
                        _controlState.value = ControlState(isLoading = false)
                    }
                    is AppResult.Error -> {
                        _controlState.value = ControlState(isLoading = false, error = result.message)
                    }
                }
                return@launch
            }
            channelVideoCacheRepository.clearCache(channelId)

            when (val channelResult = youTubeApiRepository.getChannelById(channelId)) {
                is AppResult.Success -> {
                    val playlistId = channelResult.data.uploadsPlaylistId
                    if (playlistId == null) {
                        _controlState.value = ControlState(
                            isLoading = false,
                            error = "Channel uploads playlist not found"
                        )
                        return@launch
                    }
                    uploadsPlaylistId = playlistId

                    when (val videosResult = youTubeApiRepository.getPlaylistItemsPage(playlistId, null)) {
                        is AppResult.Success -> {
                            val page = videosResult.data
                            channelVideoCacheRepository.cacheVideos(channelId, page.videos)
                            nextPageToken = page.nextPageToken
                            _controlState.value = ControlState(
                                isLoading = false,
                                hasMorePages = page.nextPageToken != null
                            )
                        }
                        is AppResult.Error -> {
                            _controlState.value = ControlState(
                                isLoading = false,
                                error = videosResult.message
                            )
                        }
                    }
                }
                is AppResult.Error -> {
                    _controlState.value = ControlState(
                        isLoading = false,
                        error = channelResult.message
                    )
                }
            }
        }
    }
}
