package io.github.degipe.youtubewhitelist.core.data.offline

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test

class OfflineStreamResolverTest {
    @Test fun `public video streams return real media bytes`() {
        val videoId = System.getenv("OFFLINE_SMOKE_VIDEO_ID")
        assumeTrue("Opt-in network check: set OFFLINE_SMOKE_VIDEO_ID", !videoId.isNullOrBlank())
        val client = OkHttpClient()
        val streams = OfflineStreamResolver(client).resolve(videoId!!)
        listOfNotNull(streams.videoUrl, streams.audioUrl).forEach { url ->
            client.newCall(Request.Builder().url(url).header("Range", "bytes=0-16383").build())
                .execute().use { response ->
                    assertThat(response.isSuccessful).isTrue()
                    val bytes = ByteArray(1024)
                    assertThat(response.body!!.byteStream().read(bytes)).isGreaterThan(0)
                }
        }
    }
}
