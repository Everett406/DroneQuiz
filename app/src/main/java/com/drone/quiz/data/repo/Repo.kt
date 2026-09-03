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

    /**
     * 启动时保证题库就绪并处理版本升级（全流程原子化，避免"版本已写入但库为空"死锁）：
     * - 库空 → 导入内置题库
     * - 已记录题库版本 ≠ 内置题库版本 → 同一事务内清空全部学习数据后重新导入
     * - 解析/校验先行，失败不碰数据库；只有导入完全成功才返回新版本号
     *   （失败返回 -1，调用方不持久化版本 → 下次启动自动重试）
     *
     * @param storedBankVersion DataStore 中记录的已加载题库版本（0 = 从未记录）
     * @return 实际加载生效的题库版本；未发生任何导入时返回 -1
     */
    suspend fun ensureBankLoaded(context: Context, storedBankVersion: Int): Int = withContext(Dispatchers.IO) {
        val assetsVersion = runCatching {
            context.assets.open("questions.json").use { input ->
                json.decodeFromString<ImportBank>(input.readBytes().decodeToString()).version
            }
        }.getOrNull() ?: return@withContext -1

        val needsImport = qDao.count() == 0 || storedBankVersion != assetsVersion
        if (!needsImport) return@withContext -1

        val bytes = runCatching {
            context.assets.open("questions.json").use { it.readBytes() }
        }.getOrNull() ?: return@withContext -1

        // 先在内存中解析并校验全部题目；任何一题不合法都直接失败，不触碰数据库
        val entities = runCatching { buildEntities(bytes) }.getOrNull() ?: return@withContext -1

        // 单事务完成：升级时清理学习数据 + 清空旧题 + 写入新题（原子）
        db.withTransaction {
            if (storedBankVersion != 0 && qDao.count() > 0) {
                // 题库升级：旧学习数据的 qid 与新题库必然失配，整体重置最干净
                rDao.clearRecords(); rDao.clearStats(); rDao.clearStreaks()
                eDao.clearExams(); eDao.clearExamAnswers()
                wDao.clear()
            }
            qDao.clear()
            entities.chunked(400).forEach { qDao.insertAll(it) }
        }
        assetsVersion
    }

    private fun buildEntities(bytes: ByteArray): List<QuestionEntity> {
        val bank = json.decodeFromString<ImportBank>(bytes.decodeToString())
        require(bank.questions.isNotEmpty()) { "题库为空" }
        return bank.questions.map { q ->
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
    }

    suspend fun importBank(bytes: ByteArray): ImportResult = withContext(Dispatchers.IO) {
        val entities = buildEntities(bytes)
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
        // 空列表直接短路：Room 对 IN () 空集合会生成非法 SQL 导致崩溃
        val ids = qDao.idsByFilter(category, type)
            .let { if (random) it.shuffled() else it }
            .take(limit)
        if (ids.isEmpty()) return@withContext emptyList()
        // 关键：Room 的 IN (:ids) 返回顺序固定按主键升序，会把 shuffled 顺序完全吃掉
        // （v2.7.2 及之前"随机刷题=顺序刷题"的根因）→ 必须按洗牌后的 ids 保序重排
        val map = qDao.byIds(ids).associateBy { it.id }
        ids.mapNotNull { map[it]?.toQuestion() }
    }

    suspend fun loadWrongPractice(): List<Question> = withContext(Dispatchers.IO) {
        val ids = wDao.activeWrongIds()
        // 错题本为空时同样必须短路，否则进"错题特训"必崩（IN () 非法 SQL）
        if (ids.isEmpty()) return@withContext emptyList()
        qDao.byIds(ids).map { it.toQuestion() }
    }

    /** 按会话快照的 id 顺序取题（恢复上次刷题进度时保证与上次完全一致） */
    suspend fun loadPracticeByIds(ids: List<Long>): List<Question> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val map = qDao.byIds(ids).associateBy { it.id }
        ids.mapNotNull { map[it]?.toQuestion() }
    }

    /** 题目搜索：题干 / 选项 / 解析 全文 LIKE。 */
    suspend fun searchQuestions(query: String): List<Question> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else qDao.search(q).map { it.toQuestion() }
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
        bumpStreakBulk(1, if (correct) 1 else 0)
    }

    /** 批量累计当日打卡（模考交卷一次写入，避免逐题查询）。 */
    private suspend fun bumpStreakBulk(answered: Int, correct: Int) {
        if (answered <= 0) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val cur = rDao.streakFor(today)
        rDao.upsertStreak(
            StreakLogEntity(
                date = today,
                answered = (cur?.answered ?: 0) + answered,
                correct = (cur?.correct ?: 0) + correct
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
            // 题库为空时防御性短路：不建卷、不写 exam_records，返回空列表由 UI 提示
            if (sIds.isEmpty() && jIds.isEmpty()) return@withContext 0L to emptyList()
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
            bumpStreakBulk(questions.size, correctIds.size)
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

    fun recentExams(): Flow<List<ExamRecordEntity>> = eDao.recentExams(20)

    /**
     * 从数据库重建历史模考成绩（供"最近模考"记录点击进入成绩页使用）。
     * 每题作答在 saveExamAnswer 时已落库（picked + isCorrect），
     * 故任意已交卷记录均可精确重建得分与错题列表；未交卷/不存在 → null。
     */
    suspend fun loadExamOutcome(examId: Long): ExamOutcome? = withContext(Dispatchers.IO) {
        if (examId <= 0) return@withContext null
        val rec = eDao.examById(examId) ?: return@withContext null
        val score = rec.score ?: return@withContext null
        val answers = eDao.answersFor(examId)
        val map = qDao.byIds(answers.map { it.qid }).associateBy { it.id }
        ExamOutcome(
            score = score,
            passed = rec.passed ?: false,
            correct = answers.count { it.isCorrect == true },
            total = rec.total,
            wrongQuestions = answers
                .filter { it.isCorrect == false }
                .mapNotNull { map[it.qid]?.toQuestion() }
        )
    }

    /** 放弃考试：删除该次模考的记录与作答（不再残留"进行中"幽灵记录）；也用于用户主动删除已完成记录 */
    suspend fun abandonExam(examId: Long) = withContext(Dispatchers.IO) {
        if (examId <= 0) return@withContext
        db.withTransaction {
            eDao.deleteAnswersFor(examId)
            eDao.deleteExam(examId)
        }
    }

    /**
     * 恢复未完成的模考（进程重启后 SessionHolder 已空时从 DB 重建）。
     * 返回 null 表示无法恢复（不存在/已交卷/题目已被题库升级清除）。
     */
    suspend fun resumeExam(
        examId: Long
    ): Triple<ExamRecordEntity, List<Question>, Map<Long, Int>>? =
        withContext(Dispatchers.IO) {
            if (examId <= 0) return@withContext null
            val exam = eDao.examById(examId) ?: return@withContext null
            if (exam.score != null) return@withContext null // 已完成，无可恢复
            val answers = eDao.answersFor(examId)
            val qs = qDao.byIds(answers.map { it.qid }).map { it.toQuestion() }
            if (qs.isEmpty()) return@withContext null
            val qidSet = qs.map { it.id }.toSet()
            Triple(
                exam,
                qs,
                answers.filter { it.picked != null && it.qid in qidSet }
                    .associate { it.qid to it.picked!! }
            )
        }

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
            // 最近模考也属于做题记录：一并清空（含未完成的"进行中"残留）
            eDao.clearExamAnswers(); eDao.clearExams()
        }
    }

    /**
     * 最近 days 天里，每天首次刷题的时刻（小时浮点，19.5 = 19:30）。
     * 用于智能提醒：学习用户习惯的开始刷题时间，替代固定 20:00。
     */
    suspend fun habitStartHours(days: Int = 10): List<Float> = withContext(Dispatchers.IO) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val since = System.currentTimeMillis() - days * 86_400_000L
        rDao.practiceTsSince(since)
            .groupBy { fmt.format(java.util.Date(it)) }
            .map { (_, list) -> list.min() }   // 每天最早一次作答
            .map { ts ->
                val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
                c.get(java.util.Calendar.HOUR_OF_DAY) + c.get(java.util.Calendar.MINUTE) / 60f
            }
    }

    /** 今天是否已刷过题（已刷则智能提醒当天不再打扰）。 */
    suspend fun todayAnsweredCount(): Int = withContext(Dispatchers.IO) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        rDao.streakFor(fmt.format(java.util.Date()))?.answered ?: 0
    }
}
