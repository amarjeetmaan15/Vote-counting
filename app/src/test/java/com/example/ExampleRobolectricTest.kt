package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
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
        repository = VoteRepository(db.voterDao(), db.candidateDao(), db.wardDao(), db.voteDao())
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
}
