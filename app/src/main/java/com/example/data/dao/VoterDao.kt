package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.AttendanceStatus
import com.example.data.entity.Voter
import kotlinx.coroutines.flow.Flow

@Dao
interface VoterDao {
    @Query("SELECT * FROM voters ORDER BY wardNumber ASC, CAST(serialNumber AS INTEGER) ASC, id ASC")
    fun getAllVoters(): Flow<List<Voter>>

    @Query("SELECT * FROM voters WHERE wardNumber = :wardNumber ORDER BY CAST(serialNumber AS INTEGER) ASC, id ASC")
    fun getVotersByWard(wardNumber: Int): Flow<List<Voter>>

    @Query("SELECT * FROM voters")
    suspend fun getAllVotersList(): List<Voter>

    @Query("SELECT COUNT(*) FROM voters")
    fun getTotalVotersCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM voters WHERE wardNumber = :wardNumber")
    fun getWardVotersCount(wardNumber: Int): Flow<Int>

    @Query("SELECT DISTINCT wardNumber FROM voters ORDER BY wardNumber ASC")
    suspend fun getDistinctWardNumbers(): List<Int>

    @Query("SELECT DISTINCT wardNumber FROM voters ORDER BY wardNumber ASC")
    fun getDistinctWardNumbersFlow(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoters(voters: List<Voter>)

    @Query("UPDATE voters SET attendanceStatus = :status WHERE id = :voterId")
    suspend fun updateAttendanceStatus(voterId: Long, status: AttendanceStatus)

    @Query("UPDATE voters SET votedCandidateId = :candidateId WHERE id = :voterId")
    suspend fun updateVotedCandidate(voterId: Long, candidateId: Long?)

    @Query("UPDATE voters SET attendanceStatus = 'NONE'")
    suspend fun resetAllAttendance()

    @Query("UPDATE voters SET votedCandidateId = NULL")
    suspend fun resetAllVotes()

    @Query("DELETE FROM voters")
    suspend fun clearAllVoters()
}
