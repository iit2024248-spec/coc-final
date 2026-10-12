package com.coc.autoattack

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.init(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        val sv = ScrollView(this).apply { addView(col) }
        setContentView(sv)

        col.addView(TextView(this).apply { text = "CoC Auto Attack"; textSize = 22f })
        status = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(8)) }
        col.addView(status)

        fun field(label: String, value: String): EditText {
            col.addView(TextView(this).apply { text = label; setPadding(0, dp(10), 0, 0) })
            val e = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(value) }
            col.addView(e, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return e
        }
        val gold = field("Minimum GOLD (e.g. 10000000)", Cfg.minGold.toString())
        val elx = field("Minimum ELIXIR (e.g. 10000000)", Cfg.minElixir.toString())
        val sec = field("Attack duration before surrender (seconds)", Cfg.attackSec.toString())
        val taps = field("Number of deploy taps (≈ number of Super Goblins)", Cfg.taps.toString())
        val next = field("Wait after pressing Next (ms)", Cfg.nextMs.toString())
        val load = field("Wait after Find a Match (ms)", Cfg.loadMs.toString())
        val home = field("Wait after Return Home (ms)", Cfg.homeMs.toString())

        fun save() {
            gold.text.toString().toLongOrNull()?.let { Cfg.minGold = it }
            elx.text.toString().toLongOrNull()?.let { Cfg.minElixir = it }
            sec.text.toString().toIntOrNull()?.let { Cfg.attackSec = it }
            taps.text.toString().toIntOrNull()?.let { Cfg.taps = it }
            next.text.toString().toIntOrNull()?.let { Cfg.nextMs = it }
            load.text.toString().toIntOrNull()?.let { Cfg.loadMs = it }
            home.text.toString().toIntOrNull()?.let { Cfg.homeMs = it }
        }

        col.addView(Button(this).apply {
            text = "1. Enable accessibility service"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        col.addView(Button(this).apply {
            text = "2. Save settings & show floating icon"
            setOnClickListener {
                save()
                val s = BotService.inst
                if (s == null) Toast.makeText(this@MainActivity, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
                else { s.showIcon(); Toast.makeText(this@MainActivity, "Saved. Open Clash of Clans and tap the floating icon.", Toast.LENGTH_LONG).show() }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (BotService.inst != null) "Accessibility service: ON ✓" else "Accessibility service: OFF (do step 1)"
    }
}
