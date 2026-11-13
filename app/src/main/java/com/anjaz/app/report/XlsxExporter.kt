package com.anjaz.app.report

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// ملف xlsx بسيط بلا مكتبات: ورقة واحدة من اليمين لليسار، نصوص مضمّنة
object XlsxExporter {
    fun write(table: ReportTable, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", WORKBOOK)
            entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            entry("xl/worksheets/sheet1.xml", sheet(table))
        }
    }

    private fun sheet(t: ReportTable): String {
        val sb = StringBuilder(HEAD)
        var r = 1
        fun row(cells: List<String>) {
            sb.append("<row r=\"$r\">")
            cells.forEachIndexed { i, v -> sb.append("<c r=\"${col(i)}$r\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>") }
            sb.append("</row>"); r++
        }
        row(listOf(t.title)); row(listOf(t.subtitle)); row(t.headers)
        t.rows.forEach(::row)
        r++
        t.summary.forEach { (k, v) -> row(listOf(k, v)) }
        return sb.append("</sheetData></worksheet>").toString()
    }

    fun col(i: Int): String = buildString {
        var n = i
        do { insert(0, 'A' + n % 26); n = n / 26 - 1 } while (n >= 0)
    }

    private fun esc(s: String) = s.map {
        when {
            it == '&' -> "&amp;"; it == '<' -> "&lt;"; it == '>' -> "&gt;"; it == '"' -> "&quot;"
            it.code < 32 && it != '\n' && it != '\t' -> ""
            else -> it.toString()
        }
    }.joinToString("")

    private const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews><cols><col min=\"1\" max=\"6\" width=\"22\" customWidth=\"1\"/></cols><sheetData>"
    private const val CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>"
    private const val ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
    private const val WORKBOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
    private const val WORKBOOK_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>"
}
