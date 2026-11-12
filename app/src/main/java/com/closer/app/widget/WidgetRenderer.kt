package com.closer.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.RemoteViews
import com.closer.app.R
import com.closer.app.data.WidgetSnapshot
import com.closer.app.location.DistanceMath
import com.closer.app.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * Draws the spectacles. Lenses and the distance bead are runtime bitmaps —
 * that buys real gradients, glow and perfect text centering that plain
 * RemoteViews drawables can't do.
 */
object WidgetRenderer {

    private val beadTextDark = 0xE614181C.toInt()
    private val bridgeColor = 0x8CFFFFFF.toInt()

    fun remoteViews(context: Context, s: WidgetSnapshot, compact: Boolean): RemoteViews {
        val layout = if (compact) R.layout.widget_spectacles_compact else R.layout.widget_spectacles
        val rv = RemoteViews(context.packageName, layout)
        val d = context.resources.displayMetrics.density
        val lensPx = ((if (compact) 30f else 36f) * d).roundToInt()
        val beadH = ((if (compact) 22f else 26f) * d)

        val near = s.nearby && !s.paused
        val dimAll = s.partnerUpdatedAtMs?.let { System.currentTimeMillis() - it > 30 * 60_000 } == true

        val meLens = lens(context, lensPx.toFloat(), s.myInitial, s.myColorIndex, near, dimAll || s.paused)
        val partnerLens = lens(context, lensPx.toFloat(), s.partnerInitial, s.partnerColorIndex, near, dimAll || s.paused)
        rv.setImageViewBitmap(R.id.lens_me, meLens)
        rv.setImageViewBitmap(R.id.lens_partner, partnerLens)

        val beadText = when {
            s.refreshing -> "…"
            s.paused -> "paused"
            !s.paired -> "add partner"
            s.distanceM == null -> "no fix yet"
            else -> DistanceMath.formatDistance(s.distanceM)
        }
        rv.setImageViewBitmap(R.id.bead, bead(context, beadText, near, beadH))
        rv.setInt(R.id.bridge_line, "setBackgroundColor", if (near) 0x99FF7A9C.toInt() else bridgeColor)

        rv.setTextViewText(R.id.footer, footerText(s, dimAll))
        if (s.paused) {
            rv.setTextColor(R.id.footer, 0x99FFFFFF.toInt())
        } else if (near) {
            rv.setTextColor(R.id.footer, 0xFFFFB3C6.toInt())
        } else {
            rv.setTextColor(R.id.footer, 0x99FFFFFF.toInt())
        }

        rv.setOnClickPendingIntent(
            R.id.widget_root,
            SpectaclesWidgetProvider.refreshPendingIntent(context),
        )
        rv.setOnClickPendingIntent(
            R.id.footer,
            SpectaclesWidgetProvider.openAppPendingIntent(context),
        )
        return rv
    }

    private fun footerText(s: WidgetSnapshot, stale: Boolean): String {
        val now = System.currentTimeMillis()
        return when {
            s.refreshing -> "refreshing…"
            s.paused -> "sharing paused — tap to resume"
            !s.paired -> "open Closer to pair up"
            s.distanceM == null -> "waiting for a location…"
            stale -> "them: last seen " + relative(s.partnerUpdatedAtMs, now)
            s.nearby -> "Nearby 💞 · within ${s.radiusM} m"
            else -> "updated " + relative(s.myUpdatedAtMs, now)
        }
    }

    private fun relative(ts: Long?, now: Long): String =
        if (ts == null) "—" else DistanceMath.relativeTime(ts, now)

    // ---- bitmaps ------------------------------------------------------------

    private fun lens(
        context: Context,
        sizePx: Float,
        initial: String,
        colorIndex: Int,
        glowing: Boolean,
        dimmed: Boolean,
    ): Bitmap {
        val size = sizePx.roundToInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = if (glowing) size * 0.14f else 0f
        val r = (size - pad * 2) / 2f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                Palette.startArgb(colorIndex), Palette.endArgb(colorIndex),
                Shader.TileMode.CLAMP,
            )
            alpha = if (dimmed) 150 else 255
        }
        c.drawCircle(size / 2f, size / 2f, r, fill)

        // inner glass highlight
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.055f
            color = 0x59FFFFFF
            alpha = if (dimmed) 90 else 150
        }
        c.drawCircle(size / 2f, size / 2f, r - ring.strokeWidth / 2f, ring)

        if (glowing) {
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = size * 0.10f
                color = 0xAAFF7A9C.toInt()
                maskFilter = android.graphics.BlurMaskFilter(size * 0.08f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            c.drawCircle(size / 2f, size / 2f, r + pad / 2f, glow)
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = size * 0.40f
            textAlign = Paint.Align.CENTER
            alpha = if (dimmed) 170 else 255
        }
        val baseline = size / 2f - (text.ascent() + text.descent()) / 2f
        c.drawText(initial.take(1), size / 2f, baseline, text)
        return bmp
    }

    private fun bead(context: Context, text: String, nearby: Boolean, heightPx: Float): Bitmap {
        val d = context.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = heightPx * 0.44f
            textAlign = Paint.Align.CENTER
            color = if (nearby) 0xFFFFFFFF.toInt() else beadTextDark
        }
        val padX = heightPx * 0.55f
        val w = (textPaint.measureText(text) + padX * 2).roundToInt()
        val h = heightPx.roundToInt()

        val bmp = Bitmap.createBitmap(w.coerceAtLeast(h), h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(0f, 0f, bmp.width.toFloat(), h.toFloat())
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (nearby) {
                shader = LinearGradient(
                    0f, 0f, bmp.width.toFloat(), h.toFloat(),
                    0xFFFF7A9C.toInt(), 0xFFFF9671.toInt(), Shader.TileMode.CLAMP,
                )
            } else {
                color = 0xD9FFFFFF.toInt()
            }
        }
        c.drawRoundRect(rect, h / 2f, h / 2f, pill)

        // hairline for the frosted edge
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = d
            color = 0x2E000000
        }
        c.drawRoundRect(rect.inset(edge.strokeWidth / 2f), h / 2f, h / 2f, edge)

        val baseline = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        c.drawText(text, bmp.width / 2f, baseline, textPaint)
        return bmp
    }

    private fun RectF.inset(v: Float): RectF = RectF(left + v, top + v, right - v, bottom - v)
}
