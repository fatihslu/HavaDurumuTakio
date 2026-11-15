package com.example.havadurumutakip.export

import com.example.havadurumutakip.model.DailySummary
import com.example.havadurumutakip.model.WeatherMeasurement
import com.example.havadurumutakip.model.WeatherMetric
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExcelExporter {

    private sealed class CellValue {
        data class Text(val value: String) : CellValue()
        data class Number(val value: Double) : CellValue()
    }

    fun writeXlsx(
        outputStream: OutputStream,
        summaries: List<DailySummary>,
        measurements: List<WeatherMeasurement>,
        selectedMetrics: Set<WeatherMetric>
    ) {
        val orderedMetrics = WeatherMetric.entries.filter { it in selectedMetrics }

        val sheet1Rows = mutableListOf<List<CellValue>>()
        sheet1Rows.add(
            listOf(
                CellValue.Text("Tarih"),
                CellValue.Text("Konum"),
                CellValue.Text("Min Sicaklik (C)"),
                CellValue.Text("Ortalama Sicaklik (C)"),
                CellValue.Text("Max Sicaklik (C)"),
                CellValue.Text("Toplam Yagis (mm)"),
                CellValue.Text("Ort Bulutluluk (%)"),
                CellValue.Text("Max UV"),
                CellValue.Text("Olcum Sayisi")
            )
        )
        for (s in summaries) {
            sheet1Rows.add(
                listOf(
                    CellValue.Text(s.dateKey),
                    CellValue.Text(s.locationName),
                    CellValue.Number(round2(s.minTemp)),
                    CellValue.Number(round2(s.avgTemp)),
                    CellValue.Number(round2(s.maxTemp)),
                    CellValue.Number(round2(s.totalPrecipitation)),
                    CellValue.Number(s.avgCloudCover.toDouble()),
                    CellValue.Number(round2(s.maxUvIndex)),
                    CellValue.Number(s.measurementCount.toDouble())
                )
            )
        }

        val sheet2Rows = mutableListOf<List<CellValue>>()
        val header2 = mutableListOf(
            CellValue.Text("Tarih"),
            CellValue.Text("Saat"),
            CellValue.Text("Konum")
        )
        for (metric in orderedMetrics) {
            header2.add(CellValue.Text(metric.excelHeader))
        }
        sheet2Rows.add(header2)

        for (m in measurements) {
            val extra = m.extraObj()
            val row = mutableListOf<CellValue>(
                CellValue.Text(m.dateKey),
                CellValue.Text(m.timeLabel),
                CellValue.Text(m.locationName)
            )
            for (metric in orderedMetrics) {
                val num = metric.excelNumberOrNull(m, extra)
                if (num != null) {
                    row.add(CellValue.Number(round2(num)))
                } else {
                    row.add(CellValue.Text(metric.formatDisplay(m, extra)))
                }
            }
            sheet2Rows.add(row)
        }

        val sheet2Widths = mutableListOf(14, 10, 32)
        orderedMetrics.forEach { metric ->
            sheet2Widths.add(maxOf(14, metric.excelHeader.length + 3))
        }

        ZipOutputStream(outputStream).use { zos ->
            writeEntry(zos, "[Content_Types].xml", contentTypesXml())
            writeEntry(zos, "_rels/.rels", rootRelsXml())
            writeEntry(zos, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            writeEntry(zos, "xl/workbook.xml", workbookXml())
            writeEntry(
                zos,
                "xl/worksheets/sheet1.xml",
                worksheetXml(sheet1Rows, listOf(14, 34, 18, 22, 18, 20, 18, 12, 14))
            )
            writeEntry(
                zos,
                "xl/worksheets/sheet2.xml",
                worksheetXml(sheet2Rows, sheet2Widths)
            )
        }
    }

    private fun round2(v: Double): Double =
        String.format(Locale.US, "%.2f", v).toDouble()

    private fun colLetters(colIndex: Int): String {
        var n = colIndex
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun writeEntry(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun contentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        </Types>
    """.trimIndent()

    private fun rootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbookRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbookXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="Gunluk Ozet" sheetId="1" r:id="rId1"/>
            <sheet name="Saatlik Kayitlar" sheetId="2" r:id="rId2"/>
          </sheets>
        </workbook>
    """.trimIndent()

    private fun worksheetXml(rows: List<List<CellValue>>, colWidths: List<Int>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
        sb.append("  <cols>\n")
        colWidths.forEachIndexed { idx, w ->
            val colNum = idx + 1
            sb.append("    <col min=\"$colNum\" max=\"$colNum\" width=\"$w\" customWidth=\"1\"/>\n")
        }
        sb.append("  </cols>\n")
        sb.append("  <sheetData>\n")
        rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            sb.append("    <row r=\"$rowNum\">\n")
            row.forEachIndexed { cIdx, cell ->
                val ref = "${colLetters(cIdx)}$rowNum"
                when (cell) {
                    is CellValue.Text -> {
                        sb.append("      <c r=\"$ref\" t=\"inlineStr\"><is><t>${escapeXml(cell.value)}</t></is></c>\n")
                    }
                    is CellValue.Number -> {
                        val numStr = if (cell.value % 1.0 == 0.0) {
                            cell.value.toLong().toString()
                        } else {
                            String.format(Locale.US, "%.2f", cell.value)
                        }
                        sb.append("      <c r=\"$ref\"><v>$numStr</v></c>\n")
                    }
                }
            }
            sb.append("    </row>\n")
        }
        sb.append("  </sheetData>\n")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    if (ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }
}
