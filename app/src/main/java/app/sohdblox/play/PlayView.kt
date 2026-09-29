package app.sohdblox.play

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class PlayView(context: Context) : View(context) {
    var title: String = "Sohdblox"
    var onLeave: (() -> Unit)? = null
    private var px = 0f
    private var pz = 8f
    private var yaw = 0f
    private var left = false
    private var right = false
    private var forward = false
    private var back = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val last = longArrayOf(System.nanoTime())

    init {
        keepScreenOn = true
        post(object : Runnable {
            override fun run() {
                val now = System.nanoTime()
                val dt = ((now - last[0]) / 1_000_000_000f).coerceAtMost(0.05f)
                last[0] = now
                val f = (if (forward) 1 else 0) - (if (back) 1 else 0)
                val r = (if (right) 1 else 0) - (if (left) 1 else 0)
                val fx = kotlin.math.sin(yaw)
                val fz = -kotlin.math.cos(yaw)
                px += (fx * f + kotlin.math.cos(yaw) * r) * 9f * dt
                pz += (fz * f + kotlin.math.sin(yaw) * r) * 9f * dt
                invalidate()
                postOnAnimation(this)
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.parseColor("#0B1220"))
        paint.color = Color.parseColor("#243044")
        canvas.drawRect(0f, h * 0.62f, w, h, paint)
        fun project(x: Float, y: Float, z: Float): FloatArray? {
            val dx = x - px
            val dy = y - 1.1f
            val dz = z - pz
            val c = kotlin.math.cos(-yaw)
            val s = kotlin.math.sin(-yaw)
            val rx = dx * c - dz * s
            val rz = dx * s + dz * c
            if (rz < 0.4f) return null
            val fov = 420f
            return floatArrayOf(w / 2 + rx * fov / rz, h / 2 - dy * fov / rz, fov / rz)
        }
        val objs = arrayOf(
            floatArrayOf(0f, 0f, 0f, Color.parseColor("#243044").toFloat(), 80f),
            floatArrayOf(0f, 1f, -16f, Color.parseColor("#C98A4A").toFloat(), 28f),
            floatArrayOf(-12f, 1f, -8f, Color.parseColor("#2F8F4E").toFloat(), 20f),
            floatArrayOf(0f, 2f, -8f, Color.parseColor("#FFD166").toFloat(), 16f)
        )
        for (o in objs) {
            val p = project(o[0], o[1], o[2]) ?: continue
            paint.color = o[3].toInt()
            val sz = (o[4] * p[2] * 0.04f).coerceAtLeast(6f)
            canvas.drawRect(p[0] - sz / 2, p[1] - sz / 2, p[0] + sz / 2, p[1] + sz / 2, paint)
        }
        paint.color = Color.parseColor("#3EE0B4")
        canvas.drawRect(w / 2 - 8, h * 0.62f, w / 2 + 8, h * 0.62f + 28, paint)
        paint.color = Color.WHITE
        paint.textSize = 36f
        canvas.drawText(title, 32f, 64f, paint)
        paint.textSize = 28f
        canvas.drawText("X خروج", 32f, 110f, paint)
        canvas.drawText("حرك من الشاشة", 32f, h - 48f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        if (x < 180 && y < 140 && event.action == MotionEvent.ACTION_UP) {
            onLeave?.invoke()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                left = x < width * 0.25f
                right = x > width * 0.75f
                forward = y < height * 0.55f && x in width * 0.25f..width * 0.75f
                back = y > height * 0.75f
                if (event.historySize > 0) yaw += (x - event.getHistoricalX(0)) * 0.006f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                left = false; right = false; forward = false; back = false
            }
        }
        return true
    }
}
