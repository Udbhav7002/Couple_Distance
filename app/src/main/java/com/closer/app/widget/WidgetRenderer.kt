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
import com.closer.app.data.ConnectionStatus
import com.closer.app.data.WidgetSnapshot
import com.closer.app.location.DistanceMath
import com.closer.app.ui.theme.Palette
import kotlin.math.roundToInt

/** Supported selectable widget dimensions. */
enum class WidgetSize {
    SIZE_1x2,
    SIZE_1x3,
    SIZE_2x3,
    SIZE_4x3;

    val isSingleColumn: Boolean
        get() = this == SIZE_1x2 || this == SIZE_1x3
}

/**
 * Renders RemoteViews for all multi-size spectacles widgets (1x2, 1x3, 2x3, 4x3).
 *
 * - 1-column layouts (1x2, 1x3): Vertically stacked layout with vertical bridge line and horizontal bead pill.
 * - Multi-column layouts (2x3, 4x3): Horizontal layout with horizontal bridge line and live distance bead.
 * - Tactile matte Apple-style design with crisp rendered bitmaps and 5 connection statuses.
 * - View ID presence is strictly guarded per layout to prevent RemoteViews warnings/exceptions.
 */
object WidgetRenderer {

    private val beadTextDark = 0xE614181C.toInt()
    private val bridgeColor = 0x8CFFFFFF.toInt()

    fun remoteViews(context: Context, s: WidgetSnapshot, size: WidgetSize = WidgetSize.SIZE_4x3): RemoteViews {
        val layoutRes = when (size) {
            WidgetSize.SIZE_1x2 -> R.layout.widget_spectacles_1x2
            WidgetSize.SIZE_1x3 -> R.layout.widget_spectacles_1x3
            WidgetSize.SIZE_2x3 -> R.layout.widget_spectacles_2x3
            WidgetSize.SIZE_4x3 -> R.layout.widget_spectacles_4x3
        }

        val rv = RemoteViews(context.packageName, layoutRes)
        val d = context.resources.displayMetrics.density
        val now = System.currentTimeMillis()

        // Sizing per layout
        val lensDp = when (size) {
            WidgetSize.SIZE_1x2 -> 28f
            WidgetSize.SIZE_1x3 -> 30f
            WidgetSize.SIZE_2x3 -> 34f
            WidgetSize.SIZE_4x3 -> 40f
        }
        val beadHdp = when (size) {
            WidgetSize.SIZE_1x2 -> 22f
            WidgetSize.SIZE_1x3 -> 24f
            WidgetSize.SIZE_2x3 -> 24f
            WidgetSize.SIZE_4x3 -> 28f
        }

        val lensPx = (lensDp * d).roundToInt()
        val beadH = beadHdp * d

        val near = s.nearby && !s.paused
        val dimAll = s.partnerUpdatedAtMs?.let { now - it > 30 * 60_000 } == true
        val status = s.connectionStatus(now)

        // 1. Render tactile matte lens bitmaps
        val meLens = lens(context, lensPx.toFloat(), s.myInitial, s.myColorIndex, near, dimAll || s.paused)
        val partnerLens = lens(context, lensPx.toFloat(), s.partnerInitial, s.partnerColorIndex, near, dimAll || s.paused)
        rv.setImageViewBitmap(R.id.lens_me, meLens)
        rv.setImageViewBitmap(R.id.lens_partner, partnerLens)

        // 2. Render distance bead bitmap
        val beadText = when {
            s.refreshing -> "…"
            s.paused -> "paused"
            !s.paired -> if (size.isSingleColumn) "pair" else "add partner"
            s.distanceM == null -> if (size.isSingleColumn) "no fix" else "no fix yet"
            else -> DistanceMath.formatDistance(s.distanceM)
        }
        rv.setImageViewBitmap(R.id.bead, bead(context, beadText, near, beadH))

        // 3. Bridge line styling
        rv.setInt(R.id.bridge_line, "setBackgroundColor", if (near) 0x99FF7A9C.toInt() else bridgeColor)

        // 4. Status chip (only for layouts containing R.id.status_chip: 1x3, 2x3, 4x3)
        if (size != WidgetSize.SIZE_1x2) {
            val statusDisplay = if (!s.paired) "Pair up" else status.displayLabel
            val statusColor = when (status) {
                ConnectionStatus.Live -> 0xFF4ADE80.toInt()      // Emerald
                ConnectionStatus.Nearby -> 0xFFFF7A9C.toInt()    // Rose/Coral
                ConnectionStatus.Connected -> 0xE6FFFFFF.toInt() // Crisp white
                ConnectionStatus.Paused -> 0xB3FFFFFF.toInt()    // Muted
                ConnectionStatus.LastSeen -> 0x80FFFFFF.toInt()  // Dimmed
            }
            rv.setTextViewText(R.id.status_chip, statusDisplay)
            rv.setTextColor(R.id.status_chip, if (!s.paired) 0x99FFFFFF.toInt() else statusColor)
        }

        // 5. Large distance readout (only for layout containing R.id.distance_readout: 4x3)
        if (size == WidgetSize.SIZE_4x3) {
            val distanceReadoutText = when {
                s.refreshing -> "Refreshing…"
                s.paused -> "Sharing paused"
                !s.paired -> "Ready to pair"
                s.distanceM != null -> DistanceMath.formatDistance(s.distanceM)
                else -> "Finding location…"
            }
            rv.setTextViewText(R.id.distance_readout, distanceReadoutText)
            rv.setTextColor(R.id.distance_readout, 0xFFFFFFFF.toInt())
        }

        // 6. Footer text and color
        val footer = footerText(s, size, dimAll, now)
        rv.setTextViewText(R.id.footer, footer)
        val footerColor = when {
            s.paused -> 0xB3FFFFFF.toInt()
            near -> 0xFFFFB3C6.toInt()
            else -> 0xD9FFFFFF.toInt()
        }
        rv.setTextColor(R.id.footer, footerColor)

        // 7. Interactive PendingIntents
        rv.setOnClickPendingIntent(
            R.id.widget_root,
            BaseSpectaclesWidgetProvider.refreshPendingIntent(context),
        )
        rv.setOnClickPendingIntent(
            R.id.footer,
            BaseSpectaclesWidgetProvider.openAppPendingIntent(context),
        )

        return rv
    }

