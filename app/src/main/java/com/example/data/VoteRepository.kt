package com.example.data

import com.example.data.dao.CandidateDao
import com.example.data.dao.VoteDao
import com.example.data.dao.VoterDao
import com.example.data.dao.WardDao
import com.example.data.entity.AttendanceStatus
import com.example.data.entity.Candidate
import com.example.data.entity.Voter
import com.example.data.entity.Ward
import org.json.JSONArray
import org.json.JSONObject
import com.example.util.ExcelReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VoteRepository(
    private val voterDao: VoterDao,
    private val candidateDao: CandidateDao,
    private val wardDao: WardDao,
    private val voteDao: VoteDao
) {
    val allVoters: Flow<List<Voter>> = voterDao.getAllVoters()
    val totalVotersCount: Flow<Int> = voterDao.getTotalVotersCount()
    val distinctWardNumbers: Flow<List<Int>> = voterDao.getDistinctWardNumbersFlow()
    val allCandidates: Flow<List<Candidate>> = candidateDao.getAllCandidates()
    val allWards: Flow<List<Ward>> = wardDao.getAllWards()
    val deletedVoters: Flow<List<Voter>> = voterDao.getDeletedVoters()
    val deletedVotersCount: Flow<Int> = voterDao.getDeletedVotersCount()

    fun getVotersByWard(wardNumber: Int): Flow<List<Voter>> {
        return voterDao.getVotersByWard(wardNumber)
    }

    suspend fun updateVoterAttendance(voterId: Long, newStatus: AttendanceStatus) = withContext(Dispatchers.IO) {
        voterDao.updateAttendanceStatus(voterId, newStatus)
    }

    suspend fun updateVoterVote(voterId: Long, candidateId: Long?) = withContext(Dispatchers.IO) {
        voterDao.updateVotedCandidate(voterId, candidateId)
    }

    suspend fun loadVotersFromExcel(voters: List<Voter>, wardsFound: List<Int>) = withContext(Dispatchers.IO) {
        voterDao.clearAllVoters()
        voterDao.insertVoters(voters)

        // Automatically create Wards extracted from Excel
        wardDao.deleteAllWards()
        val distinctWards = if (wardsFound.isNotEmpty()) wardsFound else voters.map { it.wardNumber }.distinct().sorted()
        val wardsToInsert = distinctWards.map { Ward(wardNumber = it, name = "Ward $it") }
        wardDao.insertWards(wardsToInsert)

        // Ensure default candidates exist if none setup yet
        val currentCandidates = candidateDao.getCandidatesList().filter { !it.isNota }
        if (currentCandidates.isEmpty()) {
            candidateDao.insertCandidate(Candidate(name = "Candidate 1", candidateNumber = 1))
            candidateDao.insertCandidate(Candidate(name = "Candidate 2", candidateNumber = 2))
        }
        ensureNotaCandidate(true)
    }

    suspend fun loadSampleVoters() = withContext(Dispatchers.IO) {
        val sampleList = ExcelReader.getSampleVoters()
        val sampleWards = listOf(1, 2, 3)
        loadVotersFromExcel(sampleList, sampleWards)
    }

    suspend fun resetAllAttendance() = withContext(Dispatchers.IO) {
        voterDao.resetAllAttendance()
    }

    suspend fun resetAllVotes() = withContext(Dispatchers.IO) {
        voterDao.resetAllVotes()
    }

    suspend fun clearAllVotersData() = withContext(Dispatchers.IO) {
        voterDao.clearAllVoters()
        wardDao.deleteAllWards()
    }

    suspend fun addCandidate(name: String, isNota: Boolean = false): Long = withContext(Dispatchers.IO) {
        val current = candidateDao.getCandidatesList().filter { !it.isNota }
        val nextNumber = if (isNota) 9999 else if (current.isEmpty()) 1 else (current.maxOfOrNull { it.candidateNumber } ?: 0) + 1
        candidateDao.insertCandidate(Candidate(name = name.trim(), candidateNumber = nextNumber, isNota = isNota))
    }

    suspend fun ensureNotaCandidate(enable: Boolean = true): Long? = withContext(Dispatchers.IO) {
        val existing = candidateDao.getNotaCandidate()
        if (enable) {
            if (existing == null) {
                candidateDao.insertCandidate(Candidate(name = "नोटा (NOTA)", candidateNumber = 9999, isNota = true))
            } else {
                existing.id
            }
        } else {
            if (existing != null) {
                candidateDao.deleteCandidateById(existing.id)
            }
            null
        }
    }

    suspend fun updateCandidate(candidate: Candidate) = withContext(Dispatchers.IO) {
        candidateDao.updateCandidate(candidate)
    }

    suspend fun deleteCandidate(candidateId: Long) = withContext(Dispatchers.IO) {
        candidateDao.deleteCandidateById(candidateId)
    }

    suspend fun setCandidateList(names: List<String>) = withContext(Dispatchers.IO) {
        val nota = candidateDao.getNotaCandidate()
        candidateDao.deleteAllCandidates()
        var num = 1
        for (n in names) {
            if (n.isNotBlank()) {
                candidateDao.insertCandidate(Candidate(name = n.trim(), candidateNumber = num++, isNota = false))
            }
        }
        if (nota != null) {
            candidateDao.insertCandidate(nota)
        } else {
            candidateDao.insertCandidate(Candidate(name = "नोटा (NOTA)", candidateNumber = 9999, isNota = true))
        }
    }

    suspend fun initializeDefaultSetupIfNeeded() = withContext(Dispatchers.IO) {
        ensureNotaCandidate(true)
        val currentCandidates = candidateDao.getCandidatesList().filter { !it.isNota }
        if (currentCandidates.isEmpty()) {
            candidateDao.insertCandidate(Candidate(name = "Candidate 1", candidateNumber = 1))
            candidateDao.insertCandidate(Candidate(name = "Candidate 2", candidateNumber = 2))
        }
    }

    // Recycle Bin methods
    suspend fun softDeleteVoter(voterId: Long) = withContext(Dispatchers.IO) {
        voterDao.softDeleteVoter(voterId)
    }

    suspend fun moveAllToRecycleBin() = withContext(Dispatchers.IO) {
        voterDao.moveAllToRecycleBin()
    }

    suspend fun restoreVoter(voterId: Long) = withContext(Dispatchers.IO) {
        voterDao.restoreVoter(voterId)
    }

    suspend fun restoreAllDeletedVoters() = withContext(Dispatchers.IO) {
        voterDao.restoreAllDeletedVoters()
    }

    suspend fun permanentlyDeleteVoter(voterId: Long) = withContext(Dispatchers.IO) {
        voterDao.permanentlyDeleteVoter(voterId)
    }

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        voterDao.emptyRecycleBin()
    }

    // Portable JSON backup includes voter progress and candidate configuration.
    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val voters = voterDao.getAllVotersIncludingDeleted()
        val candidates = candidateDao.getCandidatesList()
        val root = JSONObject()
            .put("format", "vote-counting-backup")
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
        val candidateArray = JSONArray()
        candidates.forEach { candidate ->
            candidateArray.put(JSONObject()
                .put("id", candidate.id)
                .put("name", candidate.name)
                .put("number", candidate.candidateNumber)
                .put("nota", candidate.isNota))
        }
        val voterArray = JSONArray()
        voters.forEach { voter ->
            voterArray.put(JSONObject()
                .put("ward", voter.wardNumber)
                .put("serial", voter.serialNumber)
                .put("name", voter.name)
                .put("relative", voter.relativeName)
                .put("epic", voter.epicNumber)
                .put("attendance", voter.attendanceStatus.name)
                .put("candidateId", voter.votedCandidateId ?: JSONObject.NULL)
                .put("deleted", voter.isDeleted)
                .put("deletedAt", voter.deletedAt ?: JSONObject.NULL))
        }
        root.put("candidates", candidateArray)
        root.put("voters", voterArray)
        root.toString(2)
    }

    // Restore only progress onto the freshly imported Excel list. Match within each ward:
    // ward+serial first (with identity verification), then EPIC, then name+relative name.
    suspend fun restoreBackupJson(json: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        require(root.optString("format") == "vote-counting-backup") { "यह Vote Counting की बैकअप फाइल नहीं है" }
        require(root.optInt("version") == 1) { "बैकअप वर्जन सपोर्टेड नहीं है" }

        val currentVoters = voterDao.getAllVotersList()
        require(currentVoters.isNotEmpty()) { "पहले Excel वोटर लिस्ट अपलोड करें, फिर बैकअप लोड करें" }

        val savedCandidates = root.optJSONArray("candidates") ?: JSONArray()
        val oldCandidateNames = mutableMapOf<Long, String>()
        val newCandidates = mutableListOf<Candidate>()
        for (i in 0 until savedCandidates.length()) {
            val item = savedCandidates.getJSONObject(i)
            val oldId = item.optLong("id")
            val name = item.optString("name").trim()
            if (name.isNotEmpty()) {
                oldCandidateNames[oldId] = name
                newCandidates.add(Candidate(name = name, candidateNumber = item.optInt("number"), isNota = item.optBoolean("nota")))
            }
        }
        if (newCandidates.isNotEmpty()) {
            candidateDao.deleteAllCandidates()
            candidateDao.insertCandidates(newCandidates)
        }
        val currentCandidates = candidateDao.getCandidatesList()
        val candidateIdByName = currentCandidates.associate { it.name.trim().lowercase() to it.id }

        val savedVoters = root.optJSONArray("voters") ?: JSONArray()
        fun norm(value: String) = value.trim().lowercase().filter { it.isLetterOrDigit() }
        var restored = 0
        val updates = mutableListOf<Voter>()
        currentVoters.forEach { current ->
            val sameWard = (0 until savedVoters.length()).map { savedVoters.getJSONObject(it) }
                .filter { it.optInt("ward") == current.wardNumber }
            val serial = norm(current.serialNumber)
            val epic = norm(current.epicNumber)
            val name = norm(current.name)
            val relative = norm(current.relativeName)
            val match = sameWard.firstOrNull { saved ->
                val savedSerial = norm(saved.optString("serial"))
                val savedEpic = norm(saved.optString("epic"))
                val savedName = norm(saved.optString("name"))
                val savedRelative = norm(saved.optString("relative"))
                savedSerial == serial && (
                    (epic.isNotEmpty() && savedEpic.isNotEmpty() && epic == savedEpic) ||
                    (savedEpic.isEmpty() || epic.isEmpty()) && name == savedName &&
                        (relative.isEmpty() || savedRelative.isEmpty() || relative == savedRelative)
                )
            } ?: sameWard.firstOrNull {
                val savedEpic = norm(it.optString("epic"))
                epic.isNotEmpty() && savedEpic.isNotEmpty() && epic == savedEpic
            } ?: sameWard.firstOrNull {
                val savedName = norm(it.optString("name"))
                val savedRelative = norm(it.optString("relative"))
                name.isNotEmpty() && name == savedName &&
                    (relative.isEmpty() || savedRelative.isEmpty() || relative == savedRelative)
            }

            if (match != null) {
                val savedOldCandidateId = if (match.isNull("candidateId")) null else match.optLong("candidateId")
                val savedCandidateName = savedOldCandidateId?.let { oldCandidateNames[it] }
                val newCandidateId = savedCandidateName?.let { candidateIdByName[it.trim().lowercase()] }
                val status = runCatching { AttendanceStatus.valueOf(match.optString("attendance", "NONE")) }
                    .getOrDefault(AttendanceStatus.NONE)
                updates.add(current.copy(
                    attendanceStatus = status,
                    votedCandidateId = newCandidateId,
                    isDeleted = match.optBoolean("deleted", false),
                    deletedAt = if (match.isNull("deletedAt")) null else match.optLong("deletedAt")
                ))
                restored++
            }
        }
        if (updates.isNotEmpty()) voterDao.insertVoters(updates)
        restored
    }

}
