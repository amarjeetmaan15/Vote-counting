package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voterDao(): VoterDao
    abstract fun candidateDao(): CandidateDao
    abstract fun wardDao(): WardDao
    abstract fun voteDao(): VoteDao

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE voters ADD COLUMN houseNumber TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vote_counting_db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
