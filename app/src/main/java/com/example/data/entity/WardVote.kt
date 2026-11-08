package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ward_votes",
    indices = [
        Index(value = ["wardId", "candidateId"], unique = true)
    ]
)
data class WardVote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wardId: Long,
    val candidateId: Long,
    val voteCount: Int = 0
)
