package com.anjaz.app.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

object PdfExporter {
    fun write(table: ReportTable, out: OutputStream) {
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
        fun paint(t: PdfText) = (if (t.bold) bold else body).also {
            it.textSize = t.size
            it.textAlign = if (t.center) Paint.Align.CENTER else Paint.Align.RIGHT
            it.color = if (t.size >= 18f) Color.rgb(16, 46, 82) else Color.BLACK
        }
        val pages = PdfLayout.build(table) { s, isBold -> (if (isBold) bold else body).also { it.textSize = 10f }.measureText(s) }
        val doc = PdfDocument()
        try {
            pages.forEach { p ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PdfLayout.W.toInt(), PdfLayout.H.toInt(), p.number).create())
                p.rules.forEach { page.canvas.drawLine(PdfLayout.M, it.y, PdfLayout.W - PdfLayout.M, it.y, line) }
                p.texts.forEach { page.canvas.drawText(it.text, it.x, it.y, paint(it)) }
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally { doc.close() }
    }
}
