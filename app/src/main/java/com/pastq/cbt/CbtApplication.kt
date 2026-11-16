package com.pastq.cbt

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pastq.cbt.data.AppDatabase
import com.pastq.cbt.data.AuthManager
import com.pastq.cbt.data.LiveManager
import com.pastq.cbt.data.Repository
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
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

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE questions ADD COLUMN image TEXT NOT NULL DEFAULT ''")
    }
}

// NECO questions (MySchool) commonly use five options (A-E); JAMB/WAEC stay at four.
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE questions ADD COLUMN e TEXT NOT NULL DEFAULT ''")
    }
}

class CbtApplication : Application(), ImageLoaderFactory {
    // Pictures are kept on the phone (up to 150 MB) whatever the server's cache headers say.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(filesDir.resolve("question_images")).maxSizeBytes(150L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()

    val repository: Repository by lazy {
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "pastq.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        Repository(this, db)
    }

    override fun onCreate() {
        super.onCreate()
        Updater.init(this)
        AuthManager.init(this)
        LiveManager.init(this)
        Push.init(this)
    }
}
