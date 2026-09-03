package com.drone.quiz.`data`.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _questionDao: Lazy<QuestionDao> = lazy {
    QuestionDao_Impl(this)
  }

  private val _recordDao: Lazy<RecordDao> = lazy {
    RecordDao_Impl(this)
  }

  private val _examDao: Lazy<ExamDao> = lazy {
    ExamDao_Impl(this)
  }

  private val _wrongDao: Lazy<WrongDao> = lazy {
    WrongDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "2f005da4030eebdbeee71f292d18c95b", "27d775682fd1657f62473c968f00c2a4") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `questions` (`id` INTEGER NOT NULL, `category` TEXT NOT NULL, `type` TEXT NOT NULL, `question` TEXT NOT NULL, `options` TEXT NOT NULL, `answer` INTEGER NOT NULL, `explanation` TEXT NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_category` ON `questions` (`category`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_type` ON `questions` (`type`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `practice_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `qid` INTEGER NOT NULL, `isCorrect` INTEGER NOT NULL, `mode` TEXT NOT NULL, `ts` INTEGER NOT NULL)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_records_qid` ON `practice_records` (`qid`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_records_ts` ON `practice_records` (`ts`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `question_stats` (`qid` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `correct` INTEGER NOT NULL, `lastResult` INTEGER, `lastTs` INTEGER NOT NULL, PRIMARY KEY(`qid`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `exam_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `total` INTEGER NOT NULL, `singleCount` INTEGER NOT NULL, `judgeCount` INTEGER NOT NULL, `durationSec` INTEGER NOT NULL, `score` REAL, `passed` INTEGER)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `exam_answers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `examId` INTEGER NOT NULL, `qid` INTEGER NOT NULL, `picked` INTEGER, `isCorrect` INTEGER)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_answers_examId` ON `exam_answers` (`examId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_answers_qid` ON `exam_answers` (`qid`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `wrongbook` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `qid` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `wrongCount` INTEGER NOT NULL, `correctStreak` INTEGER NOT NULL, `removed` INTEGER NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wrongbook_qid` ON `wrongbook` (`qid`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `streak_log` (`date` TEXT NOT NULL, `answered` INTEGER NOT NULL, `correct` INTEGER NOT NULL, PRIMARY KEY(`date`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2f005da4030eebdbeee71f292d18c95b')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `questions`")
        connection.execSQL("DROP TABLE IF EXISTS `practice_records`")
        connection.execSQL("DROP TABLE IF EXISTS `question_stats`")
        connection.execSQL("DROP TABLE IF EXISTS `exam_records`")
        connection.execSQL("DROP TABLE IF EXISTS `exam_answers`")
        connection.execSQL("DROP TABLE IF EXISTS `wrongbook`")
        connection.execSQL("DROP TABLE IF EXISTS `streak_log`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsQuestions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsQuestions.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("category", TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("question", TableInfo.Column("question", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("options", TableInfo.Column("options", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("answer", TableInfo.Column("answer", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestions.put("explanation", TableInfo.Column("explanation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysQuestions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesQuestions: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesQuestions.add(TableInfo.Index("index_questions_category", false, listOf("category"), listOf("ASC")))
        _indicesQuestions.add(TableInfo.Index("index_questions_type", false, listOf("type"), listOf("ASC")))
        val _infoQuestions: TableInfo = TableInfo("questions", _columnsQuestions, _foreignKeysQuestions, _indicesQuestions)
        val _existingQuestions: TableInfo = read(connection, "questions")
        if (!_infoQuestions.equals(_existingQuestions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |questions(com.drone.quiz.data.db.QuestionEntity).
              | Expected:
              |""".trimMargin() + _infoQuestions + """
              |
              | Found:
              |""".trimMargin() + _existingQuestions)
        }
        val _columnsPracticeRecords: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPracticeRecords.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPracticeRecords.put("qid", TableInfo.Column("qid", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPracticeRecords.put("isCorrect", TableInfo.Column("isCorrect", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPracticeRecords.put("mode", TableInfo.Column("mode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPracticeRecords.put("ts", TableInfo.Column("ts", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPracticeRecords: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPracticeRecords: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesPracticeRecords.add(TableInfo.Index("index_practice_records_qid", false, listOf("qid"), listOf("ASC")))
        _indicesPracticeRecords.add(TableInfo.Index("index_practice_records_ts", false, listOf("ts"), listOf("ASC")))
        val _infoPracticeRecords: TableInfo = TableInfo("practice_records", _columnsPracticeRecords, _foreignKeysPracticeRecords, _indicesPracticeRecords)
        val _existingPracticeRecords: TableInfo = read(connection, "practice_records")
        if (!_infoPracticeRecords.equals(_existingPracticeRecords)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |practice_records(com.drone.quiz.data.db.PracticeRecordEntity).
              | Expected:
              |""".trimMargin() + _infoPracticeRecords + """
              |
              | Found:
              |""".trimMargin() + _existingPracticeRecords)
        }
        val _columnsQuestionStats: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsQuestionStats.put("qid", TableInfo.Column("qid", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestionStats.put("attempts", TableInfo.Column("attempts", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestionStats.put("correct", TableInfo.Column("correct", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestionStats.put("lastResult", TableInfo.Column("lastResult", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQuestionStats.put("lastTs", TableInfo.Column("lastTs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysQuestionStats: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesQuestionStats: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoQuestionStats: TableInfo = TableInfo("question_stats", _columnsQuestionStats, _foreignKeysQuestionStats, _indicesQuestionStats)
        val _existingQuestionStats: TableInfo = read(connection, "question_stats")
        if (!_infoQuestionStats.equals(_existingQuestionStats)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |question_stats(com.drone.quiz.data.db.QuestionStatsEntity).
              | Expected:
              |""".trimMargin() + _infoQuestionStats + """
              |
              | Found:
              |""".trimMargin() + _existingQuestionStats)
        }
        val _columnsExamRecords: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsExamRecords.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamRecords.put("startedAt", TableInfo.Column("startedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamRecords.put("finishedAt", TableInfo.Column("finishedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamRecords.put("total", TableInfo.Column("total", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamRecords.put("singleCount", TableInfo.Column("singleCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamRecords.put("judgeCount", TableInfo.Column("judgeCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamRecords.put("durationSec", TableInfo.Column("durationSec", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamRecords.put("score", TableInfo.Column("score", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamRecords.put("passed", TableInfo.Column("passed", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysExamRecords: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesExamRecords: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoExamRecords: TableInfo = TableInfo("exam_records", _columnsExamRecords, _foreignKeysExamRecords, _indicesExamRecords)
        val _existingExamRecords: TableInfo = read(connection, "exam_records")
        if (!_infoExamRecords.equals(_existingExamRecords)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |exam_records(com.drone.quiz.data.db.ExamRecordEntity).
              | Expected:
              |""".trimMargin() + _infoExamRecords + """
              |
              | Found:
              |""".trimMargin() + _existingExamRecords)
        }
        val _columnsExamAnswers: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsExamAnswers.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamAnswers.put("examId", TableInfo.Column("examId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamAnswers.put("qid", TableInfo.Column("qid", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamAnswers.put("picked", TableInfo.Column("picked", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExamAnswers.put("isCorrect", TableInfo.Column("isCorrect", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysExamAnswers: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesExamAnswers: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesExamAnswers.add(TableInfo.Index("index_exam_answers_examId", false, listOf("examId"), listOf("ASC")))
        _indicesExamAnswers.add(TableInfo.Index("index_exam_answers_qid", false, listOf("qid"), listOf("ASC")))
        val _infoExamAnswers: TableInfo = TableInfo("exam_answers", _columnsExamAnswers, _foreignKeysExamAnswers, _indicesExamAnswers)
        val _existingExamAnswers: TableInfo = read(connection, "exam_answers")
        if (!_infoExamAnswers.equals(_existingExamAnswers)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |exam_answers(com.drone.quiz.data.db.ExamAnswerEntity).
              | Expected:
              |""".trimMargin() + _infoExamAnswers + """
              |
              | Found:
              |""".trimMargin() + _existingExamAnswers)
        }
        val _columnsWrongbook: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsWrongbook.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWrongbook.put("qid", TableInfo.Column("qid", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWrongbook.put("addedAt", TableInfo.Column("addedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWrongbook.put("wrongCount", TableInfo.Column("wrongCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWrongbook.put("correctStreak", TableInfo.Column("correctStreak", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsWrongbook.put("removed", TableInfo.Column("removed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysWrongbook: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesWrongbook: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesWrongbook.add(TableInfo.Index("index_wrongbook_qid", true, listOf("qid"), listOf("ASC")))
        val _infoWrongbook: TableInfo = TableInfo("wrongbook", _columnsWrongbook, _foreignKeysWrongbook, _indicesWrongbook)
        val _existingWrongbook: TableInfo = read(connection, "wrongbook")
        if (!_infoWrongbook.equals(_existingWrongbook)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |wrongbook(com.drone.quiz.data.db.WrongBookEntity).
              | Expected:
              |""".trimMargin() + _infoWrongbook + """
              |
              | Found:
              |""".trimMargin() + _existingWrongbook)
        }
        val _columnsStreakLog: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsStreakLog.put("date", TableInfo.Column("date", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStreakLog.put("answered", TableInfo.Column("answered", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStreakLog.put("correct", TableInfo.Column("correct", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysStreakLog: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesStreakLog: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoStreakLog: TableInfo = TableInfo("streak_log", _columnsStreakLog, _foreignKeysStreakLog, _indicesStreakLog)
        val _existingStreakLog: TableInfo = read(connection, "streak_log")
        if (!_infoStreakLog.equals(_existingStreakLog)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |streak_log(com.drone.quiz.data.db.StreakLogEntity).
              | Expected:
              |""".trimMargin() + _infoStreakLog + """
              |
              | Found:
              |""".trimMargin() + _existingStreakLog)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "questions", "practice_records", "question_stats", "exam_records", "exam_answers", "wrongbook", "streak_log")
  }

  public override fun clearAllTables() {
    super.performClear(false, "questions", "practice_records", "question_stats", "exam_records", "exam_answers", "wrongbook", "streak_log")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(QuestionDao::class, QuestionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(RecordDao::class, RecordDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ExamDao::class, ExamDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(WrongDao::class, WrongDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun questionDao(): QuestionDao = _questionDao.value

  public override fun recordDao(): RecordDao = _recordDao.value

  public override fun examDao(): ExamDao = _examDao.value

  public override fun wrongDao(): WrongDao = _wrongDao.value
}
