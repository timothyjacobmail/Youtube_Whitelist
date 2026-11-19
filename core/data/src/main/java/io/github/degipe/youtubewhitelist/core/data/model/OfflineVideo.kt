package io.github.degipe.youtubewhitelist.core.data.model

data class OfflineVideo(
    val video: LibraryVideo,
    val state: String,
    val progress: Int,
    val expiresAt: Long,
    val localPath: String?,
    val audioPath: String?,
    val error: String?
)
