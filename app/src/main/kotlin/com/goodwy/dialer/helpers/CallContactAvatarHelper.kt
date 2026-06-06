package com.goodwy.dialer.helpers

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.provider.MediaStore
import android.util.Size
import com.goodwy.commons.helpers.isQPlus
import com.goodwy.dialer.R
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap

class CallContactAvatarHelper(private val context: Context) {
    fun getCallContactAvatar(photoUri: String?, round: Boolean = true, width: Int = 0, height: Int = 0): Bitmap? {
        var bitmap: Bitmap? = null
        if (photoUri?.isNotEmpty() == true) {
            val photoUriParse = photoUri.toUri()
            try {
                val contentResolver = context.contentResolver
                bitmap = if (isQPlus()) {
                    if (round) {
                        val tmbSize = context.resources.getDimension(R.dimen.list_avatar_size).toInt()
                        contentResolver.loadThumbnail(photoUriParse, Size(tmbSize, tmbSize), null)
                    } else {
                        contentResolver.loadThumbnail(photoUriParse, Size(width, height), null)
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, photoUriParse)
                }
                bitmap = if (round) getCircularBitmap(bitmap!!) else bitmap
            } catch (_: Exception) {
                return null
            }
        }
        return bitmap
    }

    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val output = createBitmap(bitmap.width, bitmap.width)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val radius = bitmap.width / 2.toFloat()

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    // Composites the app launcher icon as a small badge in the bottom-right corner of the
    // caller avatar (the same look CallStyle gives for free on the ringing notification).
    // Fully self-guarding: this runs on a background thread outside the notification's
    // try/catch, so any drawing/OOM failure must degrade to the plain avatar, never crash.
    fun getBadgedAvatar(avatar: Bitmap): Bitmap {
        return try {
            val size = avatar.width
            if (size <= 0) return avatar
            val appIcon = context.packageManager.getApplicationIcon(context.packageName)

            val output = createBitmap(size, size)
            val canvas = Canvas(output)
            canvas.drawBitmap(avatar, 0f, 0f, null)

            val badgeDiameter = size * 0.42f
            val ring = badgeDiameter * 0.08f
            val center = size - badgeDiameter / 2f

            val ringPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
            }
            canvas.drawCircle(center, center, badgeDiameter / 2f, ringPaint)

            val iconSize = (badgeDiameter - 2 * ring).toInt().coerceAtLeast(1)
            val iconBitmap = getCircularBitmap(drawableToBitmap(appIcon, iconSize))
            canvas.drawBitmap(iconBitmap, center - iconSize / 2f, center - iconSize / 2f, null)
            output
        } catch (_: Exception) {
            avatar
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
