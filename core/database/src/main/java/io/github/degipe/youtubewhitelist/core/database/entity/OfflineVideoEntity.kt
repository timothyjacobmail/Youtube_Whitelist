package io.github.degipe.youtubewhitelist.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "offline_videos", primaryKeys = ["kidProfileId", "youtubeId"],
    foreignKeys = [
        ForeignKey(entity = KidProfileEntity::class, parentColumns = ["id"], childColumns = ["kidProfileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WhitelistItemEntity::class, parentColumns = ["id"], childColumns = ["sourceWhitelistItemId"], onDelete = ForeignKey.CASCADE)
    ], indices = [Index("sourceWhitelistItemId")])
data class OfflineVideoEntity(
    val kidProfileId: String,
    val youtubeId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String?,
    val sourceWhitelistItemId: String,
    val sourceYoutubeId: String,
    val addedAt: Long,
    val fileName: String,
    val audioFileName: String?,
    val state: String,
    val progress: Int,
    val expiresAt: Long,
    val error: String?
)
