package com.pastq.cbt

import android.app.Application
import androidx.room.Room
import com.pastq.cbt.data.AppDatabase
import com.pastq.cbt.data.Repository

class CbtApplication : Application() {
    val repository: Repository by lazy {
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "pastq.db").build()
        Repository(this, db)
    }
}
