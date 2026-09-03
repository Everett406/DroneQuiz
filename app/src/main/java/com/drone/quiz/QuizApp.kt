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

    /** 本次启动快照（含上次是否异常退出），由 QuizApp.onCreate 填充。 */
    lateinit var bootSnapshot: BootGuard.Snapshot

    fun init(app: Application) {
        db = AppDatabase.get(app)
        repo = Repo(db)
        settings = SettingsStore(app)
    }
}

class QuizApp : Application() {
    override fun onCreate() {
        // 守护必须在一切之前：先装崩溃捕获，再记录启动心跳
        CrashGuard.install(this)
        ServiceLocator.bootSnapshot = BootGuard.beginBoot(this)
        BootGuard.log(this, "app", "QuizApp.onCreate")
        super.onCreate()
        runCatching {
            ServiceLocator.init(this)
            BootGuard.log(this, "app", "ServiceLocator 初始化完成")
        }.onFailure {
            BootGuard.log(this, "app", "ServiceLocator 初始化失败: ${it.javaClass.name}: ${it.message}")
            throw it
        }
    }
}
