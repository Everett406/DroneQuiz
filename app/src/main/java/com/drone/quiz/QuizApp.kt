package com.drone.quiz

import android.app.Application
import com.drone.quiz.data.db.AppDatabase
import com.drone.quiz.data.repo.Repo
import com.drone.quiz.data.settings.SettingsStore

object ServiceLocator {
    lateinit var db: AppDatabase
        private set
    lateinit var repo: Repo
        private set
    lateinit var settings: SettingsStore
        private set

    fun init(app: Application) {
        db = AppDatabase.get(app)
        repo = Repo(db)
        settings = SettingsStore(app)
    }
}

class QuizApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
