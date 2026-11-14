package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AttendanceStatus {
    NONE,    // Not Checked (जाँच बाकी)
    GREEN,   // Green Tick: उपस्थित / पक्ष में
    YELLOW,  // Yellow Tick: संशय / अनिश्चित
    RED      // Red Cross: विपक्ष / मृत / बोगस
}

@Entity(
    tableName = "voters",
    indices = [
        Index(value = ["wardNumber"]),
        Index(value = ["votedCandidateId"]),
        Index(value = ["isDeleted"])
    ]
)
data class Voter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serialNumber: String,
    val name: String,
    val relativeName: String,
    val houseNumber: String = "",
    val epicNumber: String = "",
    val wardNumber: Int,
    val attendanceStatus: AttendanceStatus = AttendanceStatus.NONE,
    val votedCandidateId: Long? = null, // null = not voted, otherwise candidate id (or NOTA id)
    val isDeleted: Boolean = false,     // Soft delete for Recycle Bin
    val deletedAt: Long? = null         // Timestamp when moved to Recycle Bin
)
