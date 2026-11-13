package com.anjaz.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object AnjazIcons {
    val Home: ImageVector by lazy {
        build("home", false,
            "M4 11.2 12 4l8 7.2V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z")
    }

    val Tasks: ImageVector by lazy {
        build("tasks", false,
            "M8,4H16a4,4 0 0 1 4,4V16a4,4 0 0 1 -4,4H8a4,4 0 0 1 -4,-4V8a4,4 0 0 1 4,-4z",
            "m8.5 12.2 2.5 2.5 4.5-5")
    }

    val Chat: ImageVector by lazy {
        build("chat", false,
            "M5 5h14a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-7l-4.5 3.5V16H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z",
            "M8 9.5h8M8 12.5h5")
    }

    val More: ImageVector by lazy {
        build("more", false,
            "M4.6,12a1.4,1.4 0 1,0 2.8,0a1.4,1.4 0 1,0 -2.8,0z",
            "M10.6,12a1.4,1.4 0 1,0 2.8,0a1.4,1.4 0 1,0 -2.8,0z",
            "M16.6,12a1.4,1.4 0 1,0 2.8,0a1.4,1.4 0 1,0 -2.8,0z")
    }

    val Bell: ImageVector by lazy {
        build("bell", false,
            "M6.5 16.5V11a5.5 5.5 0 0 1 11 0v5.5l1.5 1.5H5z",
            "M10 20.5h4")
    }

    val Plus: ImageVector by lazy {
        build("plus", false,
            "M12 5v14M5 12h14")
    }

    val Sos: ImageVector by lazy {
        build("sos", false,
            "M12 3.5 21 19.5H3z",
            "M12 10v4.2",
            "M11.4,17a0.6,0.6 0 1,0 1.2,0a0.6,0.6 0 1,0 -1.2,0z")
    }

    val Search: ImageVector by lazy {
        build("search", false,
            "M4.5,11a6.5,6.5 0 1,0 13,0a6.5,6.5 0 1,0 -13,0z",
            "m16 16 4 4")
    }

    val Back: ImageVector by lazy {
        build("back", true,
            "m9.5 6 6 6-6 6")
    }

    val User: ImageVector by lazy {
        build("user", false,
            "M8.5,8.5a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0z",
            "M5 20c.8-3.6 3.6-5.5 7-5.5s6.2 1.9 7 5.5")
    }

    val Lock: ImageVector by lazy {
        build("lock", false,
            "M8,10.5H16a3,3 0 0 1 3,3V17a3,3 0 0 1 -3,3H8a3,3 0 0 1 -3,-3V13.5a3,3 0 0 1 3,-3z",
            "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5")
    }

    val Mic: ImageVector by lazy {
        build("mic", false,
            "M12,3.5H12a3,3 0 0 1 3,3V11.5a3,3 0 0 1 -3,3H12a3,3 0 0 1 -3,-3V6.5a3,3 0 0 1 3,-3z",
            "M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3")
    }

    val Send: ImageVector by lazy {
        build("send", false,
            "M20 4 10 14M20 4l-6.5 16-3-6-6-3z")
    }

    val Clip: ImageVector by lazy {
        build("clip", false,
            "m18.5 11-6.7 6.7a4 4 0 0 1-5.6-5.6l7-7a2.7 2.7 0 0 1 3.8 3.8l-7 7a1.3 1.3 0 0 1-1.9-1.9L14 8.7")
    }

    val Play: ImageVector by lazy {
        build("play", false,
            "M9 6.5v11l9-5.5z")
    }

    val Meet: ImageVector by lazy {
        build("meet", false,
            "M6.5,6.5H12.5a3,3 0 0 1 3,3V14.5a3,3 0 0 1 -3,3H6.5a3,3 0 0 1 -3,-3V9.5a3,3 0 0 1 3,-3z",
            "m15.5 11 5-3v8l-5-3z")
    }

    val Report: ImageVector by lazy {
        build("report", false,
            "M6 3.5h9l4 4V20H6z",
            "M9.5 15v-2.5M12.5 15v-5M15.5 15v-3.5")
    }

    val Map: ImageVector by lazy {
        build("map", false,
            "M12 21s6.5-5.6 6.5-11a6.5 6.5 0 0 0-13 0c0 5.4 6.5 11 6.5 11z",
            "M9.8,10a2.2,2.2 0 1,0 4.4,0a2.2,2.2 0 1,0 -4.4,0z")
    }

    val Shield: ImageVector by lazy {
        build("shield", false,
            "M12 3.5 19 6v5.5c0 4.3-2.8 7.3-7 9-4.2-1.7-7-4.7-7-9V6z",
            "m9 12 2.2 2.2L15.5 10")
    }

    val Log: ImageVector by lazy {
        build("log", false,
            "M8,3.5H16a3,3 0 0 1 3,3V17.5a3,3 0 0 1 -3,3H8a3,3 0 0 1 -3,-3V6.5a3,3 0 0 1 3,-3z",
            "M8.5 8.5h7M8.5 12h7M8.5 15.5h4")
    }

    val Cloud: ImageVector by lazy {
        build("cloud", false,
            "M7 18.5a4 4 0 0 1-.5-8 5.5 5.5 0 0 1 10.7-1A4.5 4.5 0 0 1 17 18.5z",
            "M12 11v5m0 0-2-2m2 2 2-2")
    }

    val Gear: ImageVector by lazy {
        build("gear", false,
            "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
            "M12 3.5v2.3M12 18.2v2.3M3.5 12h2.3M18.2 12h2.3M6 6l1.6 1.6M16.4 16.4 18 18M6 18l1.6-1.6M16.4 7.6 18 6")
    }

    val Loc: ImageVector by lazy {
        build("loc", false,
            "M8.8,12a3.2,3.2 0 1,0 6.4,0a3.2,3.2 0 1,0 -6.4,0z",
            "M4.5,12a7.5,7.5 0 1,0 15,0a7.5,7.5 0 1,0 -15,0z",
            "M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5")
    }

    val Check: ImageVector by lazy {
        build("check", false,
            "m6 12.5 4 4 8-9")
    }

    val Img: ImageVector by lazy {
        build("img", false,
            "M7,5H17a3,3 0 0 1 3,3V16a3,3 0 0 1 -3,3H7a3,3 0 0 1 -3,-3V8a3,3 0 0 1 3,-3z",
            "M7.4,10a1.6,1.6 0 1,0 3.2,0a1.6,1.6 0 1,0 -3.2,0z",
            "m5 17 4.5-4 3.5 3 2.5-2 4 3.5")
    }

    val all: Map<String, ImageVector> by lazy {
        mapOf("home" to Home, "tasks" to Tasks, "chat" to Chat, "more" to More, "bell" to Bell, "plus" to Plus, "sos" to Sos, "search" to Search, "back" to Back, "user" to User, "lock" to Lock, "mic" to Mic, "send" to Send, "clip" to Clip, "play" to Play, "meet" to Meet, "report" to Report, "map" to Map, "shield" to Shield, "log" to Log, "cloud" to Cloud, "gear" to Gear, "loc" to Loc, "check" to Check, "img" to Img)
    }

    private fun build(name: String, mirror: Boolean, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
            paths.forEach {
                addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
}
