package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.Candidate
import kotlinx.coroutines.flow.Flow

@Dao
interface CandidateDao {
    @Query("SELECT * FROM candidates ORDER BY isNota ASC, candidateNumber ASC, id ASC")
    fun getAllCandidates(): Flow<List<Candidate>>

    @Query("SELECT * FROM candidates ORDER BY isNota ASC, candidateNumber ASC, id ASC")
    suspend fun getCandidatesList(): List<Candidate>

    @Query("SELECT * FROM candidates WHERE isNota = 1 LIMIT 1")
    suspend fun getNotaCandidate(): Candidate?

    @Query("SELECT COUNT(*) FROM candidates WHERE isNota = 0")
    fun getCandidateCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidate(candidate: Candidate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(candidates: List<Candidate>)

    @Update
    suspend fun updateCandidate(candidate: Candidate)

    @Delete
    suspend fun deleteCandidate(candidate: Candidate)

    @Query("DELETE FROM candidates WHERE id = :id")
    suspend fun deleteCandidateById(id: Long)

    @Query("DELETE FROM candidates")
    suspend fun deleteAllCandidates()
}
