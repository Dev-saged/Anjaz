package com.anjaz.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DayMath {
    private val arLatinDigits: Locale = Locale.forLanguageTag("ar-u-nu-latn")

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun addDays(ms: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    fun weekday(ms: Long): String = SimpleDateFormat("EEE", arLatinDigits).format(Date(ms))

    fun dayOfMonth(ms: Long): String = SimpleDateFormat("d", arLatinDigits).format(Date(ms))

    fun time(ms: Long): String = SimpleDateFormat("HH:mm", arLatinDigits).format(Date(ms))

    fun dueLabel(deadline: Long?, now: Long): String? {
        if (deadline == null) return null
        val diff = (startOfDay(deadline) - startOfDay(now)) / DAY_MS
        val day = when {
            diff == 0L -> "اليوم"
            diff == 1L -> "غداً"
            diff == -1L -> "أمس"
            else -> SimpleDateFormat("d MMM", arLatinDigits).format(Date(deadline))
        }
        return "$day ${time(deadline)}"
    }

    private const val DAY_MS = 86_400_000L
}
