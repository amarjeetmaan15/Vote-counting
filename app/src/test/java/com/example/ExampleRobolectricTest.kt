package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.example.data.VoteRepository
import com.example.data.entity.AttendanceStatus
import com.example.data.entity.Voter
import com.example.util.ExcelExporter
import com.example.util.ExcelReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: VoteRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VoteRepository(db, db.voterDao(), db.candidateDao(), db.wardDao(), db.voteDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Vote Counting", appName)
    }

    @Test
    fun testExcelDataLoadingAndAutomaticWards() = runBlocking {
        // 1. Load sample voters (from Excel generator)
        repository.loadSampleVoters()

        val allVoters = repository.allVoters.first()
        assertTrue("Voters should be loaded", allVoters.isNotEmpty())
        assertEquals(45, allVoters.size)

        // Verify Automatic Ward creation from Excel
        val allWards = repository.allWards.first()
        assertEquals(3, allWards.size)
        assertEquals(1, allWards[0].wardNumber)
        assertEquals(2, allWards[1].wardNumber)
        assertEquals(3, allWards[2].wardNumber)

        // Verify some voters have blank EPIC numbers as per requirement
        val blankEpicVoters = allVoters.filter { it.epicNumber.isBlank() }
        assertTrue("Some voters should have blank EPIC as per requirement", blankEpicVoters.isNotEmpty())
    }

    @Test
    fun testCheckVotersAttendance() = runBlocking {
        repository.loadSampleVoters()
        val voters = repository.allVoters.first()
        val voter1 = voters[0]
        val voter2 = voters[1]
        val voter3 = voters[2]

        // Mark voter 1 as GREEN (Favorable)
        repository.updateVoterAttendance(voter1.id, AttendanceStatus.GREEN)
        // Mark voter 2 as YELLOW (Doubtful)
        repository.updateVoterAttendance(voter2.id, AttendanceStatus.YELLOW)
        // Mark voter 3 as RED (Against)
        repository.updateVoterAttendance(voter3.id, AttendanceStatus.RED)

        val updatedVoters = repository.allVoters.first()
        val updated1 = updatedVoters.find { it.id == voter1.id }
        val updated2 = updatedVoters.find { it.id == voter2.id }
        val updated3 = updatedVoters.find { it.id == voter3.id }

        assertEquals(AttendanceStatus.GREEN, updated1?.attendanceStatus)
        assertEquals(AttendanceStatus.YELLOW, updated2?.attendanceStatus)
        assertEquals(AttendanceStatus.RED, updated3?.attendanceStatus)

        val greenCount = updatedVoters.count { it.attendanceStatus == AttendanceStatus.GREEN }
        val yellowCount = updatedVoters.count { it.attendanceStatus == AttendanceStatus.YELLOW }
        val redCount = updatedVoters.count { it.attendanceStatus == AttendanceStatus.RED }
        assertEquals(1, greenCount)
        assertEquals(1, yellowCount)
        assertEquals(1, redCount)
    }

    @Test
    fun testVoteCountingCandidateSelectionAndNota() = runBlocking {
        repository.loadSampleVoters()
        repository.setCandidateList(listOf("Candidate A", "Candidate B"))

        val candidates = repository.allCandidates.first()
        assertEquals(3, candidates.size) // 2 candidates + NOTA
        val candA = candidates.find { it.name == "Candidate A" }!!
        val candB = candidates.find { it.name == "Candidate B" }!!
        val nota = candidates.find { it.isNota }!!

        val voters = repository.allVoters.first()
        val voter1 = voters[0]
        val voter2 = voters[1]

        // 1. Voter 1 votes for Candidate A
        repository.updateVoterVote(voter1.id, candA.id)
        var currentVoters = repository.allVoters.first()
        assertEquals(candA.id, currentVoters.find { it.id == voter1.id }?.votedCandidateId)

        // 2. Voter 1 changes mind and votes for Candidate B (automatic transfer)
        repository.updateVoterVote(voter1.id, candB.id)
        currentVoters = repository.allVoters.first()
        assertEquals(candB.id, currentVoters.find { it.id == voter1.id }?.votedCandidateId)

        // 3. Voter 2 votes for NOTA
        repository.updateVoterVote(voter2.id, nota.id)
        currentVoters = repository.allVoters.first()
        assertEquals(nota.id, currentVoters.find { it.id == voter2.id }?.votedCandidateId)

        val polled = currentVoters.count { it.votedCandidateId != null }
        val votesA = currentVoters.count { it.votedCandidateId == candA.id }
        val votesB = currentVoters.count { it.votedCandidateId == candB.id }
        val votesNota = currentVoters.count { it.votedCandidateId == nota.id }

        assertEquals(2, polled)
        assertEquals(0, votesA)
        assertEquals(1, votesB)
        assertEquals(1, votesNota)
    }

    @Test
    fun testSeparateExcelReportGenerations() = runBlocking {
        repository.loadSampleVoters()
        repository.setCandidateList(listOf("Candidate 1"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voters = repository.allVoters.first()
        val candidates = repository.allCandidates.first()
        val wards = listOf(1, 2, 3)

        // 1. Check Voters Report (Attendance only)
        val checkReport = ExcelExporter.generateCheckVotersReport(context, voters, wards)
        assertTrue(checkReport.exists())
        val checkText = checkReport.readText()
        assertTrue(checkText.contains("CHECK VOTERS SUMMARY REPORT"))
        assertTrue(checkText.contains("Green Tick"))
        assertTrue(checkText.contains("Yellow Mark"))
        assertTrue(checkText.contains("Red Cross"))
        assertTrue(checkText.contains("Ward 1"))

        // 2. Vote Counting Report (Candidates & NOTA only)
        val voteReport = ExcelExporter.generateVoteCountingReport(context, voters, candidates, wards)
        assertTrue(voteReport.exists())
        val voteText = voteReport.readText()
        assertTrue(voteText.contains("VOTE COUNTING REPORT"))
        assertTrue(voteText.contains("Candidate 1"))
        assertTrue(voteText.contains("NOTA"))
        assertTrue(voteText.contains("Ward 1"))
    }

    @Test
    fun testRecycleBinOperations() = runBlocking {
        repository.loadSampleVoters()
        val initialVoters = repository.allVoters.first()
        assertEquals(45, initialVoters.size)

        val target = initialVoters[0]

        // 1. Soft delete voter (move to Recycle Bin)
        repository.softDeleteVoter(target.id)
        var activeVoters = repository.allVoters.first()
        var deletedVoters = repository.deletedVoters.first()
        assertEquals(44, activeVoters.size)
        assertEquals(1, deletedVoters.size)
        assertEquals(target.id, deletedVoters[0].id)
        assertTrue(deletedVoters[0].isDeleted)

        // 2. Restore voter from Recycle Bin
        repository.restoreVoter(target.id)
        activeVoters = repository.allVoters.first()
        deletedVoters = repository.deletedVoters.first()
        assertEquals(45, activeVoters.size)
        assertEquals(0, deletedVoters.size)

        // 3. Move all to Recycle Bin
        repository.moveAllToRecycleBin()
        activeVoters = repository.allVoters.first()
        deletedVoters = repository.deletedVoters.first()
        assertEquals(0, activeVoters.size)
        assertEquals(45, deletedVoters.size)

        // 4. Restore all from Recycle Bin
        repository.restoreAllDeletedVoters()
        activeVoters = repository.allVoters.first()
        deletedVoters = repository.deletedVoters.first()
        assertEquals(45, activeVoters.size)
        assertEquals(0, deletedVoters.size)

        // 5. Soft delete and empty recycle bin
        repository.softDeleteVoter(target.id)
        repository.emptyRecycleBin()
        activeVoters = repository.allVoters.first()
        deletedVoters = repository.deletedVoters.first()
        assertEquals(44, activeVoters.size)
        assertEquals(0, deletedVoters.size)
    }
    @Test
    fun testNewSixColumnExcelKeepsSatnamWhenHouseContainsWardWord() {
        val rows = listOf(
            listOf("सीरियल नंबर", "नाम", "पिता / माता / पति का नाम", "घर नंबर", "EPIC नंबर", "वार्ड नंबर"),
            listOf("422", "सतनाम सिंह", "लखा सिंह", "वार्ड नं 12", "RGR1544121", "3")
        )
        val parsed = ExcelReader.processRowsToVoters(rows)
        assertEquals(1, parsed.voters.size)
        assertEquals("सतनाम सिंह", parsed.voters.single().name)
        assertEquals("वार्ड नं 12", parsed.voters.single().houseNumber)
        assertEquals(3, parsed.voters.single().wardNumber)
        assertEquals(0, parsed.skippedRows)
        assertEquals(1, parsed.wardsFound.size)
    }

    @Test
    fun testOldFiveColumnExcelStillImportsWithoutHouseNumber() {
        val rows = listOf(
            listOf("सीरियल नंबर", "नाम", "पिता / माता / पति का नाम", "EPIC नंबर", "वार्ड नंबर"),
            listOf("1", "पुराना मतदाता", "पिता का नाम", "RGR1234567", "1")
        )
        val parsed = ExcelReader.processRowsToVoters(rows)
        assertEquals(1, parsed.voters.size)
        assertEquals("", parsed.voters.single().houseNumber)
        assertEquals("RGR1234567", parsed.voters.single().epicNumber)
        assertEquals(1, parsed.voters.single().wardNumber)
    }

    @Test
    fun testWardExtractionUsesWardNumberNotEveryNumberInCell() {
        assertEquals(12, ExcelReader.extractWardNumber("वार्ड नं 12"))
        assertEquals(3, ExcelReader.extractWardNumber("Ward 3 / Part 2"))
        assertEquals(7, ExcelReader.extractWardNumber("७"))
    }

    @Test
    fun testCsvParserSupportsQuotedNewlinesAndSemicolonDelimiter() {
        val csv = "\"Serial\";\"Name\";\"Relative\";\"House\";\"EPIC\";\"Ward\"\r\n" +
            "\"1\";\"राम सिंह\";\"पिता का नाम\nदूसरी लाइन\";\"01\";\"RGR1234567\";\"3\"\r\n"
        val rows = ExcelReader.parseCsv(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        assertEquals(2, rows.size)
        assertEquals("पिता का नाम\nदूसरी लाइन", rows[1][2])
        assertEquals("01", rows[1][3])
    }

    @Test
    fun testXlsxUsesFirstVisibleWorksheetFromWorkbookRelationships() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Summary" sheetId="1" state="hidden" r:id="rId1"/>
                    <sheet name="Active Voters" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships>
                  <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Target="worksheets/sheet2.xml"/>
                </Relationships>""")
            entry("xl/worksheets/sheet1.xml", """<worksheet><sheetData>
                <row r="1"><c r="A1" t="inlineStr"><is><t>Wrong sheet</t></is></c></row>
                </sheetData></worksheet>""")
            entry("xl/worksheets/sheet2.xml", """<worksheet><sheetData>
                <row r="1">
                  <c r="A1" t="inlineStr"><is><t>सीरियल नंबर</t></is></c>
                  <c r="B1" t="inlineStr"><is><t>नाम</t></is></c>
                  <c r="C1" t="inlineStr"><is><t>पिता / माता / पति का नाम</t></is></c>
                  <c r="D1" t="inlineStr"><is><t>घर नंबर</t></is></c>
                  <c r="E1" t="inlineStr"><is><t>EPIC नंबर</t></is></c>
                  <c r="F1" t="inlineStr"><is><t>वार्ड नंबर</t></is></c>
                </row>
                <row r="2">
                  <c r="A2"><v>422</v></c>
                  <c r="B2" t="inlineStr"><is><t>सतनाम सिंह</t></is></c>
                  <c r="C2" t="inlineStr"><is><t>लखा सिंह</t></is></c>
                  <c r="D2" t="inlineStr"><is><t>वार्ड नं 12</t></is></c>
                  <c r="E2" t="inlineStr"><is><t>RGR1544121</t></is></c>
                  <c r="F2"><v>3</v></c>
                </row>
                </sheetData></worksheet>""")
        }
        val rows = ExcelReader.parseXlsx(ByteArrayInputStream(bytes.toByteArray()))
        val parsed = ExcelReader.processRowsToVoters(rows)
        assertEquals(1, parsed.voters.size)
        assertEquals("सतनाम सिंह", parsed.voters.single().name)
        assertEquals("वार्ड नं 12", parsed.voters.single().houseNumber)
    }

}
