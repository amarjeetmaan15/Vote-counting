package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AttendanceStatus {
    NONE,    // Not Checked (जाँच बाकी)
    GREEN,   // Green Tick: उम्मीदवार को लगता है वोट उसका है
    YELLOW,  // Yellow Tick: संशय / अनिश्चित
    RED      // Red Cross: उम्मीदवार को लगता है वोट उसका नहीं है
}

@Entity(
    tableName = "voters",
    indices = [
        Index(value = ["wardNumber"]),
        Index(value = ["votedCandidateId"])
    ]
)
data class Voter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serialNumber: String,
    val name: String,
    val relativeName: String,
    val epicNumber: String = "",
    val wardNumber: Int,
    val attendanceStatus: AttendanceStatus = AttendanceStatus.NONE,
    val votedCandidateId: Long? = null // null = not voted, otherwise candidate id (or NOTA id)
)
