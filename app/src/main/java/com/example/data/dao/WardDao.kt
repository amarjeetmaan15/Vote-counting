package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.Ward
import kotlinx.coroutines.flow.Flow

@Dao
interface WardDao {
    @Query("SELECT * FROM wards ORDER BY wardNumber ASC, id ASC")
    fun getAllWards(): Flow<List<Ward>>

    @Query("SELECT * FROM wards ORDER BY wardNumber ASC, id ASC")
    suspend fun getWardsList(): List<Ward>

    @Query("SELECT * FROM wards WHERE id = :id LIMIT 1")
    fun getWardById(id: Long): Flow<Ward?>

    @Query("SELECT * FROM wards WHERE id = :id LIMIT 1")
    suspend fun getWardByIdDirect(id: Long): Ward?

    @Query("SELECT MAX(wardNumber) FROM wards")
    suspend fun getMaxWardNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWard(ward: Ward): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWards(wards: List<Ward>)

    @Update
    suspend fun updateWard(ward: Ward)

    @Query("UPDATE wards SET totalPolledVotes = :totalVotes WHERE id = :wardId")
    suspend fun updateWardTotalPolledVotes(wardId: Long, totalVotes: Int?)

    @Delete
    suspend fun deleteWard(ward: Ward)

    @Query("DELETE FROM wards WHERE id = :id")
    suspend fun deleteWardById(id: Long)

    @Query("DELETE FROM wards")
    suspend fun deleteAllWards()
}
