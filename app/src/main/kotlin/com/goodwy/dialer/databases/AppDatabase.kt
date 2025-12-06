package com.goodwy.dialer.databases

import android.content.Context
import android.media.RingtoneManager
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goodwy.commons.extensions.getDefaultAlarmSound
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.helpers.Converters
import com.goodwy.dialer.interfaces.TimerDao
import com.goodwy.dialer.models.Timer
import com.goodwy.dialer.models.TimerState
import java.util.concurrent.Executors

@Database(entities = [Timer::class], version = 2)
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
                            .addMigrations(MIGRATION_1_2)
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
                        seconds = 600,
                        state = TimerState.Idle,
                        vibrate = config.callVibration,
                        soundUri = context.getDefaultAlarmSound(RingtoneManager.TYPE_ALARM).uri,
                        soundTitle = "",
                        title = "Timer",
                        label = "",
                        description = "",
                        createdAt = System.currentTimeMillis(),
                        channelId = "right_dialer_timer_channel",
                    )
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `timers` ADD COLUMN `oneShot` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
