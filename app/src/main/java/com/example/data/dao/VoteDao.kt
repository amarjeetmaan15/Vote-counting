package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.WardVote
import kotlinx.coroutines.flow.Flow

@Dao
interface VoteDao {
    @Query("SELECT * FROM ward_votes WHERE wardId = :wardId")
    fun getVotesForWard(wardId: Long): Flow<List<WardVote>>

    @Query("SELECT * FROM ward_votes")
    fun getAllVotes(): Flow<List<WardVote>>

    @Query("SELECT * FROM ward_votes WHERE wardId = :wardId AND candidateId = :candidateId LIMIT 1")
    suspend fun getVote(wardId: Long, candidateId: Long): WardVote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateVote(vote: WardVote)

    @Query(
        """
        INSERT INTO ward_votes (wardId, candidateId, voteCount)
        VALUES (:wardId, :candidateId, 1)
        ON CONFLICT(wardId, candidateId)
        DO UPDATE SET voteCount = voteCount + 1
        """
    )
    suspend fun incrementVote(wardId: Long, candidateId: Long)

    @Query(
        """
        INSERT INTO ward_votes (wardId, candidateId, voteCount)
        VALUES (:wardId, :candidateId, 0)
        ON CONFLICT(wardId, candidateId)
        DO UPDATE SET voteCount = MAX(0, voteCount - 1)
        """
    )
    suspend fun decrementVote(wardId: Long, candidateId: Long)

    @Query(
        """
        INSERT INTO ward_votes (wardId, candidateId, voteCount)
        VALUES (:wardId, :candidateId, :count)
        ON CONFLICT(wardId, candidateId)
        DO UPDATE SET voteCount = :count
        """
    )
    suspend fun setVoteCount(wardId: Long, candidateId: Long, count: Int)

    @Query("DELETE FROM ward_votes WHERE candidateId = :candidateId")
    suspend fun deleteVotesForCandidate(candidateId: Long)

    @Query("DELETE FROM ward_votes WHERE wardId = :wardId")
    suspend fun deleteVotesForWard(wardId: Long)

    @Query("UPDATE ward_votes SET voteCount = 0 WHERE wardId = :wardId")
    suspend fun resetWardVotes(wardId: Long)
}
