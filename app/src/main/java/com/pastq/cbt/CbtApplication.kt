package com.pastq.cbt

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pastq.cbt.data.AppDatabase
import com.pastq.cbt.data.AuthManager
import com.pastq.cbt.data.LiveManager
import com.pastq.cbt.data.Repository
import com.pastq.cbt.update.Updater

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE results ADD COLUMN mode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE results ADD COLUMN subjects TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE results ADD COLUMN breakdown TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE questions ADD COLUMN reviewStatus TEXT NOT NULL DEFAULT 'unchecked'")
    }
}

class CbtApplication : Application() {
    val repository: Repository by lazy {
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "pastq.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        Repository(this, db)
    }

    override fun onCreate() {
        super.onCreate()
        Updater.init(this)
        AuthManager.init(this)
        LiveManager.init(this)
    }
}
