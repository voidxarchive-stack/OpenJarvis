package com.openjarvis.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream

internal data class LiveAttachment(
    val displayName: String,
    val mimeType: String,
    val imageBase64: List<String> = emptyList(),
    val extractedText: String? = null,
    val note: String = "",
)

/** Converts user-selected files to a small set of visual frames or bounded plain text. */
internal fun loadLiveAttachment(context: Context, uri: Uri): LiveAttachment {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri).orEmpty().lowercase()
    val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf(String::isNotBlank) ?: "Selected file"

    return when {
        mime == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true) -> {
            val descriptor = resolver.openFileDescriptor(uri, "r")
                ?: error("Android couldn't open this PDF.")
            descriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(renderer.pageCount > 0) { "This PDF has no readable pages." }
                    val count = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    val frames = (0 until count).map { pageIndex ->
                        renderer.openPage(pageIndex).use { page ->
                            val scale = minOf(1f, MAX_FRAME_DIMENSION.toFloat() / maxOf(page.width, page.height))
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmapToBase64(bitmap).also { bitmap.recycle() }
                        }
                    }
                    LiveAttachment(
                        displayName,
                        "application/pdf",
                        imageBase64 = frames,
                        note = if (renderer.pageCount > count) {
                            "Rendered pages 1–$count of ${renderer.pageCount}; remaining pages were not attached."
                        } else "Rendered all ${renderer.pageCount} PDF page(s) as images.",
                    )
                }
            }
        }
        mime.startsWith("image/") -> {
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val scale = minOf(1f, MAX_FRAME_DIMENSION.toFloat() / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            } else {
                resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                    ?: error("Android couldn't read this image.")
            }
            LiveAttachment(displayName, mime, imageBase64 = listOf(bitmapToBase64(bitmap))).also {
                bitmap.recycle()
            }
        }
        mime.startsWith("video/") -> {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val timesMs = listOf(0L, durationMs / 2L, (durationMs - 1L).coerceAtLeast(0L)).distinct()
                val frames = timesMs.mapNotNull { timeMs ->
                    retriever.getFrameAtTime(timeMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { source ->
                            val scale = minOf(1f, MAX_FRAME_DIMENSION.toFloat() / maxOf(source.width, source.height))
                            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(
                                source,
                                (source.width * scale).toInt().coerceAtLeast(1),
                                (source.height * scale).toInt().coerceAtLeast(1),
                                true,
                            ) else source
                            bitmapToBase64(scaled).also {
                                if (scaled !== source) scaled.recycle()
                                source.recycle()
                            }
                        }
                }
                require(frames.isNotEmpty()) { "No video frames could be read from this file." }
                LiveAttachment(
                    displayName,
                    mime,
                    imageBase64 = frames,
                    note = "Attached ${frames.size} sampled video frame(s). Jarvis does not receive the full video or its audio.",
                )
            } finally {
                retriever.release()
            }
        }
        mime == "text/plain" || displayName.endsWith(".txt", ignoreCase = true) -> {
            val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(MAX_TEXT_CHARS) }
                ?: error("Android couldn't read this text file.")
            LiveAttachment(displayName, mime, extractedText = text, note = "Text content extracted locally.")
        }
        else -> error("Unsupported file type. Choose a PDF, image, video or plain-text file.")
    }
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private const val MAX_PDF_PAGES = 5
private const val MAX_FRAME_DIMENSION = 960
private const val MAX_TEXT_CHARS = 80_000
private const val JPEG_QUALITY = 68
