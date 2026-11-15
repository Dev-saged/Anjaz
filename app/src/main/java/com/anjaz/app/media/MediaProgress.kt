package com.anjaz.app.media

import androidx.compose.runtime.mutableStateMapOf

/** نسبة تنزيل الوسائط الجارية بحسب معرّف الملف، تقرؤها الفقاعات لتعرض الدائرة. */
object MediaProgress {
    val percent = mutableStateMapOf<String, Int>()
}
