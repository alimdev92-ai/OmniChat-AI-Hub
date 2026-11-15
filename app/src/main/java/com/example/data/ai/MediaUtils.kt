package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

object MediaUtils {

    private const val MAX_IMAGE_DIMENSION = 1280
    private const val MAX_TEXT_FILE_CHARS = 32_000

    suspend fun createAttachmentFromUri(
        context: Context,
        uri: Uri,
        forceImage: Boolean = false
    ): PendingAttachment? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: if (forceImage) "image/jpeg" else "text/plain"
        val isImage = forceImage || mimeType.startsWith("image/")
        val displayName = resolveDisplayName(context, uri) ?: if (isImage) "image.jpg" else "document.txt"

        // Persist read permission when granted by SAF
        try {
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Photo Picker URIs do not always support persistable permissions; that's expected.
        }

        val extractedText = if (!isImage) {
            readTextSafely(context, uri, displayName)
        } else {
            ""
        }

        PendingAttachment(
            id = UUID.randomUUID().toString(),
            uriString = uri.toString(),
            displayName = displayName,
            mimeType = mimeType,
            isImage = isImage,
            extractedText = extractedText
        )
    }

    suspend fun loadScaledBitmap(context: Context, uriString: String): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, boundsOptions)
                }
                val width = boundsOptions.outWidth
                val height = boundsOptions.outHeight
                if (width <= 0 || height <= 0) return@withContext null

                var sampleSize = 1
                while (width / sampleSize > MAX_IMAGE_DIMENSION || height / sampleSize > MAX_IMAGE_DIMENSION) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                } ?: return@withContext null

                val maxSide = maxOf(raw.width, raw.height)
                if (maxSide <= MAX_IMAGE_DIMENSION) {
                    raw
                } else {
                    val scale = MAX_IMAGE_DIMENSION.toFloat() / maxSide.toFloat()
                    Bitmap.createScaledBitmap(
                        raw,
                        (raw.width * scale).toInt().coerceAtLeast(1),
                        (raw.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

    fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int = 82): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun readTextSafely(context: Context, uri: Uri, name: String): String {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val raw = reader.readText()
                if (raw.length > MAX_TEXT_FILE_CHARS) {
                    raw.take(MAX_TEXT_FILE_CHARS) + "\n\n[Truncated $name at $MAX_TEXT_FILE_CHARS characters]"
                } else {
                    raw
                }
            }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
