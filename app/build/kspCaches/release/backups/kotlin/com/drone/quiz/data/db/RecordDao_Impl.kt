package com.drone.quiz.`data`.db

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class RecordDao_Impl(
  __db: RoomDatabase,
) : RecordDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPracticeRecordEntity: EntityInsertAdapter<PracticeRecordEntity>

  private val __insertAdapterOfQuestionStatsEntity: EntityInsertAdapter<QuestionStatsEntity>

  private val __upsertAdapterOfStreakLogEntity: EntityUpsertAdapter<StreakLogEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPracticeRecordEntity = object : EntityInsertAdapter<PracticeRecordEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `practice_records` (`id`,`qid`,`isCorrect`,`mode`,`ts`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PracticeRecordEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.qid)
        val _tmp: Int = if (entity.isCorrect) 1 else 0
        statement.bindLong(3, _tmp.toLong())
        statement.bindText(4, entity.mode)
        statement.bindLong(5, entity.ts)
      }
    }
    this.__insertAdapterOfQuestionStatsEntity = object : EntityInsertAdapter<QuestionStatsEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `question_stats` (`qid`,`attempts`,`correct`,`lastResult`,`lastTs`) VALUES (?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: QuestionStatsEntity) {
        statement.bindLong(1, entity.qid)
        statement.bindLong(2, entity.attempts.toLong())
        statement.bindLong(3, entity.correct.toLong())
        val _tmpLastResult: Boolean? = entity.lastResult
        val _tmp: Int? = _tmpLastResult?.let { if (it) 1 else 0 }
        if (_tmp == null) {
          statement.bindNull(4)
        } else {
          statement.bindLong(4, _tmp.toLong())
        }
        statement.bindLong(5, entity.lastTs)
      }
    }
    this.__upsertAdapterOfStreakLogEntity = EntityUpsertAdapter<StreakLogEntity>(object : EntityInsertAdapter<StreakLogEntity>() {
      protected override fun createQuery(): String = "INSERT INTO `streak_log` (`date`,`answered`,`correct`) VALUES (?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: StreakLogEntity) {
        statement.bindText(1, entity.date)
        statement.bindLong(2, entity.answered.toLong())
        statement.bindLong(3, entity.correct.toLong())
      }
    }, object : EntityDeleteOrUpdateAdapter<StreakLogEntity>() {
      protected override fun createQuery(): String = "UPDATE `streak_log` SET `date` = ?,`answered` = ?,`correct` = ? WHERE `date` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: StreakLogEntity) {
        statement.bindText(1, entity.date)
        statement.bindLong(2, entity.answered.toLong())
        statement.bindLong(3, entity.correct.toLong())
        statement.bindText(4, entity.date)
      }
    })
  }

  public override suspend fun insertRecord(r: PracticeRecordEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPracticeRecordEntity.insert(_connection, r)
  }

  public override suspend fun upsertStats(s: QuestionStatsEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfQuestionStatsEntity.insert(_connection, s)
  }

  public override suspend fun upsertStreak(s: StreakLogEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfStreakLogEntity.upsert(_connection, s)
  }

  public override suspend fun statsFor(qid: Long): QuestionStatsEntity? {
    val _sql: String = "SELECT * FROM question_stats WHERE qid = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, qid)
        val _columnIndexOfQid: Int = getColumnIndexOrThrow(_stmt, "qid")
        val _columnIndexOfAttempts: Int = getColumnIndexOrThrow(_stmt, "attempts")
        val _columnIndexOfCorrect: Int = getColumnIndexOrThrow(_stmt, "correct")
        val _columnIndexOfLastResult: Int = getColumnIndexOrThrow(_stmt, "lastResult")
        val _columnIndexOfLastTs: Int = getColumnIndexOrThrow(_stmt, "lastTs")
        val _result: QuestionStatsEntity?
        if (_stmt.step()) {
          val _tmpQid: Long
          _tmpQid = _stmt.getLong(_columnIndexOfQid)
          val _tmpAttempts: Int
          _tmpAttempts = _stmt.getLong(_columnIndexOfAttempts).toInt()
          val _tmpCorrect: Int
          _tmpCorrect = _stmt.getLong(_columnIndexOfCorrect).toInt()
          val _tmpLastResult: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfLastResult)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfLastResult).toInt()
          }
          _tmpLastResult = _tmp?.let { it != 0 }
          val _tmpLastTs: Long
          _tmpLastTs = _stmt.getLong(_columnIndexOfLastTs)
          _result = QuestionStatsEntity(_tmpQid,_tmpAttempts,_tmpCorrect,_tmpLastResult,_tmpLastTs)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun streakFor(date: String): StreakLogEntity? {
    val _sql: String = "SELECT * FROM streak_log WHERE date = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, date)
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfAnswered: Int = getColumnIndexOrThrow(_stmt, "answered")
        val _columnIndexOfCorrect: Int = getColumnIndexOrThrow(_stmt, "correct")
        val _result: StreakLogEntity?
        if (_stmt.step()) {
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpAnswered: Int
          _tmpAnswered = _stmt.getLong(_columnIndexOfAnswered).toInt()
          val _tmpCorrect: Int
          _tmpCorrect = _stmt.getLong(_columnIndexOfCorrect).toInt()
          _result = StreakLogEntity(_tmpDate,_tmpAnswered,_tmpCorrect)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun recentStreaks(n: Int): List<StreakLogEntity> {
    val _sql: String = "SELECT * FROM streak_log ORDER BY date DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, n.toLong())
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfAnswered: Int = getColumnIndexOrThrow(_stmt, "answered")
        val _columnIndexOfCorrect: Int = getColumnIndexOrThrow(_stmt, "correct")
        val _result: MutableList<StreakLogEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: StreakLogEntity
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpAnswered: Int
          _tmpAnswered = _stmt.getLong(_columnIndexOfAnswered).toInt()
          val _tmpCorrect: Int
          _tmpCorrect = _stmt.getLong(_columnIndexOfCorrect).toInt()
          _item = StreakLogEntity(_tmpDate,_tmpAnswered,_tmpCorrect)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun totalAnsweredFlow(): Flow<Int> {
    val _sql: String = "SELECT COALESCE(SUM(answered),0) FROM streak_log"
    return createFlow(__db, false, arrayOf("streak_log")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun answeredDistinctFlow(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM question_stats WHERE attempts > 0"
    return createFlow(__db, false, arrayOf("question_stats")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun totalCorrect(): Int {
    val _sql: String = "SELECT COALESCE(SUM(correct),0) FROM question_stats WHERE attempts > 0"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun totalAttempts(): Int {
    val _sql: String = "SELECT COALESCE(SUM(attempts),0) FROM question_stats WHERE attempts > 0"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun wrongCountFlow(): Flow<Int> {
    val _sql: String = "SELECT COUNT(DISTINCT qid) FROM wrongbook WHERE removed = 0"
    return createFlow(__db, false, arrayOf("wrongbook")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearRecords() {
    val _sql: String = "DELETE FROM practice_records"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearStats() {
    val _sql: String = "DELETE FROM question_stats"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearStreaks() {
    val _sql: String = "DELETE FROM streak_log"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
