package com.drone.quiz.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuestionEntity::class,
        BankEntity::class,
        PracticeRecordEntity::class,
        QuestionStatsEntity::class,
        ExamRecordEntity::class,
        ExamAnswerEntity::class,
        WrongBookEntity::class,
        StreakLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun bankDao(): BankDao
    abstract fun recordDao(): RecordDao
    abstract fun examDao(): ExamDao
    abstract fun wrongDao(): WrongDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * v1 → v2（v2.8.0 多题库 + 新题型）：正式 Migration，老用户学习数据完整保留。
         * - 新增 banks 表；questions 加 bankId（存量题默认归入内置无人机题库）与 answerText（填空/简答）
         * - exam_records 加 bankId 与 extraCounts（新题型计数）；exam_answers 加 detail（填空/简答作答明细）
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `banks` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("ALTER TABLE `questions` ADD COLUMN `bankId` TEXT NOT NULL DEFAULT 'drone'")
                db.execSQL("ALTER TABLE `questions` ADD COLUMN `answerText` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_bankId` ON `questions` (`bankId`)")
                db.execSQL("ALTER TABLE `exam_records` ADD COLUMN `bankId` TEXT NOT NULL DEFAULT 'drone'")
                db.execSQL("ALTER TABLE `exam_records` ADD COLUMN `extraCounts` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exam_answers` ADD COLUMN `detail` TEXT")
                db.execSQL(
                    "INSERT OR IGNORE INTO `banks` (`id`, `name`, `source`, `createdAt`) " +
                        "VALUES ('drone', '无人机装调题库', 'builtin', ${System.currentTimeMillis()})"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    // v2 起使用新库文件名，规避旧版本残留数据库的 schema 校验冲突
                    "drone_quiz_v2.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
