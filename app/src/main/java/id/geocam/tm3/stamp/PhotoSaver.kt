package id.geocam.tm3.stamp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Decodes the captured JPEG, burns the stamp, writes EXIF and publishes to DCIM/GeoCam. */
object PhotoSaver {
    const val FOLDER = "GeoCam"
    private const val JPEG_QUALITY = 92

    class Saved(val uri: Uri, val thumbnail: Bitmap)

    fun save(context: Context, jpeg: ByteArray, rotationDegrees: Int, input: StampInput): Saved {
        val bitmap = decodeUpright(jpeg, rotationDegrees)
        StampRenderer.draw(bitmap, StampText.lines(input))
        val thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 240, 240)

        val temp = File(context.cacheDir, "capture_${input.timeMillis}.jpg")
        try {
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
            writeExif(temp, input)
            val name = "GeoCam_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(input.timeMillis)) + ".jpg"
            return Saved(publish(context, temp, name, input.timeMillis), thumbnail)
        } finally {
            temp.delete()
        }
    }

    private fun decodeUpright(jpeg: ByteArray, rotationDegrees: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inMutable = true }
        var bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
            ?: error("Foto tidak bisa dibaca")
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        if (!bitmap.isMutable) {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            bitmap.recycle()
            bitmap = copy
        }
        return bitmap
    }

    private fun writeExif(file: File, input: StampInput) {
        val exif = ExifInterface(file.absolutePath)
        input.location?.let {
            exif.setLatLong(it.latitude, it.longitude)
            if (it.hasAltitude()) exif.setAltitude(it.altitude)
        }
        val date = Date(input.timeMillis)
        val dateTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(date)
        val offset = SimpleDateFormat("XXX", Locale.US).format(date)
        exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, StampText.summary(input))
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoCam TM-3")
        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        exif.saveAttributes()
    }

    private fun publish(context: Context, file: File, name: String, timeMillis: Long): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$FOLDER")
                put(MediaStore.Images.ImageColumns.DATE_TAKEN, timeMillis)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: error("Galeri tidak bisa diakses")
            try {
                resolver.openOutputStream(uri).use { out ->
                    checkNotNull(out) { "Galeri tidak bisa ditulis" }
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }

        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) error("Folder DCIM/$FOLDER tidak bisa dibuat")
        val target = File(dir, name)
        file.copyTo(target, overwrite = true)
        val values = ContentValues().apply {
            @Suppress("DEPRECATION")
            put(MediaStore.Images.ImageColumns.DATA, target.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.ImageColumns.DATE_TAKEN, timeMillis)
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Galeri tidak bisa diperbarui")
    }
}
