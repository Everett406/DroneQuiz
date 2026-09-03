package com.drone.quiz.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class CatCount(val category: String, val cnt: Int)

@Dao
interface QuestionDao {
    @Query("SELECT COUNT(*) FROM questions")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun count(): Int

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun byId(id: Long): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<QuestionEntity>

    @Query(
        "SELECT id FROM questions WHERE (:cat IS NULL OR category = :cat) " +
            "AND (:type IS NULL OR type = :type)"
    )
    suspend fun idsByFilter(cat: String?, type: String?): List<Long>

    @Query("SELECT DISTINCT category FROM questions ORDER BY category")
    suspend fun categories(): List<String>

    @Query(
        "SELECT * FROM questions WHERE question LIKE '%' || :q || '%' " +
            "OR options LIKE '%' || :q || '%' OR explanation LIKE '%' || :q || '%' " +
            "ORDER BY id LIMIT 80"
    )
    suspend fun search(q: String): List<QuestionEntity>

    @Query("SELECT category AS category, COUNT(*) AS cnt FROM questions GROUP BY category ORDER BY category")
    suspend fun catCounts(): List<CatCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<QuestionEntity>)

    @Query("DELETE FROM questions")
    suspend fun clear()
}

@Dao
interface RecordDao {
    @Insert
    suspend fun insertRecord(r: PracticeRecordEntity)

    @Query("SELECT * FROM question_stats WHERE qid = :qid")
    suspend fun statsFor(qid: Long): QuestionStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStats(s: QuestionStatsEntity)

    @Upsert
    suspend fun upsertStreak(s: StreakLogEntity)

    @Query("SELECT * FROM streak_log WHERE date = :date")
    suspend fun streakFor(date: String): StreakLogEntity?

    @Query("SELECT * FROM streak_log ORDER BY date DESC LIMIT :n")
    suspend fun recentStreaks(n: Int): List<StreakLogEntity>

    @Query("SELECT COALESCE(SUM(answered),0) FROM streak_log")
    fun totalAnsweredFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM question_stats WHERE attempts > 0")
    fun answeredDistinctFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(correct),0) FROM question_stats WHERE attempts > 0")
    suspend fun totalCorrect(): Int

    @Query("SELECT COALESCE(SUM(attempts),0) FROM question_stats WHERE attempts > 0")
    suspend fun totalAttempts(): Int

    @Query("SELECT COUNT(DISTINCT qid) FROM wrongbook WHERE removed = 0")
    fun wrongCountFlow(): Flow<Int>

    @Query("DELETE FROM practice_records")
    suspend fun clearRecords()

    @Query("DELETE FROM question_stats")
    suspend fun clearStats()

    @Query("DELETE FROM streak_log")
    suspend fun clearStreaks()
}

@Dao
interface ExamDao {
    @Insert
    suspend fun insertExam(e: ExamRecordEntity): Long

    @Insert
    suspend fun insertAnswers(list: List<ExamAnswerEntity>)

    @Query("UPDATE exam_records SET finishedAt = :finishedAt, score = :score, passed = :passed WHERE id = :examId")
    suspend fun finishExam(examId: Long, finishedAt: Long, score: Float, passed: Boolean)

    @Query("SELECT * FROM exam_records WHERE id = :examId")
    suspend fun examById(examId: Long): ExamRecordEntity?

    @Query("SELECT * FROM exam_records ORDER BY startedAt DESC LIMIT :n")
    fun recentExams(n: Int): Flow<List<ExamRecordEntity>>

    @Query("SELECT * FROM exam_answers WHERE examId = :examId")
    suspend fun answersFor(examId: Long): List<ExamAnswerEntity>

    @Query("UPDATE exam_answers SET picked = :picked, isCorrect = :correct WHERE examId = :examId AND qid = :qid")
    suspend fun updateAnswer(examId: Long, qid: Long, picked: Int, correct: Boolean)

    @Query("SELECT * FROM exam_answers WHERE examId = :examId AND picked IS NULL")
    suspend fun unanswered(examId: Long): List<ExamAnswerEntity>

    @Query("DELETE FROM exam_records")
    suspend fun clearExams()

    @Query("DELETE FROM exam_answers")
    suspend fun clearExamAnswers()

    @Query("DELETE FROM exam_records WHERE id = :examId")
    suspend fun deleteExam(examId: Long)

    @Query("DELETE FROM exam_answers WHERE examId = :examId")
    suspend fun deleteAnswersFor(examId: Long)
}

@Dao
interface WrongDao {
    @Query("SELECT * FROM wrongbook WHERE qid = :qid")
    suspend fun forQuestion(qid: Long): WrongBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: WrongBookEntity)

    @Query(
        "SELECT w.*, q.question AS question, q.category AS category, q.type AS type, " +
            "q.options AS options, q.answer AS answer, q.explanation AS explanation " +
            "FROM wrongbook w JOIN questions q ON w.qid = q.id " +
            "WHERE w.removed = 0 ORDER BY w.addedAt DESC"
    )
    fun activeWrongWithQuestions(): Flow<List<WrongWithQuestion>>

    @Query("SELECT qid FROM wrongbook WHERE removed = 0")
    suspend fun activeWrongIds(): List<Long>

    @Query("SELECT COUNT(*) FROM wrongbook WHERE removed = 0")
    fun wrongCountFlow(): Flow<Int>

    @Query("UPDATE wrongbook SET removed = 1 WHERE qid = :qid")
    suspend fun markRemoved(qid: Long)

    @Query("DELETE FROM wrongbook")
    suspend fun clear()

    @Query("UPDATE wrongbook SET removed = 0, correctStreak = 0 WHERE qid = :qid")
    suspend fun reAdd(qid: Long)
}

data class WrongWithQuestion(
    val id: Long,
    val qid: Long,
    val addedAt: Long,
    val wrongCount: Int,
    val correctStreak: Int,
    val removed: Boolean,
    val question: String,
    val category: String,
    val type: String,
    val options: String,
    val answer: Int,
    val explanation: String
)
