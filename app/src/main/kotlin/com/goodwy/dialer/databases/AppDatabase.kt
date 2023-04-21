package com.goodwy.dialer.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.helpers.Converters
import com.goodwy.dialer.interfaces.TimerDao
import com.goodwy.dialer.models.Timer
import com.goodwy.dialer.models.TimerState
import java.util.concurrent.Executors

@Database(entities = [Timer::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun TimerDao(): TimerDao

    companion object {
        private var db: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            if (db == null) {
                synchronized(AppDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app.db")
                            .fallbackToDestructiveMigration()
                            .addCallback(object : Callback() {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    super.onCreate(db)
                                    insertDefaultTimer(context)
                                }
                            })
                            .build()
                    }
                }
            }
            return db!!
        }

        private fun insertDefaultTimer(context: Context) {
            Executors.newSingleThreadScheduledExecutor().execute {
                val config = context.config
                db!!.TimerDao().insertOrUpdateTimer(
                    Timer(
                        id = null,
                        seconds = config.timerSeconds,
                        state = TimerState.Idle,
                        vibrate = config.timerVibrate,
                        soundUri = config.timerSoundUri,
                        soundTitle = config.timerSoundTitle,
                        title = config.timerTitle ?: "",
                        label = config.timerLabel ?: "",
                        description = config.timerDescription ?: "",
                        createdAt = System.currentTimeMillis(),
                        channelId = config.timerChannelId,
                    )
                )
            }
        }
    }
}
