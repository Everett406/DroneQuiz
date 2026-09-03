package com.drone.quiz.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestionEntity::class,
        PracticeRecordEntity::class,
        QuestionStatsEntity::class,
        ExamRecordEntity::class,
        ExamAnswerEntity::class,
        WrongBookEntity::class,
        StreakLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun recordDao(): RecordDao
    abstract fun examDao(): ExamDao
    abstract fun wrongDao(): WrongDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    // v2 起使用新库文件名，规避旧版本残留数据库的 schema 校验冲突
                    "drone_quiz_v2.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
