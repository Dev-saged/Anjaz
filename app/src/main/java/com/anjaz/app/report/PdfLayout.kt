package com.anjaz.app.report

data class PdfText(val text: String, val x: Float, val y: Float, val bold: Boolean = false, val size: Float = 10f, val center: Boolean = false)
data class PdfRule(val y: Float)
data class PdfPage(val number: Int, val texts: List<PdfText>, val rules: List<PdfRule>)

// تخطيط الصفحات منفصل عن الرسم ليُختبر على JVM؛ العمود الأول أقصى اليمين (عربي)
object PdfLayout {
    const val W = 595f
    const val H = 842f
    const val M = 36f
    const val ROW = 22f

    fun build(table: ReportTable, measure: (String, Boolean) -> Float): List<PdfPage> {
        val colW = (W - 2 * M) / table.headers.size
        val pages = mutableListOf<PdfPage>()
        var texts = mutableListOf<PdfText>()
        var rules = mutableListOf<PdfRule>()
        var y = 0f

        fun clip(s: String, bold: Boolean): String {
            val max = colW - 6
            if (measure(s, bold) <= max) return s
            var n = s.length
            while (n > 0 && measure(s.take(n) + "…", bold) > max) n--
            return s.take(n) + "…"
        }

        fun start() {
            texts = mutableListOf(); rules = mutableListOf()
            y = M + 6
            if (pages.isEmpty()) {
                texts += PdfText(table.title, W - M, y + 14, bold = true, size = 18f); y += 30
                texts += PdfText(table.subtitle, W - M, y); y += 22
            }
            table.headers.forEachIndexed { i, h -> texts += PdfText(clip(h, true), W - M - i * colW - 3, y, bold = true) }
            y += 6; rules += PdfRule(y); y += ROW - 6
        }

        fun end() {
            texts += PdfText("${pages.size + 1}", W / 2, H - 18f, size = 9f, center = true)
            pages += PdfPage(pages.size + 1, texts, rules)
        }

        start()
        table.rows.forEach { r ->
            if (y > H - M - 2 * ROW) { end(); start() }
            r.forEachIndexed { i, v -> texts += PdfText(clip(v, false), W - M - i * colW - 3, y) }
            y += 4; rules += PdfRule(y); y += ROW - 4
        }
        if (y > H - M - (table.summary.size + 2) * ROW) { end(); start() }
        y += 10
        table.summary.forEach { (k, v) ->
            texts += PdfText(k, W - M, y, bold = true); texts += PdfText(v, W - M - 160f, y); y += ROW - 4
        }
        end()
        return pages
    }
}
