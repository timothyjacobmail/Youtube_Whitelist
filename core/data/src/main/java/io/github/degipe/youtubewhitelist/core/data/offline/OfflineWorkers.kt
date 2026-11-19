package io.github.degipe.youtubewhitelist.core.data.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.degipe.youtubewhitelist.core.data.repository.impl.OfflineVideoRepositoryImpl

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflineWorkerDependencies {
    fun offlineVideos(): OfflineVideoRepositoryImpl
}

class OfflineDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val profile = inputData.getString("profile") ?: return Result.failure()
        val video = inputData.getString("video") ?: return Result.failure()
        val repository = EntryPointAccessors.fromApplication(applicationContext, OfflineWorkerDependencies::class.java).offlineVideos()
        return if (repository.runDownload(profile, video)) Result.success() else Result.failure()
    }
}

class OfflineCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, OfflineWorkerDependencies::class.java)
            .offlineVideos().cleanup()
        return Result.success()
    }
}
