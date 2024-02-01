package com.x8bit.bitwarden.data.vault.manager

import android.content.Context
import android.net.Uri
import com.x8bit.bitwarden.data.platform.annotation.OmitFromCoverage
import com.x8bit.bitwarden.data.vault.datasource.network.service.DownloadService
import com.x8bit.bitwarden.data.vault.manager.model.DownloadResult
import okio.use
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * The buffer size to be used when reading from an input stream.
 */
private const val BUFFER_SIZE: Int = 1024

/**
 * The default implementation of the [FileManager] interface.
 */
@OmitFromCoverage
class FileManagerImpl(
    private val context: Context,
    private val downloadService: DownloadService,
) : FileManager {

    override suspend fun deleteFile(file: File) {
        file.delete()
    }

    @Suppress("NestedBlockDepth", "ReturnCount")
    override suspend fun downloadFileToCache(url: String): DownloadResult {
        val response = downloadService
            .getDataStream(url)
            .fold(
                onSuccess = { it },
                onFailure = { return DownloadResult.Failure },
            )

        // Create a temporary file in cache to write to
        val file = File(context.cacheDir, UUID.randomUUID().toString())
        val stream = response.byteStream()
        stream.use {
            val buffer = ByteArray(BUFFER_SIZE)
            var progress = 0
            FileOutputStream(file).use { fos ->
                @Suppress("TooGenericExceptionCaught")
                try {
                    var read = stream.read(buffer)
                    while (read > 0) {
                        fos.write(buffer, 0, read)
                        progress += read
                        read = stream.read(buffer)
                    }
                    fos.flush()
                } catch (e: RuntimeException) {
                    return DownloadResult.Failure
                }
            }
        }

        return DownloadResult.Success(file)
    }

    @Suppress("NestedBlockDepth")
    override suspend fun fileToUri(fileUri: Uri, file: File): Boolean {
        @Suppress("TooGenericExceptionCaught")
        return try {
            context
                .contentResolver
                .openOutputStream(fileUri)
                ?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var length: Int
                        while (inputStream.read(buffer).also { length = it } != -1) {
                            outputStream.write(buffer, 0, length)
                        }
                    }
                }
            true
        } catch (exception: RuntimeException) {
            false
        }
    }

    override fun uriToByteArray(fileUri: Uri): ByteArray =
        context
            .contentResolver
            .openInputStream(fileUri)
            ?.use { inputStream ->
                ByteArrayOutputStream().use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } != -1) {
                        outputStream.write(buffer, 0, length)
                    }
                    outputStream.toByteArray()
                }
            }
            ?: byteArrayOf()
}