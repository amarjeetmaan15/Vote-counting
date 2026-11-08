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
    // Active Voters (Not in Recycle Bin)
    @Query("SELECT * FROM voters WHERE isDeleted = 0 ORDER BY wardNumber ASC, CAST(serialNumber AS INTEGER) ASC, id ASC")
    fun getAllVoters(): Flow<List<Voter>>

    @Query("SELECT * FROM voters WHERE wardNumber = :wardNumber AND isDeleted = 0 ORDER BY CAST(serialNumber AS INTEGER) ASC, id ASC")
    fun getVotersByWard(wardNumber: Int): Flow<List<Voter>>

    @Query("SELECT * FROM voters WHERE isDeleted = 0")
    suspend fun getAllVotersList(): List<Voter>

    @Query("SELECT COUNT(*) FROM voters WHERE isDeleted = 0")
    fun getTotalVotersCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM voters WHERE wardNumber = :wardNumber AND isDeleted = 0")
    fun getWardVotersCount(wardNumber: Int): Flow<Int>

    @Query("SELECT DISTINCT wardNumber FROM voters WHERE isDeleted = 0 ORDER BY wardNumber ASC")
    suspend fun getDistinctWardNumbers(): List<Int>

    @Query("SELECT DISTINCT wardNumber FROM voters WHERE isDeleted = 0 ORDER BY wardNumber ASC")
    fun getDistinctWardNumbersFlow(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoters(voters: List<Voter>)

    @Query("UPDATE voters SET attendanceStatus = :status WHERE id = :voterId")
    suspend fun updateAttendanceStatus(voterId: Long, status: AttendanceStatus)

    @Query("UPDATE voters SET votedCandidateId = :candidateId WHERE id = :voterId")
    suspend fun updateVotedCandidate(voterId: Long, candidateId: Long?)

    @Query("UPDATE voters SET attendanceStatus = 'NONE' WHERE isDeleted = 0")
    suspend fun resetAllAttendance()

    @Query("UPDATE voters SET votedCandidateId = NULL WHERE isDeleted = 0")
    suspend fun resetAllVotes()

    // ---------------- RECYCLE BIN OPERATIONS ---------------- //

    @Query("SELECT * FROM voters WHERE isDeleted = 1 ORDER BY deletedAt DESC, id DESC")
    fun getDeletedVoters(): Flow<List<Voter>>

    @Query("SELECT COUNT(*) FROM voters WHERE isDeleted = 1")
    fun getDeletedVotersCount(): Flow<Int>

    // Soft delete a single voter (move to Recycle Bin)
    @Query("UPDATE voters SET isDeleted = 1, deletedAt = :timestamp WHERE id = :voterId")
    suspend fun softDeleteVoter(voterId: Long, timestamp: Long = System.currentTimeMillis())

    // Soft delete all active voters (move all to Recycle Bin)
    @Query("UPDATE voters SET isDeleted = 1, deletedAt = :timestamp WHERE isDeleted = 0")
    suspend fun moveAllToRecycleBin(timestamp: Long = System.currentTimeMillis())

    // Restore a single voter from Recycle Bin
    @Query("UPDATE voters SET isDeleted = 0, deletedAt = NULL WHERE id = :voterId")
    suspend fun restoreVoter(voterId: Long)

    // Restore all voters from Recycle Bin
    @Query("UPDATE voters SET isDeleted = 0, deletedAt = NULL WHERE isDeleted = 1")
    suspend fun restoreAllDeletedVoters()

    // Permanently delete a single voter from database
    @Query("DELETE FROM voters WHERE id = :voterId")
    suspend fun permanentlyDeleteVoter(voterId: Long)

    // Empty Recycle Bin (permanently delete all soft-deleted voters)
    @Query("DELETE FROM voters WHERE isDeleted = 1")
    suspend fun emptyRecycleBin()

    // Permanent hard clear all voters
    @Query("DELETE FROM voters")
    suspend fun clearAllVoters()
}
