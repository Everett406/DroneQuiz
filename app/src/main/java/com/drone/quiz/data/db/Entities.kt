package com.drone.quiz.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "questions", indices = [Index("category"), Index("type")])
data class QuestionEntity(
    @PrimaryKey val id: Long,
    val category: String,
    val type: String, // "single" | "judge"
    val question: String,
    val options: String, // JSON array string
    val answer: Int,
    val explanation: String
)

@Entity(tableName = "practice_records", indices = [Index("qid"), Index("ts")])
data class PracticeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val qid: Long,
    val isCorrect: Boolean,
    val mode: String, // practice | exam | wrong
    val ts: Long
)

@Entity(tableName = "question_stats")
data class QuestionStatsEntity(
    @PrimaryKey val qid: Long,
    val attempts: Int = 0,
    val correct: Int = 0,
    val lastResult: Boolean? = null,
    val lastTs: Long = 0
)

@Entity(tableName = "exam_records")
data class ExamRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val total: Int,
    val singleCount: Int,
    val judgeCount: Int,
    val durationSec: Int,
    val score: Float? = null,
    val passed: Boolean? = null
)

@Entity(tableName = "exam_answers", indices = [Index("examId"), Index("qid")])
data class ExamAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val qid: Long,
    val picked: Int? = null, // null = unanswered
    val isCorrect: Boolean? = null
)

/**
 * 错题本。addedAt 为非空列，插入时必须显式赋值（修复 NOT NULL constraint 崩溃）。
 */
@Entity(tableName = "wrongbook", indices = [Index(value = ["qid"], unique = true)])
data class WrongBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val qid: Long,
    val addedAt: Long = 0,
    val wrongCount: Int = 1,
    val correctStreak: Int = 0,
    val removed: Boolean = false
)

@Entity(tableName = "streak_log")
data class StreakLogEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val answered: Int = 0,
    val correct: Int = 0
)
