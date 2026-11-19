package io.github.degipe.youtubewhitelist.core.data.offline

import io.github.degipe.youtubewhitelist.core.network.di.PlainOkHttp
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class OfflineStreams(val videoUrl: String, val audioUrl: String?)

@Singleton
class OfflineStreamResolver @Inject constructor(@PlainOkHttp private val client: OkHttpClient) {
    init {
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val builder = okhttp3.Request.Builder().url(request.url())
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                request.headers().forEach { (name, values) ->
                    builder.removeHeader(name)
                    values.forEach { builder.addHeader(name, it) }
                }
                val body = request.dataToSend()?.toRequestBody()
                    ?: if (request.httpMethod() in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null
                client.newCall(builder.method(request.httpMethod(), body).build()).execute().use { response ->
                    return Response(response.code, response.message, response.headers.toMultimap(),
                        response.body?.string(), response.request.url.toString())
                }
            }
        })
    }

    @Synchronized
    fun resolve(youtubeId: String): OfflineStreams {
        require(youtubeId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid video ID" }
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$youtubeId")
        val combined = info.videoStreams.filter {
            it.isUrl && !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        val videos = combined.ifEmpty {
            info.videoOnlyStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        }
        val compatible = videos.filter { it.codec?.startsWith("avc") == true }
            .ifEmpty { videos.filter { it.format == MediaFormat.MPEG_4 } }.ifEmpty { videos }
        val video = compatible.filter { it.height in 1..720 }.maxByOrNull { it.height }
            ?: compatible.minByOrNull { it.height }
            ?: throw IOException("This video has no downloadable video stream.")
        val audio = if (video.isVideoOnly) {
            val audioStreams = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            audioStreams.filter { it.format == MediaFormat.M4A }.ifEmpty { audioStreams }
                .minByOrNull { it.averageBitrate }?.content
                ?: throw IOException("This video has no downloadable audio stream.")
        } else null
        return OfflineStreams(video.content, audio)
    }
}