    /** Compatibility helper for binary compact flag. */
    fun remoteViews(context: Context, s: WidgetSnapshot, compact: Boolean): RemoteViews =
        remoteViews(context, s, if (compact) WidgetSize.SIZE_2x3 else WidgetSize.SIZE_4x3)

    private fun footerText(s: WidgetSnapshot, size: WidgetSize, stale: Boolean, now: Long): String {
        return when {
            s.refreshing -> "refreshing…"
            s.paused -> if (size == WidgetSize.SIZE_1x2) "paused" else "sharing paused — tap to resume"
            !s.paired -> if (size == WidgetSize.SIZE_1x2) "tap to pair" else "open Closer to pair up"
            s.distanceM == null -> if (size == WidgetSize.SIZE_1x2) "waiting…" else "waiting for a location…"
            size == WidgetSize.SIZE_1x2 -> s.resolveStatus(now)
            stale || s.connectionStatus(now) == ConnectionStatus.LastSeen ->
                "them: last seen " + relative(s.partnerUpdatedAtMs, now)
            s.nearby -> "Nearby 💞 · within ${s.radiusM} m"
            else -> "updated " + relative(s.myUpdatedAtMs, now)
        }
    }

    private fun relative(ts: Long?, now: Long): String =
        if (ts == null) "—" else DistanceMath.relativeTime(ts, now)

    // ---- tactile matte bitmaps ----------------------------------------------

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

        // Subtle matte micro-border
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = if (glowing) 0x66FF375F.toInt() else 0x2EFFFFFF
            alpha = if (dimmed) 90 else 180
        }
        c.drawCircle(size / 2f, size / 2f, r, ring)

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
                color = 0xFFFF375F.toInt() // Solid Apple Rose
            } else {
                color = 0xFFF2F4F7.toInt()
            }
        }
        c.drawRoundRect(rect, h / 2f, h / 2f, pill)

        // Hairline for tactile matte edge
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
