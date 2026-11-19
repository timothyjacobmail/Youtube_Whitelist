package io.github.degipe.youtubewhitelist.core.data.model

data class LibraryVideo(
    val kidProfileId: String,
    val youtubeId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String?,
    val sourceYoutubeId: String,
    val addedAt: Long
)
