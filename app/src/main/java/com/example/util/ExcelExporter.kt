package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.entity.AttendanceStatus
import com.example.data.entity.Candidate
import com.example.data.entity.Voter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object ExcelExporter {

    /**
     * Dedicated Check Voters Excel / CSV Report
     * Contains only voter attendance stats, ward-wise breakdown, and voter records.
     */
    fun generateCheckVotersReport(
        context: Context,
        voters: List<Voter>,
        wards: List<Int>
    ): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val file = File(reportsDir, "Check_Voters_Report.csv")

        val sortedWards = wards.sorted()
        val totalVotersCount = voters.size

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // Write UTF-8 BOM so Microsoft Excel renders Hindi and English without distortion
                writer.write("\uFEFF")

                writer.write("\"=== मतदाता चेकिंग सारांश रिपोर्ट (CHECK VOTERS SUMMARY REPORT) ===\"\n\n")

                // Header for Check Voters Summary
                val checkHeaders = mutableListOf("\"स्थिति (Status)\"")
                sortedWards.forEach { w ->
                    checkHeaders.add("\"Ward $w\"")
                }
                checkHeaders.add("\"कुल (Total ALL)\"")
                checkHeaders.add("\"प्रतिशत (%)\"")
                writer.write(checkHeaders.joinToString(",") + "\n")

                // Green Row
                val greenRow = mutableListOf("\"Green Tick (उपस्थित / वोट डाला)\"")
                var greenTotal = 0
                sortedWards.forEach { w ->
                    val c = voters.count { it.wardNumber == w && it.attendanceStatus == AttendanceStatus.GREEN }
                    greenTotal += c
                    greenRow.add("$c")
                }
                greenRow.add("$greenTotal")
                greenRow.add("\"${if (totalVotersCount > 0) String.format("%.1f", greenTotal.toFloat() / totalVotersCount * 100) else "0"}%\"")
                writer.write(greenRow.joinToString(",") + "\n")

                // Yellow Row
                val yellowRow = mutableListOf("\"Yellow Mark (अनुपस्थित / संशय)\"")
                var yellowTotal = 0
                sortedWards.forEach { w ->
                    val c = voters.count { it.wardNumber == w && it.attendanceStatus == AttendanceStatus.YELLOW }
                    yellowTotal += c
                    yellowRow.add("$c")
                }
                yellowRow.add("$yellowTotal")
                yellowRow.add("\"${if (totalVotersCount > 0) String.format("%.1f", yellowTotal.toFloat() / totalVotersCount * 100) else "0"}%\"")
                writer.write(yellowRow.joinToString(",") + "\n")

                // Red Row
                val redRow = mutableListOf("\"Red Cross (मृत / बोगस / विलोपित)\"")
                var redTotal = 0
                sortedWards.forEach { w ->
                    val c = voters.count { it.wardNumber == w && it.attendanceStatus == AttendanceStatus.RED }
                    redTotal += c
                    redRow.add("$c")
                }
                redRow.add("$redTotal")
                redRow.add("\"${if (totalVotersCount > 0) String.format("%.1f", redTotal.toFloat() / totalVotersCount * 100) else "0"}%\"")
                writer.write(redRow.joinToString(",") + "\n")

                // Not Checked Row
                val noneRow = mutableListOf("\"जाँच शेष (Pending / Not Checked)\"")
                var noneTotal = 0
                sortedWards.forEach { w ->
                    val c = voters.count { it.wardNumber == w && it.attendanceStatus == AttendanceStatus.NONE }
                    noneTotal += c
                    noneRow.add("$c")
                }
                noneRow.add("$noneTotal")
                noneRow.add("\"${if (totalVotersCount > 0) String.format("%.1f", noneTotal.toFloat() / totalVotersCount * 100) else "0"}%\"")
                writer.write(noneRow.joinToString(",") + "\n")

                // Total Voters Row
                val allVotersRow = mutableListOf("\"कुल मतदाता (Total Voters)\"")
                sortedWards.forEach { w ->
                    val c = voters.count { it.wardNumber == w }
                    allVotersRow.add("$c")
                }
                allVotersRow.add("$totalVotersCount")
                allVotersRow.add("\"100.0%\"")
                writer.write(allVotersRow.joinToString(",") + "\n\n")

                // Detailed Voter List Section
                writer.write("\"=== विस्तृत मतदाता चेकिंग सूची (DETAILED VOTER LIST) ===\"\n")
                writer.write("\"वार्ड (Ward)\",\"क्रमांक (Sr. No.)\",\"मतदाता का नाम (Name)\",\"पिता/पति का नाम (Relative Name)\",\"EPIC No.\",\"स्थिति (Status)\"\n")

                voters.sortedWith(compareBy({ it.wardNumber }, { it.serialNumber })).forEach { v ->
                    val statusText = when (v.attendanceStatus) {
                        AttendanceStatus.GREEN -> "Green Tick (उपस्थित)"
                        AttendanceStatus.YELLOW -> "Yellow Mark (अनुपस्थित/संशय)"
                        AttendanceStatus.RED -> "Red Cross (मृत/बोगस)"
                        AttendanceStatus.NONE -> "जाँच शेष"
                    }
                    val line = listOf(
                        "${v.wardNumber}",
                        "${v.serialNumber}",
                        "\"${escapeCsv(v.name)}\"",
                        "\"${escapeCsv(v.relativeName)}\"",
                        "\"${escapeCsv(v.epicNumber)}\"",
                        "\"$statusText\""
                    ).joinToString(",")
                    writer.write(line + "\n")
                }
            }
        }
        return file
    }

    /**
     * Dedicated Vote Counting Excel / CSV Report
     * Contains only candidate votes, NOTA, ward-wise breakdown, and winner summary.
     */
    fun generateVoteCountingReport(
        context: Context,
        voters: List<Voter>,
        candidates: List<Candidate>,
        wards: List<Int>
    ): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val file = File(reportsDir, "Vote_Counting_Report.csv")

        val sortedWards = wards.sorted()
        val totalPolled = voters.count { it.votedCandidateId != null }

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // Write UTF-8 BOM so Microsoft Excel renders Hindi and English without distortion
                writer.write("\uFEFF")

                writer.write("\"=== मतगणना परिणाम रिपोर्ट (VOTE COUNTING REPORT) ===\"\n\n")

                // Header Row
                val voteHeaders = mutableListOf("\"क्रमांक (Sr. No.)\"", "\"उम्मीदवार का नाम (Candidate)\"")
                sortedWards.forEach { w ->
                    voteHeaders.add("\"Ward $w\"")
                }
                voteHeaders.add("\"कुल वोट (Total)\"")
                voteHeaders.add("\"प्रतिशत (%)\"")
                writer.write(voteHeaders.joinToString(",") + "\n")

                // Regular Candidates Rows
                val regularCandidates = candidates.filter { !it.isNota }
                regularCandidates.forEachIndexed { index, candidate ->
                    val row = mutableListOf<String>()
                    row.add("${index + 1}")
                    row.add("\"${escapeCsv(candidate.name)}\"")

                    var candTotal = 0
                    sortedWards.forEach { w ->
                        val count = voters.count { it.wardNumber == w && it.votedCandidateId == candidate.id }
                        candTotal += count
                        row.add("$count")
                    }
                    val pct = if (totalPolled > 0) (candTotal.toFloat() / totalPolled) * 100f else 0f
                    row.add("$candTotal")
                    row.add("\"${String.format("%.1f", pct)}%\"")
                    writer.write(row.joinToString(",") + "\n")
                }

                // NOTA Row
                val notaCandidate = candidates.find { it.isNota }
                if (notaCandidate != null) {
                    val row = mutableListOf<String>()
                    row.add("\"-\"")
                    row.add("\"${escapeCsv(notaCandidate.name)}\"")

                    var notaTotal = 0
                    sortedWards.forEach { w ->
                        val count = voters.count { it.wardNumber == w && it.votedCandidateId == notaCandidate.id }
                        notaTotal += count
                        row.add("$count")
                    }
                    val pct = if (totalPolled > 0) (notaTotal.toFloat() / totalPolled) * 100f else 0f
                    row.add("$notaTotal")
                    row.add("\"${String.format("%.1f", pct)}%\"")
                    writer.write(row.joinToString(",") + "\n")
                }

                // Total Polled Row
                val totalRow = mutableListOf("\"-\"", "\"कुल दर्ज वोट (Total Polled)\"")
                sortedWards.forEach { w ->
                    val wardPolled = voters.count { it.wardNumber == w && it.votedCandidateId != null }
                    totalRow.add("$wardPolled")
                }
                totalRow.add("$totalPolled")
                totalRow.add("\"100.0%\"")
                writer.write(totalRow.joinToString(",") + "\n")
            }
        }
        return file
    }

    fun generateCompleteElectionReport(
        context: Context,
        voters: List<Voter>,
        candidates: List<Candidate>,
        wards: List<Int>
    ): File {
        return generateVoteCountingReport(context, voters, candidates, wards)
    }

    fun shareExcelFile(context: Context, file: File, subject: String = "Election Report") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "एक्सेल रिपोर्ट")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "एक्सेल रिपोर्ट शेयर या ओपन करें")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }
}
