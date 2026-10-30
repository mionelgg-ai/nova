package app.nova.vpn.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** The profile photo the user picked from the device (email accounts). Stored privately in the app. */
object Avatar {
    private fun file(ctx: Context) = File(ctx.filesDir, "avatar.jpg")

    fun load(ctx: Context): ImageBitmap? =
        runCatching { BitmapFactory.decodeFile(file(ctx).path)?.asImageBitmap() }.getOrNull()

    fun delete(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    /** Reads the picked image, crops it to a centered square (max 1024 px) and keeps a JPEG copy. */
    fun save(ctx: Context, uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@runCatching false
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        file(ctx).outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    }.getOrDefault(false)
}
