package com.sf.batteryapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 电池数据模型，用于存储电池状态信息
 */
@Entity(tableName = "BatteryData")
/**
 * 电池实时数据实体类
 * 用于记录单次采集的完整电池状态快照
 */
data class BatteryData(
    /**
     * 主键，自增
     * Room 自动生成，确保每条记录唯一
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * 采集时间戳（毫秒）
     * 记录该条数据被采集时的系统时间，用于后续趋势分析
     */
    val timestamp: Long,

    /**
     * 电池当前电量（level）
     * 与 scale 配合使用，原始值，范围取决于厂商实现
     */
    val level: Int,

    /**
     * 电池满电量刻度（scale）
     * 与 level 共同计算百分比：percentage = level * 100 / scale
     */
    val scale: Int,

    /**
     * 电池状态编码
     * 对应 Android BatteryManager 的 BATTERY_STATUS_* 常量
     * 如：1=未知，2=充电中，3=放电中，4=未充电，5=充满
     */
    val status: Int,

    /**
     * 是否正在充电
     * true：正在充电（AC/USB/Wireless）
     * false：未充电
     */
    val isCharging: Boolean,

    /**
     * 瞬时电流（μA）
     * 正值表示充电，负值表示放电
     * 当硬件不支持时可能返回 -1
     */
    val currentNow: Int,

    /**
     * 剩余电量（μAh）
     * 自上次充满至今消耗的电量累计，部分设备支持
     * 不支持时返回 -1
     */
    val chargeCounter: Int,

    /**
     * 电池电压（V）
     * 实时电压值，用于判断电池健康与负载
     */
    val voltage: Double,

    /**
     * 电池温度（°C）
     * 由电池热敏电阻提供，过高可能影响寿命
     */
    val temperature: Double,

    /**
     * 实时功率（W）
     * 计算值：power ≈ |currentNow| * voltage / 1_000_000
     */
    val power: Double,

    /**
     * 电池百分比（0~100）
     * 计算值：level * 100 / scale，已四舍五入为整数
     */
    val percentage: Int,

    /**
     * 电流（A）
     * 统一单位后的电流值，保留三位小数
     * 优先使用 currentNow（μA→A），若无效则用 chargeCounter 估算
     */
    val current: Double
) {
    constructor(
        timestamp: Long,
        level: Int,
        scale: Int,
        status: Int,
        isCharging: Boolean,
        currentNow: Int,
        chargeCounter: Int,
        voltage: Double,
        temperature: Double,
        power: Double
    ) : this(
        0,
        timestamp,
        level,
        scale,
        status,
        isCharging,
        currentNow,
        chargeCounter,
        voltage,
        temperature,
        power,
        (level * 100 / scale),
        when {
            currentNow != -1 -> currentNow.toDouble() / 1000.0
            chargeCounter != -1 -> chargeCounter.toDouble() / 1000.0
            else -> 0.0
        }
    )
}

/**
 * 电池健康度数据模型
 */
@Entity(tableName = "BatteryHealthData")
data class BatteryHealthData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val cycleCount: Int,
    val actualCapacity: Int,
    val chargeHabitScore: Int,
    val temperature: Double,
    val healthScore: Int,
    val batteryHealth: Int // 新增：电池健康状态，对应BatteryManager.BATTERY_HEALTH_*常量
) {
    constructor(
        timestamp: Long,
        cycleCount: Int,
        actualCapacity: Int,
        chargeHabitScore: Int,
        temperature: Double,
        batteryHealth: Int,
        designCapacity: Double = 4000.0
    ) : this(
        0,
        timestamp,
        cycleCount,
        actualCapacity,
        chargeHabitScore,
        temperature,
        calculateHealthScore(batteryHealth, cycleCount, actualCapacity, temperature, chargeHabitScore, designCapacity),
        batteryHealth
    )
    
    companion object {
        /**
         * 计算综合健康分数
         */
        private fun calculateHealthScore(
            batteryHealth: Int,
            cycleCount: Int,
            actualCapacity: Int,
            temperature: Double,
            chargeHabitScore: Int,
            designCapacity: Double = 4000.0
        ): Int {
            // 基于电池健康状态的基础得分
            val baseScore = when (batteryHealth) {
                2 -> 95.0 // BATTERY_HEALTH_GOOD
                3 -> 60.0 // BATTERY_HEALTH_OVERHEAT
                4 -> 20.0 // BATTERY_HEALTH_DEAD
                5 -> 50.0 // BATTERY_HEALTH_OVER_VOLTAGE
                6 -> 40.0 // BATTERY_HEALTH_UNSPECIFIED_FAILURE
                7 -> 80.0 // BATTERY_HEALTH_COLD
                else -> 70.0 // 默认值
            }
            
            // 充电循环次数影响 (假设设计寿命为500次循环)
            val cycleFactor = when {
                cycleCount <= 100 -> 1.0
                cycleCount <= 300 -> 0.95
                cycleCount <= 500 -> 0.85
                else -> 0.7
            }
            
            // 实际容量影响
            val capacityFactor = minOf(actualCapacity / designCapacity, 1.0)
            
            // 温度影响 (理想温度范围: 20-30°C)
            val temperatureFactor = when {
                temperature < 0 || temperature > 45 -> 0.7
                temperature < 10 || temperature > 40 -> 0.85
                temperature < 20 || temperature > 30 -> 0.95
                else -> 1.0
            }
            
            // 充电习惯影响
            val chargeHabitFactor = chargeHabitScore / 100.0
            
            // 综合计算最终得分
            var finalScore = baseScore * cycleFactor * capacityFactor * temperatureFactor * chargeHabitFactor
            
            // 确保得分在0-100之间
            finalScore = maxOf(0.0, minOf(100.0, finalScore))
            
            return finalScore.toInt()
        }
    }
}

/**
 * 应用耗电数据模型
 */
@Entity(tableName = "AppBatteryUsage")
data class AppBatteryUsage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val totalUsage: Double,
    val backgroundUsage: Double,
    val wakelockTime: Long,
    val screenOnUsage: Double,
    val screenOffUsage: Double,
    val idleUsage: Double,
    val wlanUpload: Double = 0.0,
    val wlanDownload: Double = 0.0
)

/**
 * 电池历史数据模型，用于趋势分析
 */
@Entity(tableName = "BatteryHistory")
data class BatteryHistory(
    @PrimaryKey(autoGenerate = true)
    /**
     * 主键，自增
     * Room 自动生成，确保每条记录唯一
     */
    val id: Long = 0,
    /**
     * 日期时间戳（毫秒）
     * 记录该条历史数据对应的日期，用于按日聚合统计
     */
    val date: Long, // 使用Long类型存储时间戳
    /**
     * 当日总耗电量（mAh）
     * 统计当天电池的总消耗量，用于趋势分析
     */
    val dailyUsage: Double,
    /**
     * 当日充电次数
     * 记录当天用户进行充电的次数，用于评估充电习惯
     */
    val chargingCount: Int,
    /**
     * 平均充电速度（mAh/小时）
     * 当天所有充电过程的平均充电速率，反映充电效率
     */
    val averageChargeSpeed: Double,
    /**
     * 当日最低温度（°C）
     * 记录当天电池达到的最低温度，用于评估低温影响
     */
    val minTemperature: Double,
    /**
     * 当日最高温度（°C）
     * 记录当天电池达到的最高温度，用于评估高温影响
     */
    val maxTemperature: Double
)
