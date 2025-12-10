package com.sf.batteryapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sf.batteryapp.model.BatteryData
import com.sf.batteryapp.model.BatteryHealthData
import com.sf.batteryapp.model.BatteryHistory
import com.sf.batteryapp.model.AppBatteryUsage
import com.sf.batteryapp.model.ChargingSession
import com.sf.batteryapp.model.ChargingMode

/**
 * 电池数据库类，用于管理数据库的创建和版本控制
 */
@Database(
    entities = [
        BatteryData::class,
        BatteryHealthData::class,
        AppBatteryUsage::class,
        BatteryHistory::class,
        ChargingSession::class
    ],
    version = 8,
    exportSchema = false
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao
    
    companion object {
        @Volatile
        private var INSTANCE: BatteryDatabase? = null
        
        fun getInstance(context: Context): BatteryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BatteryDatabase::class.java,
                    "battery_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
