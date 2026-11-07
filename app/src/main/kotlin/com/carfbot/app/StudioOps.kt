package com.carfbot.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** Image helpers for AI Studio: load, soft-edged local edits, save, and the AI prompt/tag parsing. */
object StudioOps {
    val colors = intArrayOf(
        0xFFF2A3BB.toInt(), 0xFF8FB6E8.toInt(), 0xFFFFA874.toInt(), 0xFFB59BE8.toInt(), 0xFF8FE0C8.toInt()
    )

    fun loadBitmap(ctx: Context, uri: Uri, maxSide: Int = 1280): Bitmap? {
        return try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val longest = max(bounds.outWidth, bounds.outHeight)
            while (longest / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val rotation = try {
                cr.openInputStream(uri)?.use { s ->
                    when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            val longestNow = max(bmp.width, bmp.height)
            val scale = if (longestNow > maxSide) maxSide.toFloat() / longestNow else 1f
            if (rotation != 0f || scale < 1f) {
                val m = Matrix().apply { postRotate(rotation); postScale(scale, scale) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            bmp.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            null
        }
    }

    fun toBase64(bmp: Bitmap, maxSide: Int = 1024): String {
        val longest = max(bmp.width, bmp.height)
        val src = if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
        } else bmp
        val out = ByteArrayOutputStream()
        src.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun crop(src: Bitmap, cx: Int, cy: Int, r: Int): Bitmap {
        val l = max(0, cx - r)
        val t = max(0, cy - r)
        val rr = min(src.width, cx + r)
        val b = min(src.height, cy + r)
        return Bitmap.createBitmap(src, l, t, max(1, rr - l), max(1, b - t))
    }

    // ───── local edits: each returns a new bitmap with a soft-edged circular change ─────

    private fun filterRegion(src: Bitmap, cx: Int, cy: Int, r: Int, fn: (Bitmap) -> Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val rect = Rect(max(0, cx - r), max(0, cy - r), min(src.width, cx + r), min(src.height, cy + r))
        if (rect.width() < 2 || rect.height() < 2) return out
        val reg = Bitmap.createBitmap(src, rect.left, rect.top, rect.width(), rect.height())
        val filtered = fn(reg)
        val mask = RadialGradient(
            (cx - rect.left).toFloat(), (cy - rect.top).toFloat(), r.toFloat(),
            intArrayOf(android.graphics.Color.BLACK, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = ComposeShader(
            BitmapShader(filtered, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP), mask, PorterDuff.Mode.DST_IN
        )
        val c = Canvas(out)
        c.save()
        c.translate(rect.left.toFloat(), rect.top.toFloat())
        c.drawRect(0f, 0f, rect.width().toFloat(), rect.height().toFloat(), paint)
        c.restore()
        return out
    }

    fun blur(src: Bitmap, cx: Int, cy: Int, r: Int): Bitmap = filterRegion(src, cx, cy, r) { reg ->
        val small = Bitmap.createScaledBitmap(reg, max(1, reg.width / 14), max(1, reg.height / 14), true)
        Bitmap.createScaledBitmap(small, reg.width, reg.height, true)
    }

    fun mosaic(src: Bitmap, cx: Int, cy: Int, r: Int): Bitmap = filterRegion(src, cx, cy, r) { reg ->
        val small = Bitmap.createScaledBitmap(reg, max(1, reg.width / 22), max(1, reg.height / 22), false)
        Bitmap.createScaledBitmap(small, reg.width, reg.height, false)
    }

    fun tint(src: Bitmap, cx: Int, cy: Int, r: Int, color: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val strong = (color and 0x00FFFFFF) or (0xB0 shl 24)
        val none = color and 0x00FFFFFF
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            cx.toFloat(), cy.toFloat(), r.toFloat(), intArrayOf(strong, strong, none),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        Canvas(out).drawCircle(cx.toFloat(), cy.toFloat(), r.toFloat(), paint)
        return out
    }

    fun save(ctx: Context, bmp: Bitmap): Boolean = try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "CarfBot_" + System.currentTimeMillis() + ".jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CarfBot")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) false
        else ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } ?: false
    } catch (e: Exception) {
        false
    }

    // ───── AI prompts and parsing ─────

    fun promptFor(instruction: String?): String =
        "Look at this image and reply with ONLY a JSON object, no other text: " +
        "{\"prompt\": \"<one sentence describing the image like a text-to-image prompt: subject, style, mood, colours>\", " +
        "\"tags\": [\"<3 to 5 one-word tags>\"]}." +
        (if (!instruction.isNullOrBlank()) " Rewrite the prompt so it also follows this style request: $instruction." else "")

    const val AREA_PROMPT =
        "This is a crop of part of an image. Reply with ONLY a JSON object, no other text: " +
        "{\"prompt\": \"<one short sentence saying what this area shows>\", \"tags\": [\"<3 one-word tags>\"]}"

    fun parse(reply: String): Pair<String, List<String>>? {
        return try {
            val a = reply.indexOf('{')
            val b = reply.lastIndexOf('}')
            if (a < 0 || b <= a) return null
            val o = JSONObject(reply.substring(a, b + 1))
            val prompt = o.optString("prompt").trim()
            if (prompt.isEmpty()) return null
            val arr = o.optJSONArray("tags")
            val tags = ArrayList<String>()
            if (arr != null) for (i in 0 until min(arr.length(), 6)) tags.add(arr.optString(i).trim())
            prompt to tags.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}
