package com.goodwy.dialer.helpers

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import com.goodwy.commons.helpers.isQPlus
import com.goodwy.dialer.R
import androidx.core.net.toUri

class CallContactAvatarHelper(private val context: Context) {
    fun getCallContactAvatar(photoUri: String?, round: Boolean = true): Bitmap? {
        var bitmap: Bitmap? = null
        if (photoUri?.isNotEmpty() == true) {
            val photoUriParse = photoUri.toUri()
            try {
                val contentResolver = context.contentResolver
                bitmap = if (isQPlus()) {
                    val tmbSize = context.resources.getDimension(R.dimen.list_avatar_size).toInt()
                    contentResolver.loadThumbnail(photoUriParse, Size(tmbSize, tmbSize), null)
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, photoUriParse)
                }
                bitmap = if (round) getCircularBitmap(bitmap!!) else bitmap
            } catch (ignored: Exception) {
                return null
            }
        }
        return bitmap
    }

    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.width, Bitmap.Config.ARGB_8888)
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
}
