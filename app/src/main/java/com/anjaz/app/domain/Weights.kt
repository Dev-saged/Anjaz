package com.anjaz.app.domain

/** الأوزان النسبية والتوزيع العادل لكمية واردة على الجهات حسب عدد الأسر. */
object Weights {
    /** نسبة مئوية بمنزلة عشرية واحدة: 12.5% */
    fun percent(part: Int, total: Int): String {
        if (total <= 0) return "0%"
        val tenths = Math.round(part * 1000.0 / total)
        return "${tenths / 10}.${tenths % 10}%".replace(".0%", "%")
    }

    /**
     * طريقة أكبر الباقي (هاميلتون): كل جهة تأخذ الجزء الصحيح من حصتها، ثم توزَّع الوحدات المتبقية
     * على أصحاب أكبر الكسور. المجموع يساوي الكمية دائماً، ولا تأخذ جهة وزنها صفر شيئاً
     * إلا إذا كانت كل الأوزان صفراً فتوزَّع بالتساوي.
     */
    fun allocate(total: Long, weights: List<Int>): List<Long> {
        if (weights.isEmpty() || total <= 0) return List(weights.size) { 0L }
        val w = if (weights.all { it <= 0 }) List(weights.size) { 1 } else weights.map { it.coerceAtLeast(0) }
        val sum = w.sumOf { it.toLong() }
        val base = w.map { total * it / sum }
        var left = total - base.sum()
        val order = w.indices.sortedWith(
            compareByDescending<Int> { (total * w[it]) % sum }.thenByDescending { w[it] }.thenBy { it },
        )
        val out = base.toMutableList()
        for (i in order) {
            if (left <= 0) break
            if (w[i] == 0) continue
            out[i] = out[i] + 1
            left--
        }
        return out
    }
}
