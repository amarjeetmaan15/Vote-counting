package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.entity.AttendanceStatus
import com.example.data.entity.Voter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

object ExcelReader {

    data class ParsedVoterData(
        val voters: List<Voter>,
        val wardsFound: List<Int>
    )

    fun parseExcelOrCsv(context: Context, uri: Uri): ParsedVoterData {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri).orEmpty().lowercase()
        val fileName = uri.lastPathSegment.orEmpty().lowercase()
        val isXlsx = fileName.endsWith(".xlsx") ||
            mimeType.contains("spreadsheetml") || mimeType.contains("excel")

        val rows = resolver.openInputStream(uri)?.use { input ->
            if (isXlsx) parseXlsx(input) else parseCsv(input)
        } ?: emptyList()

        return processRowsToVoters(rows)
    }

    private data class ColumnMap(
        val serial: Int = 0,
        val name: Int = 1,
        val relative: Int = 2,
        val house: Int = -1,
        val epic: Int = 3,
        val ward: Int = 4
    )

    private fun normalizeHeader(value: String): String =
        value.trim().lowercase()
            .replace("\uFEFF", "")
            .replace(Regex("[^a-z0-9\\u0900-\\u097F]+"), " ")
            .replace(Regex("\\s+"), " ")

    private fun headerKind(value: String): String? {
        val h = normalizeHeader(value)
        if (h.isBlank()) return null

        if (listOf("epic", "epic no", "epic number", "voter id", "voter identity",
                "identity card", "मतदाता पहचान", "पहचान पत्र", "निर्वाचक फोटो पहचान",
                "मतदाता परिचय").any { h.contains(it) }) return "epic"

        if (listOf("house", "house no", "house number", "घर नंबर", "घर संख्या",
                "मकान नंबर", "मकान संख्या").any { h.contains(it) }) return "house"

        if (listOf("ward", "ward no", "ward number", "वार्ड", "वार्ड नंबर",
                "वार्ड क्रमांक").any { h.contains(it) }) return "ward"

        if (listOf("father", "father name", "mother", "mother name", "husband",
                "husband name", "relative", "relation", "guardian", "पिता", "पिताजी",
                "माता", "माताजी", "पति", "अभिभावक", "रिश्तेदार").any { h.contains(it) }) return "relative"

        if (listOf("serial", "serial no", "serial number", "sr no", "sr number",
                "s no", "क्रमांक", "क्रम संख्या", "क्र सं", "क्र.सं", "अनुक्रमांक",
                "मतदाता क्रम").any { h.contains(it) }) return "serial"

        if (listOf("voter name", "name of voter", "elector name", "मतदाता का नाम",
                "मतदाता नाम", "निर्वाचक का नाम", "नाम").any { h == it || h.contains(it) }) return "name"

        return null
    }

    private fun processRowsToVoters(rows: List<List<String>>): ParsedVoterData {
        if (rows.isEmpty()) return ParsedVoterData(emptyList(), emptyList())

        // Look for a real header row in the first ten rows; voter rolls often have title rows above it.
        var headerIndex = -1
        var bestHeaderScore = 0
        for (i in 0 until minOf(rows.size, 10)) {
            val kinds = rows[i].mapNotNull(::headerKind).toSet()
            val score = kinds.size
            if (score > bestHeaderScore) {
                bestHeaderScore = score
                headerIndex = i
            }
        }

        val columns = if (bestHeaderScore >= 2) {
            val mapped = mutableMapOf<String, Int>()
            rows[headerIndex].forEachIndexed { index, cell ->
                val kind = headerKind(cell)
                if (kind != null && kind !in mapped) mapped[kind] = index
            }
            ColumnMap(
                serial = mapped["serial"] ?: 0,
                name = mapped["name"] ?: 1,
                relative = mapped["relative"] ?: 2,
                house = mapped["house"] ?: -1,
                epic = mapped["epic"] ?: if (mapped.containsKey("house")) 4 else 3,
                ward = mapped["ward"] ?: if (mapped.containsKey("house")) 5 else 4
            )
        } else {
            // Support both the existing five-column layout and the new six-column layout.
            val hasHouseColumn = rows.any { it.size >= 6 }
            if (hasHouseColumn) ColumnMap(house = 3, epic = 4, ward = 5) else ColumnMap()
        }

        val startIndex = if (headerIndex >= 0 && bestHeaderScore >= 2) headerIndex + 1 else 0
        val voters = mutableListOf<Voter>()
        val wards = sortedSetOf<Int>()

        for (rowIndex in startIndex until rows.size) {
            val row = rows[rowIndex]
            if (row.all { it.isBlank() }) continue
            // Ignore repeated header rows found in files assembled from multiple ward sheets.
            if (row.any { headerKind(it) == "name" } &&
                row.any { headerKind(it) == "serial" || headerKind(it) == "ward" }) continue

            val serialRaw = row.getOrNull(columns.serial).orEmpty().trim()
            val nameRaw = row.getOrNull(columns.name).orEmpty().trim()
            val relativeRaw = row.getOrNull(columns.relative).orEmpty().trim()
            val houseRaw = if (columns.house >= 0) row.getOrNull(columns.house).orEmpty().trim() else ""
            val epicRaw = row.getOrNull(columns.epic).orEmpty().trim()
            val wardRaw = row.getOrNull(columns.ward).orEmpty().trim()

            // A row must contain a voter name. Do not manufacture fake "Voter 123" records.
            if (nameRaw.isBlank()) continue
            val serial = cleanNumberString(serialRaw.ifBlank { (voters.size + 1).toString() })
            val ward = extractWardNumber(wardRaw)
            if (ward > 0) wards.add(ward)

            voters.add(
                Voter(
                    serialNumber = serial,
                    name = nameRaw,
                    relativeName = relativeRaw,
                    houseNumber = houseRaw,
                    epicNumber = epicRaw,
                    wardNumber = ward,
                    attendanceStatus = AttendanceStatus.NONE,
                    votedCandidateId = null
                )
            )
        }

        return ParsedVoterData(voters, wards.toList())
    }

    private fun extractWardNumber(raw: String): Int {
        val normalized = raw.trim()
            .replace('०', '0').replace('१', '1').replace('२', '2')
            .replace('३', '3').replace('४', '4').replace('५', '5')
            .replace('६', '6').replace('७', '7').replace('८', '8')
            .replace('९', '9')
        val digits = normalized.filter { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it > 0 } ?: 0
    }

    private fun cleanNumberString(raw: String): String {
        val value = raw.trim()
        val numeric = value.toDoubleOrNull()
        return if (numeric != null && numeric.isFinite() &&
            numeric == numeric.toLong().toDouble()) numeric.toLong().toString() else value
    }

    fun parseCsv(inputStream: InputStream): List<List<String>> {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val rows = mutableListOf<List<String>>()
        var line: String? = reader.readLine()
        while (line != null) {
            val cleanLine = line.removePrefix("\uFEFF")
            if (cleanLine.isNotBlank()) rows.add(parseCsvLine(cleanLine))
            line = reader.readLine()
        }
        return rows
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        val delimiter = when {
            line.contains('\t') -> '\t'
            line.contains(';') && !line.contains(',') -> ';'
            else -> ','
        }
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    value.append('"'); index++
                }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> {
                    result.add(value.toString().trim()); value.setLength(0)
                }
                else -> value.append(ch)
            }
            index++
        }
        result.add(value.toString().trim())
        return result
    }

    // XLSX is a ZIP of XML files. Preserve cell references so omitted/blank cells
    // do not shift later values into the wrong voter fields.
    private fun parseXlsx(inputStream: InputStream): List<List<String>> {
        var sharedStringsBytes: ByteArray? = null
        var sheetBytes: ByteArray? = null
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.lowercase()
                when {
                    entryName.endsWith("sharedstrings.xml") -> sharedStringsBytes = zip.readBytes()
                    entryName.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) && sheetBytes == null ->
                        sheetBytes = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val strings = sharedStringsBytes?.let { parseSharedStrings(ByteArrayInputStream(it)) }.orEmpty()
        val bytes = sheetBytes ?: return emptyList()
        return parseSheetXml(ByteArrayInputStream(bytes), strings)
    }

    private fun localName(name: String?): String =
        name.orEmpty().substringAfterLast(':')

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val values = mutableListOf<String>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")
        var current = StringBuilder()
        var inItem = false
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "si" -> { current = StringBuilder(); inItem = true }
                    "t" -> inText = true
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem && inText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "t" -> inText = false
                    "si" -> { values.add(current.toString()); inItem = false }
                }
            }
            event = parser.next()
        }
        return values
    }

    private fun columnIndex(cellReference: String?): Int {
        val letters = cellReference.orEmpty().takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return -1
        var result = 0
        for (ch in letters) result = result * 26 + (ch - 'A' + 1)
        return result - 1
    }

    private fun parseSheetXml(stream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")
        var rowCells: MutableMap<Int, String>? = null
        var nextColumn = 0
        var cellColumn = -1
        var cellType = ""
        var inValue = false
        var inInlineText = false
        var cellValue = StringBuilder()
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "row" -> { rowCells = mutableMapOf(); nextColumn = 0 }
                    "c" -> {
                        cellColumn = columnIndex(parser.getAttributeValue(null, "r")).let {
                            if (it >= 0) it else nextColumn
                        }
                        nextColumn = cellColumn + 1
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellValue = StringBuilder()
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr") inInlineText = true
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inValue || inInlineText) cellValue.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val raw = cellValue.toString()
                        val finalValue = when (cellType) {
                            "s" -> raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                            "inlineStr" -> raw
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        if (cellColumn >= 0) rowCells?.set(cellColumn, finalValue)
                    }
                    "row" -> {
                        val cells = rowCells
                        if (cells != null && cells.isNotEmpty()) {
                            val maxColumn = cells.keys.maxOrNull() ?: -1
                            rows.add((0..maxColumn).map { cells[it].orEmpty() })
                        }
                        rowCells = null
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

// --- SAMPLE VOTER DATA GENERATOR ---
    fun getSampleVoters(): List<Voter> {
        val list = mutableListOf<Voter>()

        val ward1Voters = listOf(
            Triple("1", "रामेश्वर शर्मा (Rameshwar Sharma)", "स्व. दीनानाथ शर्मा"),
            Triple("2", "सुनीता शर्मा (Sunita Sharma)", "रामेश्वर शर्मा"),
            Triple("3", "विकास कुमार (Vikas Kumar)", "रामेश्वर शर्मा"),
            Triple("4", "अमित वर्मा (Amit Verma)", "किशन वर्मा"),
            Triple("5", "पूजा वर्मा (Pooja Verma)", "अमित वर्मा"),
            Triple("6", "सुरेश यादव (Suresh Yadav)", "हरिराम यादव"),
            Triple("7", "कमला देवी (Kamla Devi)", "सुरेश यादव"),
            Triple("8", "दीपक गुप्ता (Deepak Gupta)", "राधेश्याम गुप्ता"),
            Triple("9", "मनीषा गुप्ता (Manisha Gupta)", "दीपक गुप्ता"),
            Triple("10", "अनिल सिंह (Anil Singh)", "राजेंद्र सिंह"),
            Triple("11", "सविता सिंह (Savita Singh)", "अनिल सिंह"),
            Triple("12", "राहुल मीणा (Rahul Meena)", "कैलाश मीणा"),
            Triple("13", "आरती मीणा (Aarti Meena)", "राहुल मीणा"),
            Triple("14", "संजय जांगिड़ (Sanjay Jangid)", "मदनलाल जांगिड़"),
            Triple("15", "रेखा जांगिड़ (Rekha Jangid)", "संजय जांगिड़")
        )

        val ward2Voters = listOf(
            Triple("1", "मनोज चौधरी (Manoj Choudhary)", "रामपाल चौधरी"),
            Triple("2", "सरोज चौधरी (Saroj Choudhary)", "मनोज चौधरी"),
            Triple("3", "मुकेश सैनी (Mukesh Saini)", "भोलाराम सैनी"),
            Triple("4", "सुमित्रा सैनी (Sumitra Saini)", "मुकेश सैनी"),
            Triple("5", "विजय पांडे (Vijay Pandey)", "उमाकांत पांडे"),
            Triple("6", "सीमा पांडे (Seema Pandey)", "विजय पांडे"),
            Triple("7", "गोपाल कुमावत (Gopal Kumawat)", "नारायण कुमावत"),
            Triple("8", "शांति देवी (Shanti Devi)", "गोपाल कुमावत"),
            Triple("9", "अशोक जैन (Ashok Jain)", "नेमीचंद जैन"),
            Triple("10", "मंजू जैन (Manju Jain)", "अशोक जैन"),
            Triple("11", "रोहित सोनी (Rohit Soni)", "मुरलीधर सोनी"),
            Triple("12", "प्रिया सोनी (Priya Soni)", "रोहित सोनी"),
            Triple("13", "दिनेश बैरवा (Dinesh Bairwa)", "रामस्वरूप बैरवा"),
            Triple("14", "ममता बैरवा (Mamta Bairwa)", "दिनेश बैरवा"),
            Triple("15", "राजेश गुर्जर (Rajesh Gurjar)", "हरिसिंह गुर्जर")
        )

        val ward3Voters = listOf(
            Triple("1", "प्रदीप तिवारी (Pradeep Tiwari)", "गौरीशंकर तिवारी"),
            Triple("2", "अंजू तिवारी (Anju Tiwari)", "प्रदीप तिवारी"),
            Triple("3", "सोनू अग्रवाल (Sonu Agarwal)", "महेंद्र अग्रवाल"),
            Triple("4", "नीतू अग्रवाल (Neetu Agarwal)", "सोनू अग्रवाल"),
            Triple("5", "विनोद पारीक (Vinod Pareek)", "लक्ष्मीनारायण पारीक"),
            Triple("6", "शारदा पारीक (Sharda Pareek)", "विनोद पारीक"),
            Triple("7", "जितेंद्र राठौड़ (Jitendra Rathore)", "भंवर सिंह राठौड़"),
            Triple("8", "किरण कंवर (Kiran Kanwar)", "जितेंद्र राठौड़"),
            Triple("9", "सुनील बंसल (Sunil Bansal)", "मुरारीलाल बंसल"),
            Triple("10", "रीता बंसal (Reeta Bansal)", "सुनील बंसल"),
            Triple("11", "अजय धाकड़ (Ajay Dhakad)", "रामकरण धाकड़"),
            Triple("12", "कविता धाकड़ (Kavita Dhakad)", "अजय धाकड़"),
            Triple("13", "अशरफ खान (Ashraf Khan)", "अय्यूब खान"),
            Triple("14", "रशीदा बानो (Rashida Bano)", "अशरफ खान"),
            Triple("15", "सलीम मोहम्मद (Saleem Mohammed)", "अब्दुल रशीद")
        )

        var epicCounter = 1001
        fun addWardList(wardNum: Int, voters: List<Triple<String, String, String>>) {
            voters.forEach { (sr, name, rel) ->
                // Every 4th voter has blank EPIC as per requirement (some voters might have empty EPIC)
                val epic = if (epicCounter % 4 == 0) "" else "RJX${epicCounter++}89"
                list.add(
                    Voter(
                        serialNumber = sr,
                        name = name,
                        relativeName = rel,
                        epicNumber = epic,
                        wardNumber = wardNum,
                        attendanceStatus = AttendanceStatus.NONE,
                        votedCandidateId = null
                    )
                )
            }
        }

        addWardList(1, ward1Voters)
        addWardList(2, ward2Voters)
        addWardList(3, ward3Voters)

        return list
    }

    // --- EXPORT CSV TEMPLATE ---
    fun exportTemplateCsv(context: Context): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val file = File(reportsDir, "Voter_List_Template.csv")

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write("\uFEFF") // UTF-8 BOM
                writer.write("\"Serial Number\",\"Voter Name\",\"Father / Mother / Husband Name\",\"EPIC Number\",\"Ward Number\"\n")
                writer.write("\"1\",\"रामेश्वर शर्मा\",\"दीनानाथ शर्मा\",\"RJX100189\",\"1\"\n")
                writer.write("\"2\",\"सुनीता शर्मा\",\"रामेश्वर शर्मा\",\"RJX100289\",\"1\"\n")
                writer.write("\"3\",\"विकास कुमार\",\"रामेश्वर शर्मा\",\"\",\"1\"\n")
                writer.write("\"1\",\"मनोज चौधरी\",\"रामपाल चौधरी\",\"RJX100489\",\"2\"\n")
                writer.write("\"2\",\"सरोज चौधरी\",\"मनोज चौधरी\",\"RJX100589\",\"2\"\n")
            }
        }
        return file
    }
}
