package io.github.degipe.youtubewhitelist.core.data.repository.impl

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.degipe.youtubewhitelist.core.common.di.IoDispatcher
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.data.model.LibraryVideo
import io.github.degipe.youtubewhitelist.core.data.model.OfflineVideo
import io.github.degipe.youtubewhitelist.core.data.offline.OfflineCleanupWorker
import io.github.degipe.youtubewhitelist.core.data.offline.OfflineDownloadWorker
import io.github.degipe.youtubewhitelist.core.data.offline.OfflineStreamResolver
import io.github.degipe.youtubewhitelist.core.data.repository.OfflineVideoRepository
import io.github.degipe.youtubewhitelist.core.database.dao.OfflineVideoDao
import io.github.degipe.youtubewhitelist.core.database.dao.WhitelistItemDao
import io.github.degipe.youtubewhitelist.core.database.entity.OfflineVideoEntity
import io.github.degipe.youtubewhitelist.core.network.di.PlainOkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineVideoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OfflineVideoDao,
    private val whitelistDao: WhitelistItemDao,
    private val resolver: OfflineStreamResolver,
    @PlainOkHttp private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : OfflineVideoRepository {
    private val directory get() = File(context.noBackupFilesDir, "offline-videos").apply { mkdirs() }
    private val work get() = WorkManager.getInstance(context)

    init {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("offline-cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OfflineCleanupWorker>(1, TimeUnit.HOURS).build())
    }

    override fun getByProfile(profileId: String) = dao.getByProfile(profileId).map { rows ->
        val now = System.currentTimeMillis()
        rows.filter { it.state != "ready" || it.expiresAt > now }.map { row ->
            val video = file(row.fileName + ".video")
            val audio = row.audioFileName?.let { file(it) }
            val available = row.state == "ready" && video.isFile && video.length() > 0 &&
                (audio == null || audio.isFile && audio.length() > 0)
            OfflineVideo(LibraryVideo(row.kidProfileId, row.youtubeId, row.title, row.thumbnailUrl,
                row.channelTitle, row.sourceYoutubeId, row.addedAt),
                if (row.state == "ready" && !available) "failed" else row.state,
                row.progress, row.expiresAt, if (available) video.absolutePath else null,
                if (available) audio?.absolutePath else null,
                if (row.state == "ready" && !available) "Local files are missing. Download again." else row.error)
        }
    }.onStart { cleanup() }.flowOn(ioDispatcher)

    override suspend fun download(video: LibraryVideo): Boolean = withContext(ioDispatcher) {
        val source = whitelistDao.findByYoutubeId(video.kidProfileId, video.youtubeId)
            ?.takeIf { it.type == WhitelistItemType.VIDEO }
            ?: whitelistDao.findByYoutubeId(video.kidProfileId, video.sourceYoutubeId)
            ?: return@withContext false
        if (source.type == WhitelistItemType.VIDEO && source.youtubeId != video.youtubeId) return@withContext false
        val old = dao.get(video.kidProfileId, video.youtubeId)
        if (old != null && old.state in listOf("queued", "downloading")) return@withContext true
        if (old != null && old.state == "ready" && old.expiresAt > System.currentTimeMillis() &&
            file(old.fileName + ".video").isFile &&
            old.audioFileName?.let { file(it).isFile } != false) return@withContext true
        if (old != null) deleteFiles(old)
        dao.insert(OfflineVideoEntity(video.kidProfileId, video.youtubeId, video.title,
            video.thumbnailUrl, video.channelTitle, source.id, source.youtubeId, System.currentTimeMillis(),
            UUID.randomUUID().toString(), null, "queued", 0, 0, null))
        val request = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setInputData(workDataOf("profile" to video.kidProfileId, "video" to video.youtubeId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true).build()).build()
        work.enqueueUniqueWork(workName(video.kidProfileId, video.youtubeId), ExistingWorkPolicy.REPLACE, request)
        true
    }

    override suspend fun remove(profileId: String, youtubeId: String) = withContext(ioDispatcher) {
        work.cancelUniqueWork(workName(profileId, youtubeId))
        val row = dao.get(profileId, youtubeId)
        dao.remove(profileId, youtubeId)
        if (row != null) deleteFiles(row)
        Unit
    }

    suspend fun runDownload(profileId: String, youtubeId: String): Boolean = withContext(ioDispatcher) {
        val row = dao.get(profileId, youtubeId) ?: return@withContext false
        val videoFile = file(row.fileName + ".video")
        val audioFile = file(row.fileName + ".audio")
        try {
            dao.updateState(profileId, youtubeId, row.fileName, "downloading", 0, null)
            val streams = resolver.resolve(youtubeId)
            currentCoroutineContext().ensureActive()
            var bytes = 0L
            suspend fun downloadTrack(url: String, target: File, start: Int, span: Int) {
                val part = file(target.name + ".part")
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download returned HTTP ${response.code}.")
                    val body = response.body ?: throw IOException("Empty download response.")
                    val length = body.contentLength()
                    var received = 0L
                    var reported = -1
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytes += count
                                received += count
                                if (bytes > 512L * 1024 * 1024 || directory.usableSpace < 50L * 1024 * 1024) {
                                    throw IOException("Not enough space, or this video exceeds the 512 MB download limit.")
                                }
                                output.write(buffer, 0, count)
                                val progress = start + if (length > 0) (received * span / length).toInt().coerceIn(0, span) else 0
                                if (progress != reported) {
                                    dao.updateState(profileId, youtubeId, row.fileName, "downloading", progress, null)
                                    reported = progress
                                }
                            }
                        }
                    }
                    if (received == 0L || length > 0 && received != length) throw IOException("Incomplete download. Try again.")
                }
                currentCoroutineContext().ensureActive()
                if (!part.renameTo(target)) throw IOException("Couldn't finish saving the download.")
            }
            downloadTrack(streams.videoUrl, videoFile, 0, if (streams.audioUrl == null) 99 else 85)
            streams.audioUrl?.let { downloadTrack(it, audioFile, 85, 14) }
            currentCoroutineContext().ensureActive()
            val latest = dao.get(profileId, youtubeId)
            if (latest?.fileName != row.fileName) {
                deleteFiles(row)
                return@withContext false
            }
            dao.complete(profileId, youtubeId, row.fileName,
                if (streams.audioUrl != null) audioFile.name else null, System.currentTimeMillis() + RETENTION_MS)
            work.enqueue(OneTimeWorkRequestBuilder<OfflineCleanupWorker>()
                .setInitialDelay(7, TimeUnit.DAYS).build())
            true
        } catch (e: CancellationException) {
            deleteFiles(row)
            throw e
        } catch (_: Exception) {
            deleteFiles(row)
            dao.updateState(profileId, youtubeId, row.fileName, "failed", 0,
                "Couldn't download this video. Check your connection and free space, then retry. Some videos aren't available for download.")
            false
        }
    }

    suspend fun cleanup() = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        dao.getAll().filter { it.state == "ready" && it.expiresAt <= now }.forEach {
            dao.remove(it.kidProfileId, it.youtubeId)
            deleteFiles(it)
        }
        val active = dao.getAll().map { it.fileName }.toSet()
        directory.listFiles()?.filter { it.name.substringBefore('.') !in active }?.forEach { it.delete() }
        Unit
    }

    private fun file(name: String): File {
        require(name.matches(Regex("[a-fA-F0-9-]+\\.(video|audio)(\\.part)?")))
        return File(directory, name)
    }
    private fun deleteFiles(row: OfflineVideoEntity) {
        listOf(".video", ".audio", ".video.part", ".audio.part").forEach { file(row.fileName + it).delete() }
    }
    private fun workName(profileId: String, youtubeId: String) = "offline:$profileId:$youtubeId"

    companion object { const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000 }
}
