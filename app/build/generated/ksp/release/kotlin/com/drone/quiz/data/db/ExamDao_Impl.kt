package com.drone.quiz.`data`.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Float
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
public class ExamDao_Impl(
  __db: RoomDatabase,
) : ExamDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfExamRecordEntity: EntityInsertAdapter<ExamRecordEntity>

  private val __insertAdapterOfExamAnswerEntity: EntityInsertAdapter<ExamAnswerEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfExamRecordEntity = object : EntityInsertAdapter<ExamRecordEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `exam_records` (`id`,`startedAt`,`finishedAt`,`total`,`singleCount`,`judgeCount`,`durationSec`,`score`,`passed`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ExamRecordEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.startedAt)
        val _tmpFinishedAt: Long? = entity.finishedAt
        if (_tmpFinishedAt == null) {
          statement.bindNull(3)
        } else {
          statement.bindLong(3, _tmpFinishedAt)
        }
        statement.bindLong(4, entity.total.toLong())
        statement.bindLong(5, entity.singleCount.toLong())
        statement.bindLong(6, entity.judgeCount.toLong())
        statement.bindLong(7, entity.durationSec.toLong())
        val _tmpScore: Float? = entity.score
        if (_tmpScore == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpScore.toDouble())
        }
        val _tmpPassed: Boolean? = entity.passed
        val _tmp: Int? = _tmpPassed?.let { if (it) 1 else 0 }
        if (_tmp == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmp.toLong())
        }
      }
    }
    this.__insertAdapterOfExamAnswerEntity = object : EntityInsertAdapter<ExamAnswerEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `exam_answers` (`id`,`examId`,`qid`,`picked`,`isCorrect`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ExamAnswerEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.examId)
        statement.bindLong(3, entity.qid)
        val _tmpPicked: Int? = entity.picked
        if (_tmpPicked == null) {
          statement.bindNull(4)
        } else {
          statement.bindLong(4, _tmpPicked.toLong())
        }
        val _tmpIsCorrect: Boolean? = entity.isCorrect
        val _tmp: Int? = _tmpIsCorrect?.let { if (it) 1 else 0 }
        if (_tmp == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmp.toLong())
        }
      }
    }
  }

  public override suspend fun insertExam(e: ExamRecordEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfExamRecordEntity.insertAndReturnId(_connection, e)
    _result
  }

  public override suspend fun insertAnswers(list: List<ExamAnswerEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfExamAnswerEntity.insert(_connection, list)
  }

  public override suspend fun examById(examId: Long): ExamRecordEntity? {
    val _sql: String = "SELECT * FROM exam_records WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, examId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "startedAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfTotal: Int = getColumnIndexOrThrow(_stmt, "total")
        val _columnIndexOfSingleCount: Int = getColumnIndexOrThrow(_stmt, "singleCount")
        val _columnIndexOfJudgeCount: Int = getColumnIndexOrThrow(_stmt, "judgeCount")
        val _columnIndexOfDurationSec: Int = getColumnIndexOrThrow(_stmt, "durationSec")
        val _columnIndexOfScore: Int = getColumnIndexOrThrow(_stmt, "score")
        val _columnIndexOfPassed: Int = getColumnIndexOrThrow(_stmt, "passed")
        val _result: ExamRecordEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpStartedAt: Long
          _tmpStartedAt = _stmt.getLong(_columnIndexOfStartedAt)
          val _tmpFinishedAt: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmpFinishedAt = null
          } else {
            _tmpFinishedAt = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          val _tmpSingleCount: Int
          _tmpSingleCount = _stmt.getLong(_columnIndexOfSingleCount).toInt()
          val _tmpJudgeCount: Int
          _tmpJudgeCount = _stmt.getLong(_columnIndexOfJudgeCount).toInt()
          val _tmpDurationSec: Int
          _tmpDurationSec = _stmt.getLong(_columnIndexOfDurationSec).toInt()
          val _tmpScore: Float?
          if (_stmt.isNull(_columnIndexOfScore)) {
            _tmpScore = null
          } else {
            _tmpScore = _stmt.getDouble(_columnIndexOfScore).toFloat()
          }
          val _tmpPassed: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfPassed)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfPassed).toInt()
          }
          _tmpPassed = _tmp?.let { it != 0 }
          _result = ExamRecordEntity(_tmpId,_tmpStartedAt,_tmpFinishedAt,_tmpTotal,_tmpSingleCount,_tmpJudgeCount,_tmpDurationSec,_tmpScore,_tmpPassed)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun recentExams(n: Int): Flow<List<ExamRecordEntity>> {
    val _sql: String = "SELECT * FROM exam_records ORDER BY startedAt DESC LIMIT ?"
    return createFlow(__db, false, arrayOf("exam_records")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, n.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "startedAt")
        val _columnIndexOfFinishedAt: Int = getColumnIndexOrThrow(_stmt, "finishedAt")
        val _columnIndexOfTotal: Int = getColumnIndexOrThrow(_stmt, "total")
        val _columnIndexOfSingleCount: Int = getColumnIndexOrThrow(_stmt, "singleCount")
        val _columnIndexOfJudgeCount: Int = getColumnIndexOrThrow(_stmt, "judgeCount")
        val _columnIndexOfDurationSec: Int = getColumnIndexOrThrow(_stmt, "durationSec")
        val _columnIndexOfScore: Int = getColumnIndexOrThrow(_stmt, "score")
        val _columnIndexOfPassed: Int = getColumnIndexOrThrow(_stmt, "passed")
        val _result: MutableList<ExamRecordEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ExamRecordEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpStartedAt: Long
          _tmpStartedAt = _stmt.getLong(_columnIndexOfStartedAt)
          val _tmpFinishedAt: Long?
          if (_stmt.isNull(_columnIndexOfFinishedAt)) {
            _tmpFinishedAt = null
          } else {
            _tmpFinishedAt = _stmt.getLong(_columnIndexOfFinishedAt)
          }
          val _tmpTotal: Int
          _tmpTotal = _stmt.getLong(_columnIndexOfTotal).toInt()
          val _tmpSingleCount: Int
          _tmpSingleCount = _stmt.getLong(_columnIndexOfSingleCount).toInt()
          val _tmpJudgeCount: Int
          _tmpJudgeCount = _stmt.getLong(_columnIndexOfJudgeCount).toInt()
          val _tmpDurationSec: Int
          _tmpDurationSec = _stmt.getLong(_columnIndexOfDurationSec).toInt()
          val _tmpScore: Float?
          if (_stmt.isNull(_columnIndexOfScore)) {
            _tmpScore = null
          } else {
            _tmpScore = _stmt.getDouble(_columnIndexOfScore).toFloat()
          }
          val _tmpPassed: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfPassed)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfPassed).toInt()
          }
          _tmpPassed = _tmp?.let { it != 0 }
          _item = ExamRecordEntity(_tmpId,_tmpStartedAt,_tmpFinishedAt,_tmpTotal,_tmpSingleCount,_tmpJudgeCount,_tmpDurationSec,_tmpScore,_tmpPassed)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun answersFor(examId: Long): List<ExamAnswerEntity> {
    val _sql: String = "SELECT * FROM exam_answers WHERE examId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, examId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfExamId: Int = getColumnIndexOrThrow(_stmt, "examId")
        val _columnIndexOfQid: Int = getColumnIndexOrThrow(_stmt, "qid")
        val _columnIndexOfPicked: Int = getColumnIndexOrThrow(_stmt, "picked")
        val _columnIndexOfIsCorrect: Int = getColumnIndexOrThrow(_stmt, "isCorrect")
        val _result: MutableList<ExamAnswerEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ExamAnswerEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpExamId: Long
          _tmpExamId = _stmt.getLong(_columnIndexOfExamId)
          val _tmpQid: Long
          _tmpQid = _stmt.getLong(_columnIndexOfQid)
          val _tmpPicked: Int?
          if (_stmt.isNull(_columnIndexOfPicked)) {
            _tmpPicked = null
          } else {
            _tmpPicked = _stmt.getLong(_columnIndexOfPicked).toInt()
          }
          val _tmpIsCorrect: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfIsCorrect)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfIsCorrect).toInt()
          }
          _tmpIsCorrect = _tmp?.let { it != 0 }
          _item = ExamAnswerEntity(_tmpId,_tmpExamId,_tmpQid,_tmpPicked,_tmpIsCorrect)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun unanswered(examId: Long): List<ExamAnswerEntity> {
    val _sql: String = "SELECT * FROM exam_answers WHERE examId = ? AND picked IS NULL"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, examId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfExamId: Int = getColumnIndexOrThrow(_stmt, "examId")
        val _columnIndexOfQid: Int = getColumnIndexOrThrow(_stmt, "qid")
        val _columnIndexOfPicked: Int = getColumnIndexOrThrow(_stmt, "picked")
        val _columnIndexOfIsCorrect: Int = getColumnIndexOrThrow(_stmt, "isCorrect")
        val _result: MutableList<ExamAnswerEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ExamAnswerEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpExamId: Long
          _tmpExamId = _stmt.getLong(_columnIndexOfExamId)
          val _tmpQid: Long
          _tmpQid = _stmt.getLong(_columnIndexOfQid)
          val _tmpPicked: Int?
          if (_stmt.isNull(_columnIndexOfPicked)) {
            _tmpPicked = null
          } else {
            _tmpPicked = _stmt.getLong(_columnIndexOfPicked).toInt()
          }
          val _tmpIsCorrect: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfIsCorrect)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfIsCorrect).toInt()
          }
          _tmpIsCorrect = _tmp?.let { it != 0 }
          _item = ExamAnswerEntity(_tmpId,_tmpExamId,_tmpQid,_tmpPicked,_tmpIsCorrect)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun finishExam(
    examId: Long,
    finishedAt: Long,
    score: Float,
    passed: Boolean,
  ) {
    val _sql: String = "UPDATE exam_records SET finishedAt = ?, score = ?, passed = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, finishedAt)
        _argIndex = 2
        _stmt.bindDouble(_argIndex, score.toDouble())
        _argIndex = 3
        val _tmp: Int = if (passed) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 4
        _stmt.bindLong(_argIndex, examId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateAnswer(
    examId: Long,
    qid: Long,
    picked: Int,
    correct: Boolean,
  ) {
    val _sql: String = "UPDATE exam_answers SET picked = ?, isCorrect = ? WHERE examId = ? AND qid = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, picked.toLong())
        _argIndex = 2
        val _tmp: Int = if (correct) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 3
        _stmt.bindLong(_argIndex, examId)
        _argIndex = 4
        _stmt.bindLong(_argIndex, qid)
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
