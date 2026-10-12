package com.coc.autoattack

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PointF

class Step(val key: String, val prompt: String)
class Group(val title: String, val steps: List<Step>)

object Cfg {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    }

    var minGold: Long
        get() = sp.getLong("minGold", 10_000_000L)
        set(v) { sp.edit().putLong("minGold", v).apply() }
    var minElixir: Long
        get() = sp.getLong("minElixir", 10_000_000L)
        set(v) { sp.edit().putLong("minElixir", v).apply() }
    var attackSec: Int
        get() = sp.getInt("attackSec", 28)
        set(v) { sp.edit().putInt("attackSec", v).apply() }
    var taps: Int
        get() = sp.getInt("taps", 40)
        set(v) { sp.edit().putInt("taps", v).apply() }
    var nextMs: Int
        get() = sp.getInt("nextMs", 1500)
        set(v) { sp.edit().putInt("nextMs", v).apply() }
    var loadMs: Int
        get() = sp.getInt("loadMs", 3000)
        set(v) { sp.edit().putInt("loadMs", v).apply() }
    var homeMs: Int
        get() = sp.getInt("homeMs", 3500)
        set(v) { sp.edit().putInt("homeMs", v).apply() }

    fun pt(n: String): PointF? =
        if (sp.contains(n + "_x")) PointF(sp.getFloat(n + "_x", 0f), sp.getFloat(n + "_y", 0f)) else null

    fun setPt(n: String, x: Float, y: Float) {
        sp.edit().putFloat(n + "_x", x).putFloat(n + "_y", y).apply()
    }

    val groups = listOf(
        Group("HOME screen (village with the Attack button)", listOf(
            Step("attack", "Tap the ATTACK button"))),
        Group("The screen after tapping Attack (Find a Match button)", listOf(
            Step("find", "Tap FIND A MATCH"))),
        Group("SCOUTING screen (enemy base, loot + Next button)", listOf(
            Step("goldTL", "GOLD number: tap its TOP-LEFT corner"),
            Step("goldBR", "GOLD number: tap its BOTTOM-RIGHT corner"),
            Step("elxTL", "ELIXIR number: tap its TOP-LEFT corner"),
            Step("elxBR", "ELIXIR number: tap its BOTTOM-RIGHT corner"),
            Step("next", "Tap the NEXT button"))),
        Group("BATTLE just started. SELECT the Super Goblin first so the red zone shows", listOf(
            Step("troop", "Tap the SUPER GOBLIN slot"),
            Step("top", "Deploy edge: tap the TOP corner (outside red zone)"),
            Step("right", "Deploy edge: tap the RIGHT corner"),
            Step("bottom", "Deploy edge: tap the BOTTOM corner"),
            Step("left", "Deploy edge: tap the LEFT corner"),
            Step("surrender", "Tap the SURRENDER button"))),
        Group("SURRENDER confirmation pop-up (tap Surrender in game once to see it)", listOf(
            Step("ok", "Tap the OKAY / confirm button"))),
        Group("RESULT screen after the battle (Return Home button)", listOf(
            Step("home", "Tap RETURN HOME")))
    )

    fun ready(): Boolean = groups.all { g -> g.steps.all { pt(it.key) != null } }
}
