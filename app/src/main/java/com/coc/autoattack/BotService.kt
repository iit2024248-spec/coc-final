package com.coc.autoattack

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class BotService : AccessibilityService() {
    companion object { @Volatile var inst: BotService? = null }

    private lateinit var wm: WindowManager
    private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val shotExec = Executors.newSingleThreadExecutor()
    private var job: Job? = null
    private var icon: TextView? = null
    private var panel: LinearLayout? = null
    private var calib: CalibView? = null
    private var calibrating = false
    private var iconX = 40
    private var iconY = 300
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        inst = this
        Cfg.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showIcon()
    }

    override fun onDestroy() {
        job?.cancel()
        removeAll()
        inst = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun screen(): Rect = wm.maximumWindowMetrics.bounds

    private fun params(w: Int, h: Int, full: Boolean = false): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (full) flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            if (full && Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    // ---------------- floating UI ----------------

    fun showIcon() {
        ui.launch {
            if (icon != null) return@launch
            val tv = TextView(this@BotService).apply {
                text = "⚔"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC1565C0.toInt()) }
            }
            val p = params(dp(52), dp(52)).apply { x = iconX; y = iconY }
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            tv.setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> { sx = ev.rawX; sy = ev.rawY; ox = p.x; oy = p.y; moved = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - sx; val dy = ev.rawY - sy
                        if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true
                        if (moved) { p.x = ox + dx.toInt(); p.y = oy + dy.toInt(); iconX = p.x; iconY = p.y; wm.updateViewLayout(tv, p) }
                        true
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) togglePanel(); true }
                    else -> false
                }
            }
            wm.addView(tv, p)
            icon = tv
        }
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        calibrating = false
        menu()
    }

    private fun closePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    private fun setPanel(text: String, vararg acts: Pair<String, () -> Unit>) {
        val p = panel ?: LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply { setColor(0xDD222222.toInt()); cornerRadius = dp(10).toFloat() }
            wm.addView(this, params(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { x = iconX + dp(58); y = iconY })
            panel = this
        }
        p.removeAllViews()
        p.addView(TextView(this).apply { this.text = text; setTextColor(Color.WHITE); textSize = 12f; setPadding(0, 0, 0, dp(4)) })
        for ((label, fn) in acts) {
            p.addView(Button(this).apply {
                this.text = label; textSize = 12f; minHeight = 0; minimumHeight = dp(36)
                setOnClickListener { fn() }
            })
        }
    }

    private fun menu(info: String = "Ready") {
        setPanel(info,
            "Start" to { start() },
            "Stop" to { stop() },
            "Calibrate" to { calibrate(0) },
            "Close" to { closePanel() },
            "Exit icon" to { exitAll() })
    }

    private fun note(s: String) {
        ui.launch { if (!calibrating) (panel?.getChildAt(0) as? TextView)?.text = s }
    }

    private fun exitAll() { job?.cancel(); removeAll() }

    private fun removeAll() {
        closePanel()
        icon?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        icon = null
        calib?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        calib = null
    }

    // ---------------- calibration ----------------

    private fun calibrate(gi: Int) {
        calibrating = true
        if (gi >= Cfg.groups.size) { calibrating = false; menu("Calibration saved ✓"); return }
        val g = Cfg.groups[gi]
        setPanel("Step ${gi + 1}/${Cfg.groups.size}: go to\n${g.title}\nthen press SNAP",
            "Snap" to { snap(gi) },
            "Cancel" to { calibrating = false; menu() })
    }

    private fun snap(gi: Int) {
        panel?.visibility = View.INVISIBLE
        icon?.visibility = View.INVISIBLE
        ui.launch {
            delay(450)
            val bmp = shot()
            panel?.visibility = View.VISIBLE
            icon?.visibility = View.VISIBLE
            if (bmp == null) { calibrate(gi); return@launch }
            showCalib(bmp, gi)
        }
    }

    private fun showCalib(bmp: Bitmap, gi: Int) {
        val g = Cfg.groups[gi]
        var idx = 0
        val v = CalibView(this, bmp)
        v.prompt = g.steps[0].prompt
        v.onTap = { fx, fy ->
            Cfg.setPt(g.steps[idx].key, fx, fy)
            v.marks.add(PointF(fx, fy))
            idx++
            if (idx >= g.steps.size) {
                try { wm.removeView(v) } catch (_: Exception) {}
                calib = null
                calibrate(gi + 1)
            } else { v.prompt = g.steps[idx].prompt; v.invalidate() }
        }
        v.onCancel = {
            try { wm.removeView(v) } catch (_: Exception) {}
            calib = null; calibrating = false; menu()
        }
        val r = screen()
        wm.addView(v, params(r.width(), r.height(), true))
        calib = v
    }

    // ---------------- screen + gestures ----------------

    private suspend fun shot(): Bitmap? = suspendCancellableCoroutine { c ->
        takeScreenshot(Display.DEFAULT_DISPLAY, shotExec, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                var out: Bitmap? = null
                try {
                    val hb = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                    out = hb?.copy(Bitmap.Config.ARGB_8888, false)
                    hb?.recycle()
                } catch (_: Exception) {}
                try { r.hardwareBuffer.close() } catch (_: Exception) {}
                if (c.isActive) c.resume(out)
            }
            override fun onFailure(errorCode: Int) { if (c.isActive) c.resume(null) }
        })
    }

    private suspend fun gesture(g: GestureDescription): Boolean = suspendCancellableCoroutine { c ->
        val ok = dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (c.isActive) c.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (c.isActive) c.resume(false) }
        }, null)
        if (!ok && c.isActive) c.resume(false)
    }

    private fun stroke(p: PointF, start: Long, dur: Long): GestureDescription.StrokeDescription {
        val r = screen()
        val path = Path().apply { moveTo(p.x * r.width(), p.y * r.height()) }
        return GestureDescription.StrokeDescription(path, start, dur)
    }

    private suspend fun tapP(p: PointF) {
        gesture(GestureDescription.Builder().addStroke(stroke(p, 0, 40)).build())
    }

    // ---------------- loot reading ----------------

    private suspend fun recognize(b: Bitmap): String = suspendCancellableCoroutine { c ->
        recognizer.process(InputImage.fromBitmap(b, 0))
            .addOnSuccessListener { if (c.isActive) c.resume(it.text) }
            .addOnFailureListener { if (c.isActive) c.resume("") }
    }

    private suspend fun readLoot(bmp: Bitmap, a: String, b: String): String? {
        val p1 = Cfg.pt(a)!!; val p2 = Cfg.pt(b)!!
        val x0 = (minOf(p1.x, p2.x) * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val y0 = (minOf(p1.y, p2.y) * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val x1 = (maxOf(p1.x, p2.x) * bmp.width).toInt().coerceIn(x0 + 1, bmp.width)
        val y1 = (maxOf(p1.y, p2.y) * bmp.height).toInt().coerceIn(y0 + 1, bmp.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 6 || h < 6) return null
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, x0, y0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val luma = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            px[i] = if (luma > 165) Color.BLACK else Color.WHITE   // bright text -> black on white
        }
        val pad = 12
        val out = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        out.setPixels(px, 0, w, pad, pad, w, h)
        val text = recognize(out)
        out.recycle()
        val d = text.map {
            when (it) {
                'O', 'o', 'D' -> '0'
                'l', 'I', '|', 'i' -> '1'
                'S', 's' -> '5'
                'B' -> '8'
                'Z' -> '2'
                else -> it
            }
        }.filter { it.isDigit() }.joinToString("")
        return d.ifEmpty { null }
    }

    // digit-count shortcut: more digits => bigger; same digits => lexicographic compare
    private fun meets(d: String, min: String) =
        d.length > min.length || (d.length == min.length && d >= min)

    // ---------------- attack loop ----------------

    private fun start() {
        if (!Cfg.ready()) { note("Calibrate first"); return }
        if (job?.isActive == true) return
        job = bg.launch {
            try { loop() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { note("Error: ${e.message}") }
            finally { if (isActive) note("Stopped") }
        }
        note("Running…")
    }

    private fun stop() {
        job?.cancel()
        note("Stopped")
    }

    private fun deployPoints(): List<PointF> {
        val c = listOf(Cfg.pt("top")!!, Cfg.pt("right")!!, Cfg.pt("bottom")!!, Cfg.pt("left")!!)
        val cx = c.map { it.x }.average().toFloat(); val cy = c.map { it.y }.average().toFloat()
        val out = ArrayList<PointF>()
        val per = 8
        for (i in 0 until 4) {
            val a = c[i]; val b = c[(i + 1) % 4]
            for (k in 0 until per) {
                val t = (k + 0.5f) / per
                var x = a.x + (b.x - a.x) * t
                var y = a.y + (b.y - a.y) * t
                x += (x - cx) * 0.01f; y += (y - cy) * 0.01f
                out.add(PointF(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)))
            }
        }
        return out
    }

    private suspend fun loop() {
        val minG = Cfg.minGold.toString()
        val minE = Cfg.minElixir.toString()
        var attacks = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            tapP(Cfg.pt("attack")!!); delay(900)
            tapP(Cfg.pt("find")!!); delay(Cfg.loadMs.toLong())

            // scouting
            var scans = 0; var blank = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val bmp = shot()
                if (bmp == null) { delay(200); continue }
                val (g, e) = coroutineScope {
                    val a = async { readLoot(bmp, "goldTL", "goldBR") }
                    val b = async { readLoot(bmp, "elxTL", "elxBR") }
                    Pair(a.await(), b.await())
                }
                bmp.recycle()
                scans++
                if (g == null || e == null) {
                    blank++
                    note("Scan $scans: can't read loot ($blank)")
                    if (blank < 6) { delay(350); continue }
                    blank = 0
                } else if (meets(g, minG) && meets(e, minE)) {
                    note("Attack #${attacks + 1}: G $g / E $e")
                    break
                } else {
                    note("Scan $scans: G $g / E $e - skip")
                }
                tapP(Cfg.pt("next")!!); delay(Cfg.nextMs.toLong())
            }

            // deploy
            val t0 = System.currentTimeMillis()
            tapP(Cfg.pt("troop")!!); delay(150)
            val pts = deployPoints()
            val chunk = minOf(GestureDescription.getMaxStrokeCount(), 10)
            var remaining = Cfg.taps
            var i = 0
            while (remaining > 0) {
                val n = minOf(chunk, remaining)
                val b = GestureDescription.Builder()
                for (k in 0 until n) b.addStroke(stroke(pts[i++ % pts.size], k * 35L, 10))
                gesture(b.build())
                remaining -= n
            }

            // wait, surrender, home
            val left = Cfg.attackSec * 1000L - (System.currentTimeMillis() - t0)
            if (left > 0) delay(left)
            tapP(Cfg.pt("surrender")!!); delay(700)
            tapP(Cfg.pt("ok")!!); delay(1800)
            tapP(Cfg.pt("home")!!); delay(Cfg.homeMs.toLong())
            attacks++
            note("Done $attacks attacks")
        }
    }
}
