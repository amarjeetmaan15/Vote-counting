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
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        val fileName = uri.lastPathSegment?.lowercase() ?: ""

        // Check if file is XLSX (ZIP archive)
        val isXlsx = fileName.endsWith(".xlsx") ||
                mimeType.contains("spreadsheetml", ignoreCase = true) ||
                mimeType.contains("excel", ignoreCase = true)

        val rawRows: List<List<String>> = try {
            if (isXlsx) {
                contentResolver.openInputStream(uri)?.use { parseXlsx(it) } ?: emptyList()
            } else {
                contentResolver.openInputStream(uri)?.use { parseCsv(it) } ?: emptyList()
            }
        } catch (e: Exception) {
            // Fallback: try parsing as CSV in case it was a renamed text file
            try {
                contentResolver.openInputStream(uri)?.use { parseCsv(it) } ?: emptyList()
            } catch (fallbackEx: Exception) {
                emptyList()
            }
        }

        return processRowsToVoters(rawRows)
    }

    private fun processRowsToVoters(rows: List<List<String>>): ParsedVoterData {
        if (rows.isEmpty()) return ParsedVoterData(emptyList(), emptyList())

        // Detect header row if present
        var startIndex = 0
        var serialCol = 0
        var nameCol = 1
        var relativeCol = 2
        var epicCol = 3
        var wardCol = 4

        val firstRow = rows.first()
        val hasHeader = firstRow.any { cell ->
            val lower = cell.lowercase()
            lower.contains("serial") || lower.contains("sr") || lower.contains("क्रम") ||
                    lower.contains("voter") || lower.contains("name") || lower.contains("नाम") ||
                    lower.contains("father") || lower.contains("husband") || lower.contains("पिता") ||
                    lower.contains("epic") || lower.contains("ward") || lower.contains("वार्ड")
        }

        if (hasHeader) {
            startIndex = 1
            firstRow.forEachIndexed { index, cell ->
                val lower = cell.lowercase().trim()
                when {
                    lower.contains("serial") || lower.contains("sr") || lower.contains("क्रम") -> serialCol = index
                    lower.contains("epic") || lower.contains("voter id") || lower.contains("पहचान") -> epicCol = index
                    lower.contains("ward") || lower.contains("वार्ड") -> wardCol = index
                    lower.contains("father") || lower.contains("mother") || lower.contains("husband") || lower.contains("relative") || lower.contains("पिता") || lower.contains("पति") -> relativeCol = index
                    lower.contains("name") || lower.contains("voter") || lower.contains("नाम") -> nameCol = index
                }
            }
        }

        val votersList = mutableListOf<Voter>()
        val wardsSet = sortedSetOf<Int>()

        for (i in startIndex until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue

            val serialVal = row.getOrNull(serialCol)?.trim().takeIf { !it.isNullOrBlank() } ?: "${votersList.size + 1}"
            val nameVal = row.getOrNull(nameCol)?.trim().takeIf { !it.isNullOrBlank() } ?: "Voter $serialVal"
            val relativeVal = row.getOrNull(relativeCol)?.trim() ?: ""
            val epicVal = row.getOrNull(epicCol)?.trim() ?: ""
            val wardRaw = row.getOrNull(wardCol)?.trim() ?: "1"

            val wardNum = extractWardNumber(wardRaw)
            wardsSet.add(wardNum)

            votersList.add(
                Voter(
                    serialNumber = cleanNumberString(serialVal),
                    name = nameVal,
                    relativeName = relativeVal,
                    epicNumber = epicVal,
                    wardNumber = wardNum,
                    attendanceStatus = AttendanceStatus.NONE,
                    votedCandidateId = null
                )
            )
        }

        return ParsedVoterData(votersList, wardsSet.toList())
    }

    private fun extractWardNumber(wardStr: String): Int {
        val digits = wardStr.filter { it.isDigit() }
        val parsed = digits.toIntOrNull()
        return if (parsed != null && parsed > 0) parsed else 1
    }

    private fun cleanNumberString(raw: String): String {
        val floatVal = raw.toDoubleOrNull()
        return if (floatVal != null && floatVal == floatVal.toLong().toDouble()) {
            floatVal.toLong().toString()
        } else {
            raw
        }
    }

    // --- CSV PARSER ---
    fun parseCsv(inputStream: InputStream): List<List<String>> {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val rows = mutableListOf<List<String>>()

        var line: String? = reader.readLine()
        // Strip BOM if present
        if (line != null && line.startsWith("\uFEFF")) {
            line = line.substring(1)
        }

        while (line != null) {
            if (line.isNotBlank()) {
                val cells = parseCsvLine(line)
                rows.add(cells)
            }
            line = reader.readLine()
        }
        return rows
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val curVal = StringBuilder()
        var inQuotes = false

        val delimiter = if (line.contains("\t")) '\t' else if (line.contains(";") && !line.contains(",")) ';' else ','

        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == delimiter && !inQuotes) {
                result.add(curVal.toString().trim())
                curVal.setLength(0)
            } else {
                curVal.append(ch)
            }
        }
        result.add(curVal.toString().trim())
        return result
    }

    // --- PURE JAVA XLSX PARSER ---
    private fun parseXlsx(inputStream: InputStream): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        var sheetXmlBytes: ByteArray? = null

        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name.lowercase()
            if (name.endsWith("sharedstrings.xml")) {
                sharedStrings.addAll(parseSharedStrings(zip))
            } else if (name.endsWith("sheet1.xml") || (name.contains("sheet") && name.endsWith(".xml") && sheetXmlBytes == null)) {
                sheetXmlBytes = zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        if (sheetXmlBytes == null) return emptyList()

        return parseSheetXml(ByteArrayInputStream(sheetXmlBytes), sharedStrings)
    }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var inText = false
        val currentText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("t", ignoreCase = true)) {
                        inText = true
                        currentText.setLength(0)
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("t", ignoreCase = true)) {
                        inText = false
                        strings.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseSheetXml(stream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var currentRow = mutableListOf<String>()
        var currentCellVal = StringBuilder()
        var isSharedString = false
        var insideValue = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "row" -> {
                            currentRow = mutableListOf()
                        }
                        "c" -> {
                            val type = parser.getAttributeValue(null, "t")
                            isSharedString = type == "s"
                            currentCellVal.setLength(0)
                        }
                        "v" -> {
                            insideValue = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideValue) {
                        currentCellVal.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "v" -> {
                            insideValue = false
                        }
                        "c" -> {
                            val raw = currentCellVal.toString().trim()
                            val finalVal = if (isSharedString) {
                                val idx = raw.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                            } else {
                                raw
                            }
                            currentRow.add(finalVal)
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                rows.add(currentRow)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
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
