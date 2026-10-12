package com.coc.autoattack

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class CalibView(ctx: Context, private val bmp: Bitmap) : View(ctx) {
    var prompt = ""
    val marks = ArrayList<PointF>()
    var onTap: ((Float, Float) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        c.drawBitmap(bmp, null, Rect(0, 0, width, height), null)
        paint.style = Paint.Style.FILL
        paint.color = 0xCC000000.toInt()
        c.drawRect(0f, 0f, width.toFloat(), 48 * d, paint)
        paint.color = Color.WHITE
        paint.textSize = 15 * d
        c.drawText(prompt, 12 * d, 30 * d, paint)
        paint.color = 0xFFD32F2F.toInt()
        c.drawRect(width - 56 * d, 0f, width.toFloat(), 48 * d, paint)
        paint.color = Color.WHITE
        c.drawText("X", width - 34 * d, 31 * d, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3 * d
        paint.color = Color.RED
        for (m in marks) c.drawCircle(m.x * width, m.y * height, 10 * d, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            if (e.x > width - 56 * d && e.y < 48 * d) onCancel?.invoke()
            else onTap?.invoke(e.x / width, e.y / height)
        }
        return true
    }
}
