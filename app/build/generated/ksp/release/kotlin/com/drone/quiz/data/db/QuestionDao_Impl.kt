package com.drone.quiz.`data`.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class QuestionDao_Impl(
  __db: RoomDatabase,
) : QuestionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfQuestionEntity: EntityInsertAdapter<QuestionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfQuestionEntity = object : EntityInsertAdapter<QuestionEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `questions` (`id`,`category`,`type`,`question`,`options`,`answer`,`explanation`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: QuestionEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.category)
        statement.bindText(3, entity.type)
        statement.bindText(4, entity.question)
        statement.bindText(5, entity.options)
        statement.bindLong(6, entity.answer.toLong())
        statement.bindText(7, entity.explanation)
      }
    }
  }

  public override suspend fun insertAll(list: List<QuestionEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfQuestionEntity.insert(_connection, list)
  }

  public override fun countFlow(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM questions"
    return createFlow(__db, false, arrayOf("questions")) { _connection ->
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

  public override suspend fun count(): Int {
    val _sql: String = "SELECT COUNT(*) FROM questions"
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

  public override suspend fun byId(id: Long): QuestionEntity? {
    val _sql: String = "SELECT * FROM questions WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfQuestion: Int = getColumnIndexOrThrow(_stmt, "question")
        val _columnIndexOfOptions: Int = getColumnIndexOrThrow(_stmt, "options")
        val _columnIndexOfAnswer: Int = getColumnIndexOrThrow(_stmt, "answer")
        val _columnIndexOfExplanation: Int = getColumnIndexOrThrow(_stmt, "explanation")
        val _result: QuestionEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpQuestion: String
          _tmpQuestion = _stmt.getText(_columnIndexOfQuestion)
          val _tmpOptions: String
          _tmpOptions = _stmt.getText(_columnIndexOfOptions)
          val _tmpAnswer: Int
          _tmpAnswer = _stmt.getLong(_columnIndexOfAnswer).toInt()
          val _tmpExplanation: String
          _tmpExplanation = _stmt.getText(_columnIndexOfExplanation)
          _result = QuestionEntity(_tmpId,_tmpCategory,_tmpType,_tmpQuestion,_tmpOptions,_tmpAnswer,_tmpExplanation)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun byIds(ids: List<Long>): List<QuestionEntity> {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT * FROM questions WHERE id IN (")
    val _inputSize: Int = ids.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        for (_item: Long in ids) {
          _stmt.bindLong(_argIndex, _item)
          _argIndex++
        }
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfQuestion: Int = getColumnIndexOrThrow(_stmt, "question")
        val _columnIndexOfOptions: Int = getColumnIndexOrThrow(_stmt, "options")
        val _columnIndexOfAnswer: Int = getColumnIndexOrThrow(_stmt, "answer")
        val _columnIndexOfExplanation: Int = getColumnIndexOrThrow(_stmt, "explanation")
        val _result: MutableList<QuestionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item_1: QuestionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpQuestion: String
          _tmpQuestion = _stmt.getText(_columnIndexOfQuestion)
          val _tmpOptions: String
          _tmpOptions = _stmt.getText(_columnIndexOfOptions)
          val _tmpAnswer: Int
          _tmpAnswer = _stmt.getLong(_columnIndexOfAnswer).toInt()
          val _tmpExplanation: String
          _tmpExplanation = _stmt.getText(_columnIndexOfExplanation)
          _item_1 = QuestionEntity(_tmpId,_tmpCategory,_tmpType,_tmpQuestion,_tmpOptions,_tmpAnswer,_tmpExplanation)
          _result.add(_item_1)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun idsByFilter(cat: String?, type: String?): List<Long> {
    val _sql: String = "SELECT id FROM questions WHERE (? IS NULL OR category = ?) AND (? IS NULL OR type = ?)"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        if (cat == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, cat)
        }
        _argIndex = 2
        if (cat == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, cat)
        }
        _argIndex = 3
        if (type == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, type)
        }
        _argIndex = 4
        if (type == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, type)
        }
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

  public override suspend fun categories(): List<String> {
    val _sql: String = "SELECT DISTINCT category FROM questions ORDER BY category"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: MutableList<String> = mutableListOf()
        while (_stmt.step()) {
          val _item: String
          _item = _stmt.getText(0)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun catCounts(): List<CatCount> {
    val _sql: String = "SELECT category AS category, COUNT(*) AS cnt FROM questions GROUP BY category ORDER BY category"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfCategory: Int = 0
        val _columnIndexOfCnt: Int = 1
        val _result: MutableList<CatCount> = mutableListOf()
        while (_stmt.step()) {
          val _item: CatCount
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpCnt: Int
          _tmpCnt = _stmt.getLong(_columnIndexOfCnt).toInt()
          _item = CatCount(_tmpCategory,_tmpCnt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM questions"
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
