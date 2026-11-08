package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.VoteRepository
import com.example.data.entity.AttendanceStatus
import com.example.data.entity.Candidate
import com.example.data.entity.Voter
import com.example.data.entity.Ward
import com.example.util.ExcelReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    CHECK_VOTERS,
    CHECK_VOTERS_RESULTS,
    VOTE_COUNTING,
    VOTE_COUNTING_RESULTS,
    RECYCLE_BIN
}

data class CheckSummary(
    val totalVoters: Int = 0,
    val greenCount: Int = 0,
    val yellowCount: Int = 0,
    val redCount: Int = 0,
    val notCheckedCount: Int = 0
)

data class WardCheckResult(
    val wardNumber: Int,
    val summary: CheckSummary
)

data class CandidateVoteStat(
    val candidate: Candidate,
    val votes: Int,
    val percentage: Float
)

data class WardVoteResult(
    val wardNumber: Int,
    val totalVotesPolled: Int,
    val candidateVotes: List<CandidateVoteStat>
)

data class CheckVotersUiState(
    val voters: List<Voter> = emptyList(),
    val summary: CheckSummary = CheckSummary(),
    val availableWards: List<Int> = emptyList(),
    val selectedWard: Int? = null, // null = ALL
    val searchQuery: String = ""
)

data class VoteCountingUiState(
    val voters: List<Voter> = emptyList(),
    val candidates: List<Candidate> = emptyList(),
    val availableWards: List<Int> = emptyList(),
    val selectedWard: Int? = null, // null = ALL
    val totalPolled: Int = 0,
    val candidateVoteCounts: Map<Long, Int> = emptyMap(),
    val searchQuery: String = ""
)

data class ResultsUiState(
    val overallCheckSummary: CheckSummary = CheckSummary(),
    val wardCheckResults: List<WardCheckResult> = emptyList(),
    val overallCandidateStats: List<CandidateVoteStat> = emptyList(),
    val overallTotalPolled: Int = 0,
    val overallTotalVoters: Int = 0,
    val wardVoteResults: List<WardVoteResult> = emptyList(),
    val candidates: List<Candidate> = emptyList()
)

class VoteViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = VoteRepository(
        database.voterDao(),
        database.candidateDao(),
        database.wardDao(),
        database.voteDao()
    )

    val currentScreen = MutableStateFlow(AppScreen.HOME)
    val selectedWard = MutableStateFlow<Int?>(null) // null = ALL

    val searchQuery = MutableStateFlow("")

    val allVoters: StateFlow<List<Voter>> = repository.allVoters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCandidates: StateFlow<List<Candidate>> = repository.allCandidates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWards: StateFlow<List<Ward>> = repository.allWards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctWardNumbers: StateFlow<List<Int>> = repository.distinctWardNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedVoters: StateFlow<List<Voter>> = repository.deletedVoters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedCount: StateFlow<Int> = repository.deletedVotersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val checkVotersState: StateFlow<CheckVotersUiState> = combine(
        allVoters,
        distinctWardNumbers,
        selectedWard,
        searchQuery
    ) { voters, wards, wardFilter, query ->
        val filteredByWard = if (wardFilter == null) voters else voters.filter { it.wardNumber == wardFilter }

        val green = filteredByWard.count { it.attendanceStatus == AttendanceStatus.GREEN }
        val yellow = filteredByWard.count { it.attendanceStatus == AttendanceStatus.YELLOW }
        val red = filteredByWard.count { it.attendanceStatus == AttendanceStatus.RED }
        val notChecked = filteredByWard.count { it.attendanceStatus == AttendanceStatus.NONE }

        val finalVoters = if (query.isBlank()) {
            filteredByWard
        } else {
            val q = query.trim().lowercase()
            filteredByWard.filter {
                it.name.lowercase().contains(q) ||
                        it.serialNumber.contains(q) ||
                        it.epicNumber.lowercase().contains(q) ||
                        it.relativeName.lowercase().contains(q)
            }
        }

        CheckVotersUiState(
            voters = finalVoters,
            summary = CheckSummary(
                totalVoters = filteredByWard.size,
                greenCount = green,
                yellowCount = yellow,
                redCount = red,
                notCheckedCount = notChecked
            ),
            availableWards = wards,
            selectedWard = wardFilter,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CheckVotersUiState())

    val voteCountingState: StateFlow<VoteCountingUiState> = combine(
        allVoters,
        allCandidates,
        distinctWardNumbers,
        selectedWard,
        searchQuery
    ) { voters, candidates, wards, wardFilter, query ->
        val filteredByWard = if (wardFilter == null) voters else voters.filter { it.wardNumber == wardFilter }

        val countsMap = mutableMapOf<Long, Int>()
        var polledCount = 0
        for (voter in filteredByWard) {
            val cId = voter.votedCandidateId
            if (cId != null) {
                countsMap[cId] = (countsMap[cId] ?: 0) + 1
                polledCount++
            }
        }

        val finalVoters = if (query.isBlank()) {
            filteredByWard
        } else {
            val q = query.trim().lowercase()
            filteredByWard.filter {
                it.name.lowercase().contains(q) ||
                        it.serialNumber.contains(q) ||
                        it.epicNumber.lowercase().contains(q) ||
                        it.relativeName.lowercase().contains(q)
            }
        }

        VoteCountingUiState(
            voters = finalVoters,
            candidates = candidates,
            availableWards = wards,
            selectedWard = wardFilter,
            totalPolled = polledCount,
            candidateVoteCounts = countsMap,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoteCountingUiState())

    val resultsState: StateFlow<ResultsUiState> = combine(
        allVoters,
        allCandidates,
        distinctWardNumbers
    ) { voters, candidates, wards ->
        // Check Summary (ALL)
        val overallGreen = voters.count { it.attendanceStatus == AttendanceStatus.GREEN }
        val overallYellow = voters.count { it.attendanceStatus == AttendanceStatus.YELLOW }
        val overallRed = voters.count { it.attendanceStatus == AttendanceStatus.RED }
        val overallNotChecked = voters.count { it.attendanceStatus == AttendanceStatus.NONE }
        val overallCheck = CheckSummary(
            totalVoters = voters.size,
            greenCount = overallGreen,
            yellowCount = overallYellow,
            redCount = overallRed,
            notCheckedCount = overallNotChecked
        )

        // Check Summary (Ward-wise)
        val wardCheckList = wards.map { wardNum ->
            val wardVoters = voters.filter { it.wardNumber == wardNum }
            WardCheckResult(
                wardNumber = wardNum,
                summary = CheckSummary(
                    totalVoters = wardVoters.size,
                    greenCount = wardVoters.count { it.attendanceStatus == AttendanceStatus.GREEN },
                    yellowCount = wardVoters.count { it.attendanceStatus == AttendanceStatus.YELLOW },
                    redCount = wardVoters.count { it.attendanceStatus == AttendanceStatus.RED },
                    notCheckedCount = wardVoters.count { it.attendanceStatus == AttendanceStatus.NONE }
                )
            )
        }

        // Vote Counting (ALL)
        val totalPolled = voters.count { it.votedCandidateId != null }
        val overallCandidateStats = candidates.map { candidate ->
            val votes = voters.count { it.votedCandidateId == candidate.id }
            val pct = if (totalPolled > 0) (votes.toFloat() / totalPolled) * 100f else 0f
            CandidateVoteStat(candidate, votes, pct)
        }.sortedWith(
            compareBy<CandidateVoteStat> { it.candidate.isNota }
                .thenByDescending { it.votes }
        )

        // Vote Counting (Ward-wise)
        val wardVoteList = wards.map { wardNum ->
            val wardVoters = voters.filter { it.wardNumber == wardNum }
            val wardPolled = wardVoters.count { it.votedCandidateId != null }
            val stats = candidates.map { candidate ->
                val votes = wardVoters.count { it.votedCandidateId == candidate.id }
                val pct = if (wardPolled > 0) (votes.toFloat() / wardPolled) * 100f else 0f
                CandidateVoteStat(candidate, votes, pct)
            }.sortedWith(
                compareBy<CandidateVoteStat> { it.candidate.isNota }
                    .thenByDescending { it.votes }
            )
            WardVoteResult(
                wardNumber = wardNum,
                totalVotesPolled = wardPolled,
                candidateVotes = stats
            )
        }

        ResultsUiState(
            overallCheckSummary = overallCheck,
            wardCheckResults = wardCheckList,
            overallCandidateStats = overallCandidateStats,
            overallTotalPolled = totalPolled,
            overallTotalVoters = voters.size,
            wardVoteResults = wardVoteList,
            candidates = candidates
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResultsUiState())

    init {
        viewModelScope.launch {
            repository.initializeDefaultSetupIfNeeded()
        }
    }

    fun selectWardFilter(wardNum: Int?) {
        selectedWard.value = wardNum
    }

    fun setSearch(query: String) {
        searchQuery.value = query
    }

    fun toggleVoterAttendance(voterId: Long, currentStatus: AttendanceStatus, targetStatus: AttendanceStatus) {
        viewModelScope.launch {
            val newStatus = if (currentStatus == targetStatus) AttendanceStatus.NONE else targetStatus
            repository.updateVoterAttendance(voterId, newStatus)
        }
    }

    fun selectCandidateForVoter(voterId: Long, currentCandidateId: Long?, targetCandidateId: Long) {
        viewModelScope.launch {
            val newVote = if (currentCandidateId == targetCandidateId) null else targetCandidateId
            repository.updateVoterVote(voterId, newVote)
        }
    }

    fun loadExcelFromUri(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val parsedData = ExcelReader.parseExcelOrCsv(context, uri)
                if (parsedData.voters.isEmpty()) {
                    onResult(false, "Excel फाइल में कोई मतदाता डेटा नहीं मिला!")
                    return@launch
                }
                repository.loadVotersFromExcel(parsedData.voters, parsedData.wardsFound)
                onResult(true, "${parsedData.voters.size} वोटर्स और ${parsedData.wardsFound.size} वार्ड लोड किए गए!")
            } catch (e: Exception) {
                onResult(false, "फाइल पढ़ने में त्रुटि: ${e.message}")
            }
        }
    }

    fun loadSampleData(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.loadSampleVoters()
            onComplete()
        }
    }

    fun addCandidate(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCandidate(name, isNota = false)
        }
    }

    fun setCandidateList(names: List<String>) {
        viewModelScope.launch {
            repository.setCandidateList(names)
        }
    }

    fun resetAttendance() {
        viewModelScope.launch {
            repository.resetAllAttendance()
        }
    }

    fun resetVotes() {
        viewModelScope.launch {
            repository.resetAllVotes()
        }
    }

    fun clearAllVoters() {
        viewModelScope.launch {
            repository.clearAllVotersData()
        }
    }

    // Recycle Bin functions
    fun softDeleteVoter(voterId: Long) {
        viewModelScope.launch {
            repository.softDeleteVoter(voterId)
        }
    }

    fun moveAllToRecycleBin() {
        viewModelScope.launch {
            repository.moveAllToRecycleBin()
        }
    }

    fun restoreVoter(voterId: Long) {
        viewModelScope.launch {
            repository.restoreVoter(voterId)
        }
    }

    fun restoreAllFromRecycleBin() {
        viewModelScope.launch {
            repository.restoreAllDeletedVoters()
        }
    }

    fun permanentlyDeleteVoter(voterId: Long) {
        viewModelScope.launch {
            repository.permanentlyDeleteVoter(voterId)
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            repository.emptyRecycleBin()
        }
    }
}
