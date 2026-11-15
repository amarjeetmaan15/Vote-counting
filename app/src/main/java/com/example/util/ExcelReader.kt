package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
        val wardsFound: List<Int>,
        val sourceRows: Int = voters.size,
        val skippedRows: Int = 0,
        val votersWithoutWard: Int = 0,
        val duplicateEpicRows: Int = 0
    )

    private enum class InputFormat { XLSX, CSV }

    fun parseExcelOrCsv(context: Context, uri: Uri): ParsedVoterData {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri).orEmpty().lowercase()
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }
            .orEmpty()
            .ifBlank { uri.lastPathSegment.orEmpty() }
        val format = detectInputFormat(displayName, mimeType)
        val rows = resolver.openInputStream(uri)?.use { input ->
            when (format) {
                InputFormat.XLSX -> parseXlsx(input)
                InputFormat.CSV -> parseCsv(input)
            }
        } ?: throw IllegalArgumentException("चुनी गई फाइल पढ़ी नहीं जा सकी")
        return processRowsToVoters(rows)
    }

    internal fun detectInputFormat(fileName: String, mimeType: String): InputFormat {
        val name = fileName.substringAfterLast('/').lowercase()
        val mime = mimeType.lowercase()
        return when {
            name.endsWith(".xls") ->
                throw IllegalArgumentException("पुरानी .xls फाइल सपोर्ट नहीं है। Excel में इसे .xlsx के रूप में Save As करके दोबारा चुनें।")
            name.endsWith(".xlsx") || mime.contains("spreadsheetml") -> InputFormat.XLSX
            name.endsWith(".csv") || mime.contains("csv") || mime == "text/plain" -> InputFormat.CSV
            mime == "application/vnd.ms-excel" ->
                throw IllegalArgumentException("यह .xls Excel फाइल लगती है। कृपया इसे .xlsx में Save As करें।")
            else -> throw IllegalArgumentException("सिर्फ .xlsx और .csv फाइलें सपोर्टेड हैं। चुनी गई फाइल का format पहचान नहीं आया।")
        }
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

    // Exact header matching only. A voter value such as "सतनाम सिंह" or
    // a house value such as "वार्ड नं 12" must never be classified as a header.
    private fun headerKind(value: String): String? = when (normalizeHeader(value)) {
        "epic", "epic no", "epic number", "epic नंबर", "voter id", "voter identity",
        "identity card", "मतदाता पहचान", "पहचान पत्र", "निर्वाचक फोटो पहचान", "मतदाता परिचय" -> "epic"

        "house", "house no", "house number", "घर नंबर", "घर संख्या",
        "मकान नंबर", "मकान संख्या" -> "house"

        "ward", "ward no", "ward number", "वार्ड", "वार्ड नं",
        "वार्ड नंबर", "वार्ड क्रमांक" -> "ward"

        "father", "father name", "mother", "mother name", "husband",
        "husband name", "relative", "relation", "guardian",
        "पिता", "पिताजी", "माता", "माताजी", "पति", "अभिभावक",
        "रिश्तेदार", "पिता माता पति का नाम" -> "relative"

        "serial", "serial no", "serial number", "sr no", "sr number", "s no",
        "क्रमांक", "क्रम संख्या", "क्र सं", "अनुक्रमांक", "मतदाता क्रम",
        "सीरियल नंबर", "सीरियल क्रमांक" -> "serial"

        "voter name", "name of voter", "elector name",
        "मतदाता का नाम", "मतदाता नाम", "निर्वाचक का नाम", "नाम" -> "name"

        else -> null
    }

    internal fun processRowsToVoters(rows: List<List<String>>): ParsedVoterData {
        if (rows.isEmpty()) return ParsedVoterData(emptyList(), emptyList(), 0)

        // Find a genuine header row near the top. Require both the voter-name and
        // ward headings plus at least one other recognized column.
        var headerIndex = -1
        var bestHeaderScore = 0
        for (i in 0 until minOf(rows.size, 20)) {
            val kinds = rows[i].mapNotNull(::headerKind).toSet()
            val score = kinds.size
            if ("name" in kinds && "ward" in kinds && score >= 3 && score > bestHeaderScore) {
                bestHeaderScore = score
                headerIndex = i
            }
        }

        val columns = if (headerIndex >= 0) {
            val mapped = mutableMapOf<String, Int>()
            rows[headerIndex].forEachIndexed { index, cell ->
                val kind = headerKind(cell)
                if (kind != null && kind !in mapped) mapped[kind] = index
            }
            val hasHouse = mapped.containsKey("house")
            ColumnMap(
                serial = mapped["serial"] ?: 0,
                name = mapped["name"] ?: 1,
                relative = mapped["relative"] ?: 2,
                house = mapped["house"] ?: -1,
                epic = mapped["epic"] ?: if (hasHouse) 4 else 3,
                ward = mapped["ward"] ?: if (hasHouse) 5 else 4
            )
        } else {
            // Headerless files are classified by the actual column contents,
            // not by a single unusually wide or formatted row.
            val dataRows = rows.filter { it.any(String::isNotBlank) && it.getOrNull(1).orEmpty().isNotBlank() }
            val sixColumnVotes = dataRows.count { row ->
                row.size >= 6 && extractWardNumber(row.getOrNull(5).orEmpty()) > 0 &&
                    looksLikeEpicOrBlank(row.getOrNull(4).orEmpty())
            }
            val fiveColumnVotes = dataRows.count { row ->
                row.size >= 5 && extractWardNumber(row.getOrNull(4).orEmpty()) > 0 &&
                    looksLikeEpicOrBlank(row.getOrNull(3).orEmpty())
            }
            if (sixColumnVotes > fiveColumnVotes) ColumnMap(house = 3, epic = 4, ward = 5) else ColumnMap()
        }

        val startIndex = if (headerIndex >= 0) headerIndex + 1 else 0
        val voters = mutableListOf<Voter>()
        val wards = sortedSetOf<Int>()
        var sourceRows = 0
        var skippedRows = 0
        var votersWithoutWard = 0

        for (rowIndex in startIndex until rows.size) {
            val row = rows[rowIndex]
            if (row.all { it.isBlank() }) continue
            if (isRepeatedHeaderRow(row, columns)) continue
            sourceRows++

            val serialRaw = row.getOrNull(columns.serial).orEmpty().trim()
            val nameRaw = row.getOrNull(columns.name).orEmpty().trim()
            val relativeRaw = row.getOrNull(columns.relative).orEmpty().trim()
            val houseRaw = if (columns.house >= 0) row.getOrNull(columns.house).orEmpty().trim() else ""
            val epicRaw = row.getOrNull(columns.epic).orEmpty().trim()
            val wardRaw = row.getOrNull(columns.ward).orEmpty().trim()

            if (nameRaw.isBlank()) {
                skippedRows++
                continue
            }

            val serial = cleanNumberString(serialRaw.ifBlank { (voters.size + 1).toString() })
            val ward = extractWardNumber(wardRaw)
            if (ward > 0) wards.add(ward) else votersWithoutWard++

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

        val duplicateEpicRows = voters
            .map { it.epicNumber.trim().uppercase() }
            .filter { it.isNotBlank() }
            .let { values -> values.size - values.toSet().size }

        return ParsedVoterData(
            voters = voters,
            wardsFound = wards.toList(),
            sourceRows = sourceRows,
            skippedRows = skippedRows,
            votersWithoutWard = votersWithoutWard,
            duplicateEpicRows = duplicateEpicRows
        )
    }

    private fun isRepeatedHeaderRow(row: List<String>, columns: ColumnMap): Boolean =
        headerKind(row.getOrNull(columns.name).orEmpty()) == "name" &&
            headerKind(row.getOrNull(columns.ward).orEmpty()) == "ward" &&
            headerKind(row.getOrNull(columns.serial).orEmpty()) == "serial"

    private fun looksLikeEpicOrBlank(value: String): Boolean {
        val text = value.trim()
        return text.isEmpty() || text.matches(Regex("(?i)[a-z0-9/ -]{4,}"))
    }

    internal fun extractWardNumber(raw: String): Int {
        val normalized = raw.trim()
            .replace('०', '0').replace('१', '1').replace('२', '2')
            .replace('३', '3').replace('४', '4').replace('५', '5')
            .replace('६', '6').replace('७', '7').replace('८', '8')
            .replace('९', '9')
        val wardLabelNumber = Regex("(?:ward|वार्ड)\\s*(?:no|number|नं|नंबर|क्रमांक)?\\s*[:#.-]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(normalized)?.groupValues?.getOrNull(1)
        val firstNumber = wardLabelNumber ?: Regex("\\d+").find(normalized)?.value
        return firstNumber?.toIntOrNull()?.takeIf { it > 0 } ?: 0
    }

    private fun cleanNumberString(raw: String): String {
        val value = raw.trim()
        val numeric = value.toDoubleOrNull()
        return if (numeric != null && numeric.isFinite() &&
            numeric == numeric.toLong().toDouble()) numeric.toLong().toString() else value
    }

    fun parseCsv(inputStream: InputStream): List<List<String>> {
        val content = InputStreamReader(inputStream, StandardCharsets.UTF_8)
            .use { it.readText() }
            .removePrefix("\uFEFF")
        if (content.isBlank()) return emptyList()

        val delimiter = detectCsvDelimiter(content)
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0

        fun finishCell() {
            row.add(cell.toString().trim())
            cell.setLength(0)
        }
        fun finishRow() {
            finishCell()
            if (row.any { it.isNotBlank() }) rows.add(row.toList())
            row.clear()
        }

        while (index < content.length) {
            val ch = content[index]
            when {
                ch == '"' && quoted && index + 1 < content.length && content[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> finishCell()
                (ch == '\n' || ch == '\r') && !quoted -> {
                    finishRow()
                    if (ch == '\r' && index + 1 < content.length && content[index + 1] == '\n') index++
                }
                else -> cell.append(ch)
            }
            index++
        }
        if (quoted) throw IllegalArgumentException("CSV फाइल में quotes का जोड़ा अधूरा है।")
        if (cell.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }

    private fun detectCsvDelimiter(content: String): Char {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        var quoted = false
        var commas = 0
        var semicolons = 0
        var tabs = 0
        var i = 0
        while (i < firstLine.length) {
            val ch = firstLine[i]
            if (ch == '"' && quoted && i + 1 < firstLine.length && firstLine[i + 1] == '"') {
                i++
            } else if (ch == '"') {
                quoted = !quoted
            } else if (!quoted) {
                when (ch) {
                    ',' -> commas++
                    ';' -> semicolons++
                    '\t' -> tabs++
                }
            }
            i++
        }
        return when {
            tabs > commas && tabs >= semicolons -> '\t'
            semicolons > commas && semicolons > tabs -> ';'
            else -> ','
        }
    }

    // Resolve the workbook's first visible worksheet through workbook.xml and
    // workbook.xml.rels instead of assuming sheet1.xml is the voter list.
    internal fun parseXlsx(inputStream: InputStream): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val workbookBytes = entries["xl/workbook.xml"]
            ?: throw IllegalArgumentException("यह मान्य XLSX workbook नहीं है।")
        val relsBytes = entries["xl/_rels/workbook.xml.rels"]
            ?: throw IllegalArgumentException("XLSX workbook की worksheet जानकारी नहीं मिली।")
        val relationshipId = firstVisibleSheetRelationshipId(ByteArrayInputStream(workbookBytes))
            ?: throw IllegalArgumentException("Excel में कोई visible worksheet नहीं मिली।")
        val target = worksheetTarget(ByteArrayInputStream(relsBytes), relationshipId)
            ?: throw IllegalArgumentException("Excel की active worksheet का path नहीं मिला।")
        val sheetPath = normalizeZipPath(if (target.startsWith("/")) target.removePrefix("/") else "xl/$target")
        val sheetBytes = entries[sheetPath]
            ?: throw IllegalArgumentException("Excel worksheet पढ़ी नहीं जा सकी: " + sheetPath)

        val strings = entries["xl/sharedStrings.xml"]
            ?.let { parseSharedStrings(ByteArrayInputStream(it)) }
            .orEmpty()
        return parseSheetXml(ByteArrayInputStream(sheetBytes), strings)
    }

    private fun firstVisibleSheetRelationshipId(stream: InputStream): String? {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")
        var firstId: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && localName(parser.name) == "sheet") {
                val id = attributeByLocalName(parser, "id")
                if (firstId == null) firstId = id
                val state = attributeByLocalName(parser, "state").orEmpty()
                if (state != "hidden" && state != "veryHidden") return id
            }
            event = parser.next()
        }
        return firstId
    }

    private fun worksheetTarget(stream: InputStream, relationshipId: String): String? {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && localName(parser.name) == "Relationship" &&
                attributeByLocalName(parser, "Id") == relationshipId) {
                return attributeByLocalName(parser, "Target")
            }
            event = parser.next()
        }
        return null
    }

    private fun attributeByLocalName(parser: XmlPullParser, expected: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (localName(parser.getAttributeName(i)) == expected) return parser.getAttributeValue(i)
        }
        return null
    }

    private fun normalizeZipPath(path: String): String {
        val parts = mutableListOf<String>()
        path.replace('\\', '/').split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
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
    // New six-column template matches the current House Number Excel format.
    fun exportTemplateCsv(context: Context): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val file = File(reportsDir, "Voter_List_Template.csv")

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write("\uFEFF")
                writer.write("\"सीरियल नंबर\",\"नाम\",\"पिता / माता / पति का नाम\",\"घर नंबर\",\"EPIC नंबर\",\"वार्ड नंबर\"\n")
                writer.write("\"1\",\"रामेश्वर शर्मा\",\"दीनानाथ शर्मा\",\"101\",\"RJX100189\",\"1\"\n")
                writer.write("\"2\",\"सुनीता शर्मा\",\"रामेश्वर शर्मा\",\"102\",\"RJX100289\",\"1\"\n")
                writer.write("\"3\",\"विकास कुमार\",\"रामेश्वर शर्मा\",\"103\",\"\",\"1\"\n")
                writer.write("\"1\",\"मनोज चौधरी\",\"रामपाल चौधरी\",\"201\",\"RJX100489\",\"2\"\n")
                writer.write("\"2\",\"सरोज चौधरी\",\"मनोज चौधरी\",\"202\",\"RJX100589\",\"2\"\n")
            }
        }
        return file
    }
}
