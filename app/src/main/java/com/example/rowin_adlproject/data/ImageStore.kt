package com.example.rowin_adlproject.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Stores wardrobe photos in app-private storage and produces base64 JPEG
 * payloads for the Groq vision request (feature #6).
 *
 * Files are named by UUID; the file name (not the full path) is what the
 * database persists, so the store owns the on-disk layout.
 */
class ImageStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "wardrobe_images").apply { mkdirs() }

    private val avatarDir: File
        get() = File(context.filesDir, "profile_images").apply { mkdirs() }

    private val pendingDir: File
        get() = File(context.filesDir, "pending_camera").apply { mkdirs() }

    /** Imports an image [Uri] (gallery pick or camera capture), downscaled + EXIF-rotated. */
    fun importFromUri(uri: Uri): String {
        val fileName = "${UUID.randomUUID()}.jpg"
        val bitmap = decodeScaled(uri)
        val rotated = applyExifRotation(uri, bitmap)
        File(dir, fileName).outputStream().use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        return fileName
    }

    /**
     * Imports a full-resolution image [Uri] without the downscale cap
     * (used by gallery picks in Add Clothing so photos stay sharp).
     */
    fun importFullFromUri(uri: Uri): String {
        val fileName = "${UUID.randomUUID()}.jpg"
        val bitmap = decodeScaled(uri, targetPixels = FULL_PIXELS)
        val rotated = applyExifRotation(uri, bitmap)
        File(dir, fileName).outputStream().use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return fileName
    }

    /** Imports a picked gallery [Uri] as the profile avatar. */
    fun importAvatarFromUri(uri: Uri): String {
        val fileName = "${UUID.randomUUID()}.jpg"
        val bitmap = decodeScaled(uri, targetPixels = AVATAR_PIXELS)
        val rotated = applyExifRotation(uri, bitmap)
        File(avatarDir, fileName).outputStream().use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return fileName
    }

    /** Persists an already-decoded camera [Bitmap] (in-app CameraX capture). */
    fun saveBitmap(bitmap: Bitmap): String {
        val fileName = "${UUID.randomUUID()}.jpg"
        File(dir, fileName).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return fileName
    }

    /**
     * Creates a capture target for the in-app CameraX screen. The captured
     * JPEG lands here first; the caller then moves it via [commitPending].
     */
    fun newPendingFile(): File {
        val file = File(pendingDir, "capture_${System.currentTimeMillis()}.jpg")
        file.delete() // camera writes fresh each time
        return file
    }

    /** Moves a finished capture into the wardrobe store and returns its name. */
    fun commitPending(file: File): String? {
        if (!file.exists()) return null
        val fileName = "${UUID.randomUUID()}.jpg"
        val ok = runCatching {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: error("decode failed")
            val capped = if (maxOf(bitmap.width, bitmap.height) > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            File(dir, fileName).outputStream().use { out ->
                capped.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
        }.isSuccess
        file.delete()
        return fileName.takeIf { ok }
    }

    fun avatarFileFor(fileName: String?): File? =
        fileName?.let { File(avatarDir, it).takeIf { f -> f.exists() } }

    /** Deletes the old avatar file when a new one replaces it. */
    fun deleteAvatar(fileName: String) {
        File(avatarDir, fileName).delete()
    }

    fun fileFor(fileName: String?): File? =
        fileName?.let { File(dir, it).takeIf { f -> f.exists() } }

    fun delete(fileName: String) {
        File(dir, fileName).delete()
    }

    /** Base64 JPEG used as the Groq vision input. */
    fun toBase64(fileName: String): String? {
        val file = fileFor(fileName) ?: return null
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decodeScaled(uri: Uri, targetPixels: Long = TARGET_PIXELS): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        var width = bounds.outWidth
        var height = bounds.outHeight
        while (width.toLong() * height.toLong() / (sample * 2) > targetPixels) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("Could not decode image $uri")
        val maxSide = maxOf(decoded.width, decoded.height)
        val cap = if (targetPixels >= FULL_PIXELS) FULL_SIDE else MAX_SIDE
        return if (maxSide > cap) {
            val scale = cap.toFloat() / maxSide
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_SIDE = 1024
        const val FULL_SIDE = 2048
        const val TARGET_PIXELS = 1_100_000L // roughly 1024x1024
        const val FULL_PIXELS = 4_200_000L // roughly 2048x2048
        const val AVATAR_PIXELS = 400_000L // roughly 640x640
    }
}
