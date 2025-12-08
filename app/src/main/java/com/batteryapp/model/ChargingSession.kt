package com.batteryapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * 功率阈值定义（单位：瓦特）
 */
private object PowerThresholds {
    // 涓流充电：通常 < 5W
    const val TRICKLE_MAX = 5f
    
    // 慢充/普通充电：5W - 10W
    const val SLOW_MIN = 5f
    const val SLOW_MAX = 10f
    
    // 快充：10W - 25W
    const val FAST_MIN = 10f
    const val FAST_MAX = 25f
    
    // 超级快充：> 25W
    const val SUPER_FAST_MIN = 25f
    
    // 极速快充（如120W）：> 65W
    const val ULTRA_FAST_MIN = 65f
}

/**
 * 电流阈值定义（单位：毫安）
 */
private object CurrentThresholds {
    // 涓流充电最大电流
    const val TRICKLE_MAX = 100f
    
    // USB标准充电最大电流
    const val USB_STANDARD_MAX = 500f
}

/**
 * 充电模式数据类
 */
data class ChargingMode(
    val mode: Mode,
    val powerW: Float,           // 当前功率
) {
    enum class Mode {
        TRICKLE,        // 涓流充电（< 5W）
        SLOW,           // 慢充（5-10W）
        NORMAL,         // 普通充电（10-18W）
        FAST,           // 快充（18-25W）
        SUPER_FAST,     // 超级快充（25-65W）
        ULTRA_FAST,     // 极速快充（>65W）
        DISCHARGING,    // 放电中
        UNKNOWN         // 未知
    }
}

/**
 * 充电会话数据模型
 * 用于记录和分析用户的充电习惯
 */
@Entity(tableName = "charging_sessions")
data class ChargingSession(
    /** 唯一标识符 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 充电开始时间戳 */
    val startTime: Long,
    
    /** 充电结束时间戳 */
    val endTime: Long,
    
    /** 充电开始电量 */
    val startLevel: Int,
    
    /** 充电结束电量 */
    val endLevel: Int,
    
    /** 充电过程中的最高温度（摄氏度） */
    val maxTemperature: Float,
    
    /** 充电器类型（如USB、快充、无线充电等） */
    val chargerType: String,
    
    /** 充电模式 */
    val chargingMode: String = ChargingMode.Mode.UNKNOWN.name,
    
    /** 充电时长（毫秒） */
    val duration: Long = endTime - startTime
)

/**
 * 充电习惯分析结果
 */
data class ChargingHabitsAnalysis(
    /** 总充电会话数 */
    val totalSessions: Int,
    
    /** 平均充电时间（毫秒） */
    val avgChargingTime: Double,
    
    /** 平均起始电量 */
    val avgStartLevel: Double,
    
    /** 平均结束电量 */
    val avgEndLevel: Double,
    
    /** 峰值充电小时 */
    val peakChargingHour: Int,
    
    /** 过夜充电百分比 */
    val overnightChargePercentage: Double,
    
    /** 充电建议 */
    val recommendations: List<String>
) {
    companion object {
        /**
         * 创建空的分析结果
         */
        fun empty() = ChargingHabitsAnalysis(
            totalSessions = 0,
            avgChargingTime = 0.0,
            avgStartLevel = 0.0,
            avgEndLevel = 0.0,
            peakChargingHour = 0,
            overnightChargePercentage = 0.0,
            recommendations = emptyList()
        )
    }
}
