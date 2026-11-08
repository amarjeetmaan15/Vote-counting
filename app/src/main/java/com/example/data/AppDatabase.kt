package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.CandidateDao
import com.example.data.dao.VoteDao
import com.example.data.dao.VoterDao
import com.example.data.dao.WardDao
import com.example.data.entity.Candidate
import com.example.data.entity.Voter
import com.example.data.entity.Ward
import com.example.data.entity.WardVote

@Database(
    entities = [Voter::class, Candidate::class, Ward::class, WardVote::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voterDao(): VoterDao
    abstract fun candidateDao(): CandidateDao
    abstract fun wardDao(): WardDao
    abstract fun voteDao(): VoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vote_counting_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
