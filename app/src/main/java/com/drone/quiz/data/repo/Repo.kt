package com.drone.quiz.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.drone.quiz.data.db.AppDatabase
import com.drone.quiz.data.db.CatCount
import com.drone.quiz.data.db.ExamAnswerEntity
import com.drone.quiz.data.db.ExamRecordEntity
import com.drone.quiz.data.db.PracticeRecordEntity
import com.drone.quiz.data.db.QuestionEntity
import com.drone.quiz.data.db.QuestionStatsEntity
import com.drone.quiz.data.db.StreakLogEntity
import com.drone.quiz.data.db.WrongBookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class ImportQuestion(
    val id: Long? = null,
    val category: String = "未分类",
    val type: String = "single",
    val question: String,
    val options: List<String> = emptyList(),
    val answer: Int,
    val explanation: String = ""
)

@Serializable
data class ImportBank(
    val version: Int = 1,
    val questions: List<ImportQuestion>
)

data class Question(
    val id: Long,
    val category: String,
    val isJudge: Boolean,
    val text: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
) {
    val optionsOrJudge: List<String>
        get() = if (isJudge) listOf("正确", "错误") else options
}

private val json = Json { ignoreUnknownKeys = true }

class Repo(private val db: AppDatabase) {

    private val qDao = db.questionDao()
    private val rDao = db.recordDao()
    private val eDao = db.examDao()
    private val wDao = db.wrongDao()

    // ---------- 题库 ----------

    fun countFlow(): Flow<Int> = qDao.countFlow()

    suspend fun ensureBankLoaded(context: Context) = withContext(Dispatchers.IO) {
        if (qDao.count() == 0) {
            runCatching {
                context.assets.open("questions.json").use { importBank(it.readBytes()) }
            }
        }
    }

    suspend fun importBank(bytes: ByteArray): ImportResult = withContext(Dispatchers.IO) {
        val bank = json.decodeFromString<ImportBank>(bytes.decodeToString())
        require(bank.questions.isNotEmpty()) { "题库为空" }
        val entities = bank.questions.map { q ->
            val isJudge = q.type == "judge"
            val opts = if (isJudge) listOf("正确", "错误") else q.options
            require(opts.size >= 2) { "第 ${q.id ?: 0} 题选项不足" }
            require(q.answer in opts.indices) { "第 ${q.id ?: 0} 题答案越界" }
            val id = q.id ?: stableHash(q.question)
            QuestionEntity(
                id = id,
                category = q.category.ifBlank { "未分类" },
                type = if (isJudge) "judge" else "single",
                question = q.question,
                options = json.encodeToString(opts),
                answer = q.answer,
                explanation = q.explanation
            )
        }.distinctBy { it.id }.let { list ->
            // 保证 id 唯一：冲突时重新生成
            val seen = HashSet<Long>()
            list.map { e ->
                if (seen.add(e.id)) e else e.copy(id = stableHash(e.question + seen.size))
            }
        }
        db.withTransaction {
            qDao.clear()
            entities.chunked(400).forEach { qDao.insertAll(it) }
        }
        ImportResult(entities.size, entities.map { it.category }.distinct().sorted())
    }

    data class ImportResult(val count: Int, val categories: List<String>)

    private fun stableHash(s: String): Long = 1125899906842597L.let { h ->
        s.fold(h) { acc, c -> 31 * acc + c.code }.let { if (it < 0) -it else it }
    }

    suspend fun categories(): List<CatCount> = qDao.catCounts()

    // ---------- 刷题 ----------

    suspend fun loadPractice(
        category: String?,
        type: String?,
        random: Boolean,
        limit: Int = 800
    ): List<Question> = withContext(Dispatchers.IO) {
        val ids = qDao.idsByFilter(category, type)
            .let { if (random) it.shuffled() else it }
            .take(limit)
        qDao.byIds(ids).map { it.toQuestion() }.let { list ->
            if (random) list else list.sortedBy { it.id }
        }
    }

    suspend fun loadWrongPractice(): List<Question> = withContext(Dispatchers.IO) {
        val ids = wDao.activeWrongIds()
        qDao.byIds(ids).map { it.toQuestion() }
    }

    private fun QuestionEntity.toQuestion() = Question(
        id = id,
        category = category,
        isJudge = type == "judge",
        text = question,
        options = json.decodeFromString(options),
        answer = answer,
        explanation = explanation
    )

