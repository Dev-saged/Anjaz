package com.anjaz.app.domain

/** صيغ العدد العربية: 1 مخيم واحد، 2 مخيمان، 3-10 مخيمات، 11-99 مخيماً، والمئات بالمفرد. */
data class Noun(val one: String, val two: String, val few: String, val many: String, val none: String)

object Plural {
    val camp = Noun("مخيم واحد", "مخيمان", "مخيمات", "مخيماً", "لا مخيمات")
    val square = Noun("مربع واحد", "مربعان", "مربعات", "مربعاً", "لا مربعات")
    val family = Noun("أسرة واحدة", "أسرتان", "أسر", "أسرة", "لا أسر")
    val task = Noun("مهمة واحدة", "مهمتان", "مهام", "مهمة", "لا مهام")

    /** يعيد النص كاملاً؛ [num] لتنسيق الرقم (مثلاً عزله باتجاه LTR وفواصل الآلاف). */
    fun of(n: Int, w: Noun, num: (Int) -> String = { it.toString() }): String {
        val r = n % 100
        return when {
            n == 0 -> w.none
            n == 1 -> w.one
            n == 2 -> w.two
            r in 3..10 -> "${num(n)} ${w.few}"
            r in 11..99 -> "${num(n)} ${w.many}"
            else -> "${num(n)} ${w.one.substringBefore(' ')}"
        }
    }

    fun grouped(n: Int): String = "%,d".format(java.util.Locale.US, n)
}
