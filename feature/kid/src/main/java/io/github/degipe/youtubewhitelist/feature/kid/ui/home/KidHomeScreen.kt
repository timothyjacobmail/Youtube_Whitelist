package io.github.degipe.youtubewhitelist.feature.kid.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import io.github.degipe.youtubewhitelist.core.data.model.LibraryVideo

@Composable
fun KidHomeScreen(
    viewModel: KidHomeViewModel,
    onParentAccess: () -> Unit,
    onSearchClick: () -> Unit,
    onLibraryVideoClick: (LibraryVideo) -> Unit,
    onChannelClick: (youtubeId: String, channelTitle: String, thumbnailUrl: String) -> Unit,
    onVideoClick: (videoId: String, videoTitle: String, channelTitle: String?) -> Unit,
    onPlaylistClick: (youtubeId: String, title: String, thumbnailUrl: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryError by viewModel.likeError.collectAsStateWithLifecycle()

    // Block back button in kid mode — only parent can exit via PIN
    BackHandler { /* Intentionally empty — prevents exiting kid mode */ }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onParentAccess,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Parent Mode"
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.isEmpty -> {
                    EmptyContent(
                        profileName = uiState.profileName,
                        modifier = Modifier.padding(padding)
                    )
                }
                else -> {
                    KidHomeContent(
                        uiState = uiState,
                        onSearchClick = onSearchClick,
                        onLibraryVideoClick = onLibraryVideoClick,
                        onUnlike = viewModel::removeLikedVideo,
                        onDeleteOffline = viewModel::removeOfflineVideo,
                        libraryError = libraryError,
                        onChannelClick = onChannelClick,
                        onVideoClick = onVideoClick,
                        onPlaylistClick = onPlaylistClick,
                        modifier = Modifier.padding(padding)
                    )
                }
            }

            // Time's Up overlay
            if (uiState.isTimeLimitReached) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Time's Up!",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Your daily screen time is over.\nAsk a parent to continue.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FloatingActionButton(
                            onClick = onParentAccess,
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Parent Mode")
                        }
                    }
                }
            }

            // Good Night overlay (sleep timer expired)
            if (uiState.isSleepTimerExpired) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A1A).copy(alpha = 0.98f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFF7B68EE)
                        )
                        Text(
                            text = "Good Night!",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color(0xFFB0B0D0)
                        )
                        Text(
                            text = "Time to sleep.\nSweet dreams!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = Color(0xFFB0B0D0).copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FloatingActionButton(
                            onClick = onParentAccess,
                            containerColor = Color(0xFF7B68EE)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Parent Mode",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(profileName: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (profileName.isNotEmpty()) "Hi $profileName!" else "My Videos",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No whitelisted content yet. Ask a parent to add videos!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun KidHomeContent(
    uiState: KidHomeUiState,
    onSearchClick: () -> Unit,
    onLibraryVideoClick: (LibraryVideo) -> Unit,
    onUnlike: (String) -> Unit,
    onDeleteOffline: (String) -> Unit,
    libraryError: String?,
    onChannelClick: (youtubeId: String, channelTitle: String, thumbnailUrl: String) -> Unit,
    onVideoClick: (videoId: String, videoTitle: String, channelTitle: String?) -> Unit,
    onPlaylistClick: (youtubeId: String, title: String, thumbnailUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Greeting + Search
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (uiState.profileName.isNotEmpty()) "Hi ${uiState.profileName}!" else "My Videos",
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }
        }

        // Remaining time chip
        uiState.remainingTimeFormatted?.let { remaining ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Time remaining: $remaining",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Text("Liked videos", style = MaterialTheme.typography.titleMedium)
        if (uiState.likedVideos.isEmpty()) {
            Text("Tap Like on a video to find it here.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.likedVideos, key = { it.youtubeId }) { video ->
                    Column(Modifier.width(200.dp)) {
                        VideoCard(video.title, video.thumbnailUrl, video.channelTitle,
                            onClick = { onLibraryVideoClick(video) })
                        TextButton(onClick = { onUnlike(video.youtubeId) }) { Text("Unlike") }
                    }
                }
            }
        }

        Text("Saved for offline", style = MaterialTheme.typography.titleMedium)
        Text("Downloaded copies expire after 7 days.", style = MaterialTheme.typography.bodySmall)
        if (uiState.offlineVideos.isEmpty()) {
            Text("Use Save offline on a video to download it.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.offlineVideos, key = { it.video.youtubeId }) { offline ->
                    Column(Modifier.width(200.dp)) {
                        val video = offline.video
                        VideoCard(video.title, video.thumbnailUrl, video.channelTitle,
                            onClick = { onLibraryVideoClick(video) })
                        Text(when (offline.state) {
                            "ready" -> "Available offline · ${java.text.DateFormat.getDateInstance().format(java.util.Date(offline.expiresAt))} expiry"
                            "queued" -> "Waiting for connection / storage"
                            "downloading" -> "Downloading ${offline.progress}%"
                            else -> offline.error ?: "Download failed. Open video to retry."
                        }, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onDeleteOffline(video.youtubeId) }) {
                            Text(if (offline.state in listOf("queued", "downloading")) "Cancel download" else "Delete offline copy")
                        }
                    }
                }
            }
        }
        libraryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        // Channels section
        if (uiState.channels.isNotEmpty()) {
            Text(
                text = "Channels",
                style = MaterialTheme.typography.titleMedium
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                uiState.channels.chunked(2).forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { channel ->
                            ChannelCard(
                                channel = channel,
                                onClick = { onChannelClick(channel.youtubeId, channel.title, channel.thumbnailUrl) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill empty space if odd number
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Videos section
        if (uiState.recentVideos.isNotEmpty()) {
            Text(
                text = "Videos",
                style = MaterialTheme.typography.titleMedium
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(uiState.recentVideos, key = { it.id }) { video ->
                    VideoCard(
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        channelTitle = video.channelTitle,
                        onClick = { onVideoClick(video.youtubeId, video.title, video.channelTitle) }
                    )
                }
            }
        }

        // Playlists section
        if (uiState.playlists.isNotEmpty()) {
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.titleMedium
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(uiState.playlists, key = { it.id }) { playlist ->
                    VideoCard(
                        title = playlist.title,
                        thumbnailUrl = playlist.thumbnailUrl,
                        channelTitle = playlist.channelTitle,
                        onClick = { onPlaylistClick(playlist.youtubeId, playlist.title, playlist.thumbnailUrl) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun ChannelCard(
    channel: WhitelistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = channel.title,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = channel.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun VideoCard(
    title: String,
    thumbnailUrl: String,
    channelTitle: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                channelTitle?.let { channelTitle ->
                    Text(
                        text = channelTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
