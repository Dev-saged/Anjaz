package com.anjaz.app

import com.anjaz.app.core.Oklch

object BrandConfig {
    const val NAME = "إنجاز"
    const val NAME_EN = "Anjaz"
    const val TAGLINE = "منظومة الإدارة والمتابعة الميدانية"

    val enabledModules = listOf("tasks", "chat", "sos", "meetings", "reports")

    const val DYNAMIC_COLOR = false

    // عنوان الـ Worker بعد النشر؛ فارغ = وضع محلي بلا خادم
    const val WORKER_URL = ""
    const val EMAIL_DOMAIN = "users.anjaz.local"

    data class Pair(val light: Oklch, val dark: Oklch)

    object Palette {
        val navy = Pair(Oklch(.30, .075, 255.0), Oklch(.34, .08, 255.0))
        val navyHigh = Pair(Oklch(.46, .08, 255.0), Oklch(.52, .08, 255.0))
        val emerald = Pair(Oklch(.72, .16, 158.0), Oklch(.74, .16, 158.0))
        val emeraldDeep = Pair(Oklch(.50, .13, 158.0), Oklch(.78, .14, 158.0))
        val amber = Pair(Oklch(.78, .15, 70.0), Oklch(.80, .15, 70.0))
        val amberDeep = Pair(Oklch(.48, .12, 60.0), Oklch(.82, .13, 70.0))
        val red = Pair(Oklch(.60, .21, 25.0), Oklch(.66, .20, 25.0))
        val redDeep = Pair(Oklch(.48, .19, 25.0), Oklch(.80, .12, 25.0))
        val surface = Pair(Oklch(.98, .005, 250.0), Oklch(.17, .035, 255.0))
        val card = Pair(Oklch(1.0, 0.0, 0.0), Oklch(.22, .04, 255.0))
        val ink = Pair(Oklch(.20, .04, 255.0), Oklch(.96, .01, 250.0))
        val mute = Pair(Oklch(.50, .03, 255.0), Oklch(.74, .025, 255.0))
        val line = Pair(Oklch(.91, .01, 250.0), Oklch(.30, .035, 255.0))
        val onNavy = Pair(Oklch(.98, .005, 250.0), Oklch(.98, .005, 250.0))
        val onEmerald = Pair(Oklch(.20, .04, 255.0), Oklch(.20, .04, 255.0))
    }
}
