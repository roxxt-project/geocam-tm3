package id.geocam.tm3.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/** Burns the stamp into the bottom-left corner of a mutable bitmap. */
object StampRenderer {
    private val ACCENT = 0xFFFF9F1A.toInt()
    private val PANEL = 0x8C000000.toInt()

    fun draw(bitmap: Bitmap, lines: List<String>) {
        require(bitmap.isMutable) { "bitmap must be mutable" }
        val canvas = Canvas(bitmap)
        val shortSide = min(bitmap.width, bitmap.height).toFloat()

        val textSize = shortSide * 0.026f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, 0x99000000.toInt())
        }

        val margin = shortSide * 0.03f
        val pad = textSize * 0.7f
        val bar = textSize * 0.22f
        val lineHeight = textSize * 1.3f
        val maxTextWidth = bitmap.width - 2 * margin - 2 * pad - bar

        val wrapped = lines.flatMap { wrap(it, paint, maxTextWidth) }
        if (wrapped.isEmpty()) return

        val metrics = paint.fontMetrics
        val textWidth = wrapped.maxOf { paint.measureText(it) }
        val panelWidth = bar + 2 * pad + textWidth
        val panelHeight = 2 * pad + (metrics.descent - metrics.ascent) + (wrapped.size - 1) * lineHeight
        val left = margin
        val top = bitmap.height - margin - panelHeight
        val radius = pad * 0.6f

        canvas.drawRoundRect(
            RectF(left, top, left + panelWidth, top + panelHeight),
            radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL },
        )
        canvas.drawRoundRect(
            RectF(left, top, left + bar, top + panelHeight),
            bar / 2, bar / 2,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT },
        )

        val x = left + bar + pad
        var baseline = top + pad - metrics.ascent
        for (line in wrapped) {
            paint.color = if (line.startsWith(StampText.ZONE_PREFIX)) ACCENT else Color.WHITE
            canvas.drawText(line, x, baseline, paint)
            baseline += lineHeight
        }
    }

    /** Greedy word wrap; a single word longer than the width stays on its own line. */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val out = mutableListOf<String>()
        var current = ""
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                out += current
                current = word
            }
        }
        if (current.isNotEmpty()) out += current
        return out
    }
}
