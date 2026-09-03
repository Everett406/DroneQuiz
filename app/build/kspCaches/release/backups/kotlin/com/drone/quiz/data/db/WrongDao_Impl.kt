package com.drone.quiz.`data`.db

import androidx.room.EntityInsertAdapter
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
public class WrongDao_Impl(
  __db: RoomDatabase,
) : WrongDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfWrongBookEntity: EntityInsertAdapter<WrongBookEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfWrongBookEntity = object : EntityInsertAdapter<WrongBookEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `wrongbook` (`id`,`qid`,`addedAt`,`wrongCount`,`correctStreak`,`removed`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WrongBookEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.qid)
        statement.bindLong(3, entity.addedAt)
        statement.bindLong(4, entity.wrongCount.toLong())
        statement.bindLong(5, entity.correctStreak.toLong())
        val _tmp: Int = if (entity.removed) 1 else 0
        statement.bindLong(6, _tmp.toLong())
      }
    }
  }

  public override suspend fun upsert(e: WrongBookEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfWrongBookEntity.insert(_connection, e)
  }

  public override suspend fun forQuestion(qid: Long): WrongBookEntity? {
    val _sql: String = "SELECT * FROM wrongbook WHERE qid = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, qid)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfQid: Int = getColumnIndexOrThrow(_stmt, "qid")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _columnIndexOfWrongCount: Int = getColumnIndexOrThrow(_stmt, "wrongCount")
        val _columnIndexOfCorrectStreak: Int = getColumnIndexOrThrow(_stmt, "correctStreak")
        val _columnIndexOfRemoved: Int = getColumnIndexOrThrow(_stmt, "removed")
        val _result: WrongBookEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpQid: Long
          _tmpQid = _stmt.getLong(_columnIndexOfQid)
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          val _tmpWrongCount: Int
          _tmpWrongCount = _stmt.getLong(_columnIndexOfWrongCount).toInt()
          val _tmpCorrectStreak: Int
          _tmpCorrectStreak = _stmt.getLong(_columnIndexOfCorrectStreak).toInt()
          val _tmpRemoved: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfRemoved).toInt()
          _tmpRemoved = _tmp != 0
          _result = WrongBookEntity(_tmpId,_tmpQid,_tmpAddedAt,_tmpWrongCount,_tmpCorrectStreak,_tmpRemoved)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun activeWrongWithQuestions(): Flow<List<WrongWithQuestion>> {
    val _sql: String = "SELECT w.*, q.question AS question, q.category AS category, q.type AS type, q.options AS options, q.answer AS answer, q.explanation AS explanation FROM wrongbook w JOIN questions q ON w.qid = q.id WHERE w.removed = 0 ORDER BY w.addedAt DESC"
    return createFlow(__db, false, arrayOf("wrongbook", "questions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfQid: Int = getColumnIndexOrThrow(_stmt, "qid")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _columnIndexOfWrongCount: Int = getColumnIndexOrThrow(_stmt, "wrongCount")
        val _columnIndexOfCorrectStreak: Int = getColumnIndexOrThrow(_stmt, "correctStreak")
        val _columnIndexOfRemoved: Int = getColumnIndexOrThrow(_stmt, "removed")
        val _columnIndexOfQuestion: Int = getColumnIndexOrThrow(_stmt, "question")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfOptions: Int = getColumnIndexOrThrow(_stmt, "options")
        val _columnIndexOfAnswer: Int = getColumnIndexOrThrow(_stmt, "answer")
        val _columnIndexOfExplanation: Int = getColumnIndexOrThrow(_stmt, "explanation")
        val _result: MutableList<WrongWithQuestion> = mutableListOf()
        while (_stmt.step()) {
          val _item: WrongWithQuestion
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpQid: Long
          _tmpQid = _stmt.getLong(_columnIndexOfQid)
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          val _tmpWrongCount: Int
          _tmpWrongCount = _stmt.getLong(_columnIndexOfWrongCount).toInt()
          val _tmpCorrectStreak: Int
          _tmpCorrectStreak = _stmt.getLong(_columnIndexOfCorrectStreak).toInt()
          val _tmpRemoved: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfRemoved).toInt()
          _tmpRemoved = _tmp != 0
          val _tmpQuestion: String
          _tmpQuestion = _stmt.getText(_columnIndexOfQuestion)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpOptions: String
          _tmpOptions = _stmt.getText(_columnIndexOfOptions)
          val _tmpAnswer: Int
          _tmpAnswer = _stmt.getLong(_columnIndexOfAnswer).toInt()
          val _tmpExplanation: String
          _tmpExplanation = _stmt.getText(_columnIndexOfExplanation)
          _item = WrongWithQuestion(_tmpId,_tmpQid,_tmpAddedAt,_tmpWrongCount,_tmpCorrectStreak,_tmpRemoved,_tmpQuestion,_tmpCategory,_tmpType,_tmpOptions,_tmpAnswer,_tmpExplanation)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun activeWrongIds(): List<Long> {
    val _sql: String = "SELECT qid FROM wrongbook WHERE removed = 0"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: MutableList<Long> = mutableListOf()
        while (_stmt.step()) {
          val _item: Long
          _item = _stmt.getLong(0)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun wrongCountFlow(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM wrongbook WHERE removed = 0"
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

  public override suspend fun markRemoved(qid: Long) {
    val _sql: String = "UPDATE wrongbook SET removed = 1 WHERE qid = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, qid)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM wrongbook"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun reAdd(qid: Long) {
    val _sql: String = "UPDATE wrongbook SET removed = 0, correctStreak = 0 WHERE qid = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
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
