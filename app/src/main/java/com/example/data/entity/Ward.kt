package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wards")
data class Ward(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wardNumber: Int,
    val name: String = "Ward $wardNumber",
    val totalPolledVotes: Int? = null // Optional / Not required
)
