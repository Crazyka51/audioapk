package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/** Outcome of publishing a finished working copy into the shared music collection. */
sealed interface ExportResult {
    /** The file is available at [location], for example `Music/AudioDetective/case1_Cleaned.wav`. */
    data class Success(val location: String) : ExportResult

    /** Only possible below Android 10, where writing to shared storage needs a runtime permission. */
    data object PermissionRequired : ExportResult

    data object Failed : ExportResult
}

/**
 * Publishes finished working copies into the shared `Music/AudioDetective` folder so an exported
 * recording is visible in file managers and music players instead of living only inside the app
 * sandbox.
 *
 * Android 10+ writes through MediaStore (scoped storage, no permission needed). Android 9 and
 * below writes the file directly and asks the media scanner to index it, which requires
 * `WRITE_EXTERNAL_STORAGE` (declared with `maxSdkVersion="28"`).
 */
object AudioExporter {

    const val COLLECTION = "Music"
    const val ALBUM_DIR = "AudioDetective"

    /** Human readable destination shown in messages, e.g. `Music/AudioDetective`. */
    val displayLocation: String = "$COLLECTION/$ALBUM_DIR"

    /**
     * Copies [sourceFile] into the shared collection under its own name. The copy streams the bytes,
     * so exporting a long recording never loads it into memory.
     */
    fun publishWav(context: Context, sourceFile: File, displayName: String): ExportResult {
        if (!sourceFile.exists()) return ExportResult.Failed
        val safeName = displayName.ifBlank { sourceFile.name }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishViaMediaStore(context, sourceFile, safeName)
        } else {
            publishToPublicDirectory(context, sourceFile, safeName)
        }
    }

    private fun publishViaMediaStore(context: Context, sourceFile: File, displayName: String): ExportResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "$COLLECTION/$ALBUM_DIR")
            // Keep the entry hidden until the bytes are fully written.
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val uri = try {
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return ExportResult.Failed

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output, COPY_BUFFER_BYTES) }
            } ?: return ExportResult.Failed

            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null)

            ExportResult.Success("$displayLocation/$displayName")
        } catch (e: Exception) {
            e.printStackTrace()
            // Do not leave a half written, pending entry behind.
            try {
                resolver.delete(uri, null, null)
            } catch (ignored: Exception) {
            }
            ExportResult.Failed
        }
    }

    private fun publishToPublicDirectory(context: Context, sourceFile: File, displayName: String): ExportResult {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return ExportResult.PermissionRequired

        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            ALBUM_DIR
        )
        if (!directory.exists() && !directory.mkdirs()) return ExportResult.Failed

        val target = uniqueTarget(directory, displayName)
        return try {
            sourceFile.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_BYTES) }
            }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("audio/wav"), null)
            ExportResult.Success("$displayLocation/${target.name}")
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult.Failed
        }
    }

    /**
     * Picks a free `name.wav`, `name_1.wav`, ... target so exporting the same recording twice never
     * silently overwrites an earlier export.
     */
    fun uniqueTarget(directory: File, displayName: String): File {
        val base = displayName.substringBeforeLast(".")
        val extension = displayName.substringAfterLast(".", "wav")
        var candidate = File(directory, "$base.$extension")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(directory, "${base}_$counter.$extension")
            counter++
        }
        return candidate
    }

    private const val COPY_BUFFER_BYTES = 64 * 1024
}