    /**
     * 记录一次作答（刷题/错题模式）。
     * 错题本插入时显式 addedAt = System.currentTimeMillis()，从根源避免 NOT NULL 崩溃。
     */
    suspend fun recordAnswer(
        qid: Long,
        isCorrect: Boolean,
        mode: String,
        removeThreshold: Int
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            rDao.insertRecord(PracticeRecordEntity(qid = qid, isCorrect = isCorrect, mode = mode, ts = now))
            val st = rDao.statsFor(qid)
            rDao.upsertStats(
                QuestionStatsEntity(
                    qid = qid,
                    attempts = (st?.attempts ?: 0) + 1,
                    correct = (st?.correct ?: 0) + if (isCorrect) 1 else 0,
                    lastResult = isCorrect,
                    lastTs = now
                )
            )
            val wrong = wDao.forQuestion(qid)
            if (!isCorrect) {
                // 加入或强化错题（addedAt 始终显式赋值）
                if (wrong == null) {
                    wDao.upsert(WrongBookEntity(qid = qid, addedAt = now, wrongCount = 1, correctStreak = 0))
                } else {
                    wDao.upsert(
                        wrong.copy(
                            addedAt = if (wrong.removed) now else wrong.addedAt,
                            wrongCount = wrong.wrongCount + 1,
                            correctStreak = 0,
                            removed = false
                        )
                    )
                }
            } else if (wrong != null && !wrong.removed) {
                val streak = wrong.correctStreak + 1
                if (streak >= removeThreshold.coerceIn(1, 3)) {
                    wDao.upsert(wrong.copy(correctStreak = streak, removed = true))
                } else {
                    wDao.upsert(wrong.copy(correctStreak = streak))
                }
            }
            bumpStreak(isCorrect)
        }
    }

    private suspend fun bumpStreak(correct: Boolean) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val cur = rDao.streakFor(today)
        rDao.upsertStreak(
            StreakLogEntity(
                date = today,
                answered = (cur?.answered ?: 0) + 1,
                correct = (cur?.correct ?: 0) + if (correct) 1 else 0
            )
        )
    }

    // ---------- 模考 ----------

    suspend fun startExam(total: Int, judgeRatio: Float, durationSec: Int, random: Boolean): Pair<Long, List<Question>> =
        withContext(Dispatchers.IO) {
            val allSingle = qDao.idsByFilter(null, "single")
            val allJudge = qDao.idsByFilter(null, "judge")
            val wantJudge = if (allJudge.isEmpty()) 0 else ((total * judgeRatio).toInt()).coerceAtMost(allJudge.size)
            val wantSingle = (total - wantJudge).coerceAtMost(allSingle.size)
            val sIds = (if (random) allSingle.shuffled() else allSingle).take(wantSingle)
            val jIds = (if (random) allJudge.shuffled() else allJudge).take(wantJudge)
            val qs = qDao.byIds(sIds + jIds).map { it.toQuestion() }
                .let { if (random) it.shuffled() else it.sortedWith(compareBy({ it.isJudge }, { it.id })) }
            val examId = db.withTransaction {
                val id = eDao.insertExam(
                    ExamRecordEntity(
                        startedAt = System.currentTimeMillis(),
                        total = qs.size,
                        singleCount = qs.count { !it.isJudge },
                        judgeCount = qs.count { it.isJudge },
                        durationSec = durationSec
                    )
                )
                eDao.insertAnswers(qs.map { ExamAnswerEntity(examId = id, qid = it.id, picked = null, isCorrect = null) })
                id
            }
            examId to qs
        }

    suspend fun saveExamAnswer(examId: Long, qid: Long, picked: Int, correct: Boolean) =
        withContext(Dispatchers.IO) { eDao.updateAnswer(examId, qid, picked, correct) }

    /**
     * 交卷：计分、写错题本（addedAt 显式赋值，修复崩溃）、更新打卡。
     */
    suspend fun submitExam(
        examId: Long,
        questions: List<Question>,
        picked: Map<Long, Int>,
        durationSec: Int,
        passScore: Int,
        removeThreshold: Int
    ): ExamOutcome = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val correctIds = questions.filter { picked[it.id] == it.answer }.map { it.id }
        val score = if (questions.isEmpty()) 0f else correctIds.size * 100f / questions.size
        val passed = score >= passScore
        db.withTransaction {
            eDao.finishExam(examId, now, score, passed)
            questions.forEach { q ->
                val p = picked[q.id]
                val ok = p == q.answer
                rDao.insertRecord(
                    PracticeRecordEntity(qid = q.id, isCorrect = ok, mode = "exam", ts = now)
                )
                val st = rDao.statsFor(q.id)
                rDao.upsertStats(
                    QuestionStatsEntity(
                        qid = q.id,
                        attempts = (st?.attempts ?: 0) + 1,
                        correct = (st?.correct ?: 0) + if (ok) 1 else 0,
                        lastResult = ok,
                        lastTs = now
                    )
                )
                val wrong = wDao.forQuestion(q.id)
                if (!ok) {
                    if (wrong == null) {
                        wDao.upsert(WrongBookEntity(qid = q.id, addedAt = now, wrongCount = 1, correctStreak = 0))
                    } else {
                        wDao.upsert(
                            wrong.copy(
                                addedAt = if (wrong.removed) now else wrong.addedAt,
                                wrongCount = wrong.wrongCount + 1,
                                correctStreak = 0,
                                removed = false
                            )
                        )
                    }
                } else if (wrong != null && !wrong.removed) {
                    val streak = wrong.correctStreak + 1
                    if (streak >= removeThreshold.coerceIn(1, 3)) {
                        wDao.upsert(wrong.copy(correctStreak = streak, removed = true))
                    } else {
                        wDao.upsert(wrong.copy(correctStreak = streak))
                    }
                }
            }
            bumpStreak(correctIds.size >= 0)
        }
        ExamOutcome(
            score = score,
            passed = passed,
            correct = correctIds.size,
            total = questions.size,
            wrongQuestions = questions.filter { picked[it.id] != it.answer }
        )
    }

    data class ExamOutcome(
        val score: Float,
        val passed: Boolean,
        val correct: Int,
        val total: Int,
        val wrongQuestions: List<Question>
    )

    suspend fun recentExams(): Flow<List<ExamRecordEntity>> = eDao.recentExams(20)

    // ---------- 错题本 ----------

    fun activeWrong(): Flow<List<com.drone.quiz.data.db.WrongWithQuestion>> = wDao.activeWrongWithQuestions()

    fun wrongCountFlow(): Flow<Int> = wDao.wrongCountFlow()

    suspend fun removeWrongForever(qid: Long) = withContext(Dispatchers.IO) { wDao.markRemoved(qid) }

    // ---------- 首页统计 ----------

    fun totalAnsweredFlow(): Flow<Int> = rDao.totalAnsweredFlow()
    fun answeredDistinctFlow(): Flow<Int> = rDao.answeredDistinctFlow()
    fun wrongCount(): Flow<Int> = wDao.wrongCountFlow()

    suspend fun last7Days(): List<DayStat> = withContext(Dispatchers.IO) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val map = rDao.recentStreaks(30).associateBy { it.date }
        (6 downTo 0).map { off ->
            val d = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -off)
            }.time
            val key = fmt.format(d)
            val s = map[key]
            DayStat(
                label = listOf("日", "一", "二", "三", "四", "五", "六")[
                    java.util.Calendar.getInstance().apply { time = d }.get(java.util.Calendar.DAY_OF_WEEK) - 1
                ],
                isToday = off == 0,
                answered = s?.answered ?: 0,
                correct = s?.correct ?: 0
            )
        }
    }

    suspend fun accuracy(): Float = withContext(Dispatchers.IO) {
        val a = rDao.totalAttempts()
        val c = rDao.totalCorrect()
        if (a == 0) 0f else c.toFloat() / a
    }

    data class DayStat(val label: String, val isToday: Boolean, val answered: Int, val correct: Int)

    suspend fun streakDays(): Int = withContext(Dispatchers.IO) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val dates = rDao.recentStreaks(90).map { it.date }.toSet()
        var count = 0
        var off = 0
        while (true) {
            val d = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -off)
            }.time
            if (fmt.format(d) in dates) {
                count++
                off++
            } else break
        }
        count
    }

    suspend fun clearAllRecords() = withContext(Dispatchers.IO) {
        db.withTransaction {
            rDao.clearRecords(); rDao.clearStats(); rDao.clearStreaks(); wDao.clear()
        }
    }
}
