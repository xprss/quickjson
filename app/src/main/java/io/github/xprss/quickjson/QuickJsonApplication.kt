package io.github.xprss.quickjson

import android.app.Application
import androidx.annotation.StringRes
import androidx.room.Room
import io.github.xprss.quickjson.data.DocumentRepository
import io.github.xprss.quickjson.data.FileGateway
import io.github.xprss.quickjson.data.QuickJsonDatabase
import io.github.xprss.quickjson.data.UserPreferences

class QuickJsonApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(private val application: Application) {
    private val database = Room.databaseBuilder(application, QuickJsonDatabase::class.java, "quickjson.db")
        .addMigrations(QuickJsonDatabase.MIGRATION_1_2)
        .build()

    val documents = DocumentRepository(database.dao())
    val preferences = UserPreferences(application)
    val files = FileGateway(application)

    fun text(@StringRes id: Int, vararg args: Any): String = application.getString(id, *args)
}
