package com.sf.batteryapp.data

import androidx.room.Room
import android.content.Context
import com.sf.batteryapp.R
import com.sf.batteryapp.model.BatteryData
import com.sf.batteryapp.model.BatteryHealthData
import com.sf.batteryapp.model.AppBatteryUsage
import com.sf.batteryapp.model.BatteryHistory
import com.sf.batteryapp.model.ChargingSession
import com.sf.batteryapp.model.ChargingHabitsAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import android.os.BatteryManager
import android.os.Build

/**
 * 电池数据仓库，用于处理数据库操作和业务逻辑
 */
class BatteryRepository(private val context: Context) {

    companion object {
        // 日志标签
        private const val TAG = "BatteryInfo"
        
        // 典型的电池容量范围（mAh）
        private const val MIN_BATTERY_CAPACITY_MAH = 1000  // 最小电池容量 1000mAh
        private const val MAX_BATTERY_CAPACITY_MAH = 10000  // 最大电池容量 10000mAh

        // 合理的电池电压范围（毫伏）
        private const val MIN_VALID_VOLTAGE_MV = 3000  // 3.0V（极低电量）
        private const val MAX_VALID_VOLTAGE_MV = 10000  // 10.0V（满电）
    }



    private val database by lazy {
        BatteryDatabase.getInstance(context)
    }
    
    private val batteryDao by lazy {
        database.batteryDao()
    }
    
    // BatteryData相关操作
    suspend fun insertBatteryData(data: BatteryData) = withContext(Dispatchers.IO) {
        batteryDao.insertBatteryData(data)
    }
    
    suspend fun getRecentBatteryData() = withContext(Dispatchers.IO) {
        batteryDao.getRecentBatteryData()
    }
    
    suspend fun getBatteryDataByTimeRange(startTime: Long, endTime: Long) = withContext(Dispatchers.IO) {
        batteryDao.getBatteryDataByTimeRange(startTime, endTime)
    }
    
    // BatteryHealthData相关操作
    suspend fun insertBatteryHealthData(data: BatteryHealthData) = withContext(Dispatchers.IO) {
        batteryDao.insertBatteryHealthData(data)
    }
    
    suspend fun getLatestBatteryHealthData() = withContext(Dispatchers.IO) {
        batteryDao.getLatestBatteryHealthData()
    }
    
    suspend fun getRecentBatteryHealthData() = withContext(Dispatchers.IO) {
        batteryDao.getRecentBatteryHealthData()
    }
    
    // AppBatteryUsage相关操作
    suspend fun insertAppBatteryUsage(usage: AppBatteryUsage) = withContext(Dispatchers.IO) {
        batteryDao.insertAppBatteryUsage(usage)
    }
    
    suspend fun getAllAppBatteryUsage() = withContext(Dispatchers.IO) {
        batteryDao.getAllAppBatteryUsage()
    }
    
    suspend fun getAppBatteryUsageByBackground() = withContext(Dispatchers.IO) {
        Log.d("BatteryRepository", "batteryDao.getAppBatteryUsageByBackground")
        batteryDao.getAppBatteryUsageByBackground()
    }
    
    suspend fun getAppBatteryUsageByWakelock() = withContext(Dispatchers.IO) {
        batteryDao.getAppBatteryUsageByWakelock()
    }
    
    // BatteryHistory相关操作
    suspend fun insertBatteryHistory(history: BatteryHistory) = withContext(Dispatchers.IO) {
        batteryDao.insertBatteryHistory(history)
    }
    
    suspend fun getRecentBatteryHistory() = withContext(Dispatchers.IO) {
        batteryDao.getRecentBatteryHistory()
    }
    
    suspend fun getBatteryHistoryByDateRange(startDate: Long, endDate: Long) = withContext(Dispatchers.IO) {
        batteryDao.getBatteryHistoryByDateRange(startDate, endDate)
    }
    
    // 计算电池健康度
    suspend fun calculateBatteryHealth() = withContext(Dispatchers.IO) {
        // 从BatteryManager获取电池信息
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        // 获取电池温度（°C）
        val temperature = getBatteryTemperature(batteryManager)
        
        // 获取实际容量（mAh）
        val actualCapacity = getBatteryActualCapacity()

        Log.w("BatteryApp_x", "actualCapacity:$actualCapacity")
        
        // 获取充电循环次数和电池健康状态
        val (cycleCount, batteryHealth) = getBatteryCycleCount()
        
        // 计算充电习惯得分（0-100）
        val chargeHabitScore = calculateChargeHabitScore()
        
        BatteryHealthData(
            timestamp = System.currentTimeMillis(),
            cycleCount = cycleCount,
            actualCapacity = actualCapacity,
            chargeHabitScore = chargeHabitScore,
            temperature = temperature,
            batteryHealth = batteryHealth,
            designCapacity = getBatteryDesignCapacity(context)
        )
    }
    
    /**
     * 计算充电习惯得分（0-100）
     * 基于用户的充电行为模式评估充电习惯的健康程度
     */
    private suspend fun calculateChargeHabitScore(): Int {
        try {
            // 检查是否有充电记录数据
            val recentHealthData = batteryDao.getRecentBatteryHealthData()
            
            if (recentHealthData.isNotEmpty()) {
                // 基于历史数据评估充电习惯
                // 这里可以实现更复杂的算法，基于充电频率、充电时间、充电速率等
                // 暂时使用一个基于温度和容量变化的简单算法
                
                val latestData = recentHealthData.first()
                var score = 85 // 基础分数
                
                // 基于温度调整得分
                if (latestData.temperature < 10 || latestData.temperature > 40) {
                    score -= 10
                }
                
                // 基于容量变化调整得分
                val designCapacity = getBatteryDesignCapacity(context)
                val capacityPercentage = latestData.actualCapacity / designCapacity * 100
                if (capacityPercentage < 80) {
                    score -= 5
                }
                
                return maxOf(60, minOf(100, score))
            }
        } catch (e: Exception) {
            Log.e("BatteryRepository", "计算充电习惯得分失败: ${e.message}")
        }
        
        // 默认返回一个合理的分数
        return 85
    }

    /**
     * 智能标准化：检测并转换为 µAh
     */
    private fun normalizeChargeCounter(rawValue: Long): Long {
        if (rawValue <= 0) return rawValue
        
        // 检测当前值的可能单位
        return when (detectChargeCounterUnit(rawValue)) {
            ChargeCounterUnit.NANO_AMP_HOURS -> {
                Log.d("BatteryRepository", "Detected nAh, converting to µAh: ${rawValue / 1000L}")
                rawValue / 1000L  // nAh → µAh
            }
            ChargeCounterUnit.MILLI_AMP_HOURS -> {
                Log.d("BatteryRepository", "Detected mAh, converting to µAh: ${rawValue * 1000L}")
                rawValue * 1000L  // mAh → µAh
            }
            ChargeCounterUnit.MICRO_AMP_HOURS -> {
                Log.d("BatteryRepository", "Detected µAh, using as-is: $rawValue")
                rawValue  // 已经是 µAh
            }
            ChargeCounterUnit.UNKNOWN -> {
                // 启发式猜测：基于常见的电池容量范围
                guessAndConvert(rawValue)
            }
        }
    }

    /**
     * 单位枚举
     */
    private enum class ChargeCounterUnit {
        NANO_AMP_HOURS,   // nAh
        MICRO_AMP_HOURS,  // µAh
        MILLI_AMP_HOURS,  // mAh
        UNKNOWN
    }
    
    /**
     * 检测原始值的单位
     */
    private fun detectChargeCounterUnit(rawValue: Long): ChargeCounterUnit {
        // 将原始值转换为 mAh 范围进行判断
        val valueInMah = rawValue.toDouble() / 1000.0  // 假设是 µAh
        
        when {
            // 如果是 nAh 单位：值会很小（如 1650 nAh = 1.65 µAh = 0.00165 mAh）
            rawValue < 1000 -> {
                return ChargeCounterUnit.NANO_AMP_HOURS
            }
            
            // 如果是 µAh 单位：应该在合理电池容量范围内
            valueInMah in MIN_BATTERY_CAPACITY_MAH.toDouble()..MAX_BATTERY_CAPACITY_MAH.toDouble() -> {
                return ChargeCounterUnit.MICRO_AMP_HOURS
            }
            
            // 如果是 mAh 单位：值会很大（如 4861440 mAh 不合理，但可能是 µAh）
            rawValue > MAX_BATTERY_CAPACITY_MAH * 1000 -> {
                // 如果以 mAh 计算的值远大于最大电池容量，说明它实际上可能是 µAh
                // 例如：4861440 > 6000*1000，所以它不是 mAh
                return if (rawValue > MAX_BATTERY_CAPACITY_MAH * 1000L) {
                    ChargeCounterUnit.MICRO_AMP_HOURS
                } else {
                    ChargeCounterUnit.MILLI_AMP_HOURS
                }
            }
            
            // 其他情况：可能是 mAh
            else -> {
                return ChargeCounterUnit.MILLI_AMP_HOURS
            }
        }
    }
    
    /**
     * 启发式猜测与转换
     */
    private fun guessAndConvert(rawValue: Long): Long {
        // 基于 Android 版本的经验规则
        return when (Build.VERSION.SDK_INT) {
            in Build.VERSION_CODES.M..Build.VERSION_CODES.P -> {
                // Android 6.0-9.0：通常是 µAh
                Log.d(TAG, "API ${Build.VERSION.SDK_INT}: Assuming µAh")
                rawValue
            }
            Build.VERSION_CODES.Q -> {
                // Android 10：存在不一致，但大多数是 µAh
                Log.d(TAG, "API 29: Most devices use µAh")
                rawValue
            }
            Build.VERSION_CODES.R -> {
                // Android 11：有些设备可能改为 mAh
                Log.d(TAG, "API 30: Checking if mAh...")
                if (rawValue in 1000L..6000L) rawValue * 1000L else rawValue
            }
            Build.VERSION_CODES.S -> {
                // Android 12 (API 31)：你的设备返回 1650，很可能是 nAh
                Log.d(TAG, "API 31: Suspected nAh, converting to µAh")
                rawValue / 1000L
            }
            Build.VERSION_CODES.S_V2, Build.VERSION_CODES.TIRAMISU -> {
                // Android 12L/13：可能已修复，假设 µAh
                Log.d(TAG, "API ${Build.VERSION.SDK_INT}: Assuming µAh")
                rawValue
            }
            else -> {
                // 默认假设为 µAh
                Log.d(TAG, "Unknown API ${Build.VERSION.SDK_INT}: Assuming µAh")
                rawValue
            }
        }
    }
    
    /**
     * 获取电池实际容量（mAh）
     * 使用BatteryManager API获取准确的电池容量
     */
    private fun getBatteryActualCapacity(): Int {
        try {
            // 使用BatteryManager API估计电池容量
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            
            // 获取当前剩余电荷（微安时 µAh）
            val chargeCounter = when {
                // 方案1：优先使用 BatteryManager API（Android 6.0+）
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M -> {
                    val value = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    Log.d("BatteryInfo", "Using BatteryManager API ${android.os.Build.VERSION.SDK_INT}, charge_counter: $value")
                    value
                }
                
                // 方案2：使用 Intent 附加数据（全版本支持）
                else -> {
                    // 获取电池状态Intent
                    val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    
                    if (intent != null) {
                        // 注意：有些设备使用 Integer，有些使用 Long
                        val value = try {
                            // 尝试获取 Long 类型
                            intent.getLongExtra("charge_counter", -1L)
                        } catch (e: Exception) {
                            // 如果失败，尝试获取 Int 类型并转换
                            intent.getIntExtra("charge_counter", -1).toLong()
                        }
                        Log.d("BatteryInfo", "Using Intent API, charge_counter: $value")
                        value
                    } else {
                        // 方案3：所有方法都失败
                        Log.w("BatteryInfo", "All charge counter retrieval methods failed")
                        -1L
                    }
                }
            }
            Log.d("BatteryRepository", "chargeCounter: $chargeCounter μAh")
            
            // 获取当前电量百分比
            val percentage = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                } else {
                    // 旧版本使用intent计算
                    val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    if (level != -1 && scale != -1) {
                        (level * 100 / scale)
                    } else {
                        -1
                    }
                }
            } catch (e: Exception) {
                // 兜底方案
                val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level != -1 && scale != -1) {
                    (level * 100 / scale)
                } else {
                    -1
                }
            }
            
            Log.d("BatteryRepository", "chargeCounter: $chargeCounter μAh, percentage: $percentage%")

            val normalizedChargeCounter = normalizeChargeCounter(chargeCounter.toLong())
            
            // 基于电量百分比估算总容量：估算总容量 = (当前电荷 / 当前百分比) * 100% 
            val estimatedCapacity = if (normalizedChargeCounter > 0 && percentage > 0) {
                // 转换为mAh：μAh / 1000 = mAh
                val chargeInmAh = normalizedChargeCounter / 1000.0
                // 计算总容量
                ((chargeInmAh / percentage) * 100).toInt()
            } else {
                // 如果获取不到有效数据，返回一个合理的默认值
                3800 // 默认返回3800 mAh
            }
            
            Log.d("BatteryRepository", "estimatedCapacity: $estimatedCapacity mAh")
            return estimatedCapacity
        } catch (e: Exception) {
            Log.e("BatteryRepository", "获取电池实际容量失败: ${e.message}")
            return 3800 // 默认返回3800 mAh
        }
    }
    
    /**
     * 获取电池充电循环次数和健康状态
     * 返回Pair<循环次数, 健康状态>
     * 健康状态对应BatteryManager.BATTERY_HEALTH_*常量
     */
    private fun getBatteryCycleCount(): Pair<Int, Int> {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            
            // 获取电池健康状态
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val batteryHealth = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD) ?: BatteryManager.BATTERY_HEALTH_GOOD
            
            // 尝试获取真实的充电循环次数
            var cycleCount = -1
            
            // Android O及以上版本尝试使用BatteryManager API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // 使用反射获取BATTERY_PROPERTY_CYCLE_COUNT常量
                    val propertyField = BatteryManager::class.java.getField("BATTERY_PROPERTY_CYCLE_COUNT")
                    val property = propertyField.getInt(null)
                    cycleCount = batteryManager.getLongProperty(property).toInt()
                    Log.d("BatteryRepository", "成功获取真实充电循环次数: $cycleCount")
                } catch (e: Exception) {
                    Log.w("BatteryRepository", "通过API获取真实充电循环次数失败: ${e.message}")
                    
                    // 尝试通过反射调用getLongProperty方法
                    try {
                        val method = batteryManager.javaClass.getMethod("getLongProperty", Int::class.javaPrimitiveType)
                        // BATTERY_PROPERTY_CYCLE_COUNT的值为2
                        cycleCount = (method.invoke(batteryManager, 2) as Long).toInt()
                        Log.d("BatteryRepository", "通过反射获取真实充电循环次数: $cycleCount")
                    } catch (reflectException: Exception) {
                        Log.w("BatteryRepository", "通过反射获取真实充电循环次数失败: ${reflectException.message}")
                    }
                }
            } else {
                Log.d("BatteryRepository", "当前Android版本(${Build.VERSION.RELEASE})不支持获取真实充电循环次数")
            }
            
            // 如果无法获取真实循环次数，则根据电池健康状态估算
            if (cycleCount <= 0) {
                Log.w("BatteryRepository", "无法获取真实充电循环次数，将使用电池健康状态来估算")

                cycleCount = when (batteryHealth) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> 50 // 健康状态良好，循环次数较少
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> 200 // 过热，循环次数较多
                    BatteryManager.BATTERY_HEALTH_DEAD -> 500 // 电池已损坏，循环次数很多
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> 150 // 过电压，循环次数较多
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> 300 // 不明故障，循环次数较多
                    BatteryManager.BATTERY_HEALTH_COLD -> 80 // 低温，循环次数适中
                    else -> 50
                }
                Log.d("BatteryRepository", "使用估算的充电循环次数: $cycleCount (基于电池健康状态: $batteryHealth)")
            }
            
            Log.d("BatteryRepository", "最终获取的充电循环次数: $cycleCount, 电池健康状态: $batteryHealth")
            return Pair(cycleCount, batteryHealth)
        } catch (e: Exception) {
            Log.e("BatteryRepository", "获取充电循环次数过程中发生异常: ${e.message}", e)
            val defaultCycleCount = 50
            val defaultHealth = BatteryManager.BATTERY_HEALTH_GOOD
            Log.d("BatteryRepository", "使用默认值: 充电循环次数=$defaultCycleCount, 电池健康状态=$defaultHealth")
            return Pair(defaultCycleCount, defaultHealth) // 默认返回50次和良好健康状态
        }
    }
    
    /**
     * 获取电池温度（°C）
     */
    private fun getBatteryTemperature(batteryManager: BatteryManager): Double {
        try {
            // 使用Intent获取电池温度（兼容所有Android版本）
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250
            return temperature / 10.0 // 转换为°C
        } catch (e: Exception) {
            Log.e("BatteryRepository", "获取电池温度失败: ${e.message}")
            return 25.0
        }
    }
    


    /**
     * 通过 PowerProfile 类获取电池设计容量
     * 这个类包含了设备的各种电源消耗配置，包括电池容量
     */
    fun getBatteryDesignCapacity(context: Context): Double {
        return try {
            // 获取 PowerProfile 类
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            
            // 创建实例
            val constructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfile = constructor.newInstance(context)
            
            // 调用 getBatteryCapacity 方法
            val method = powerProfileClass.getMethod("getBatteryCapacity")
            val capacity = method.invoke(powerProfile) as Double
            
            capacity.toDouble()
        } catch (e: Exception) {
            Log.e("BatteryInfo", "Failed to get capacity from PowerProfile", e)
            // 默认返回4000mAh
            4000.0
        }
    }
    
    // 获取应用耗电排行榜（过去24小时）
    suspend fun getAppBatteryUsageRanking(rankType: BatteryUsageRankType) = withContext(Dispatchers.IO) {
        // 这里实现应用耗电排行榜逻辑
        Log.d("getAppBatteryUsageRanking", "rankType: $rankType")
        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000 // 过去24小时（TODO 理论上要从开机时间开始的）
        when (rankType) {
            // 总耗电量
            BatteryUsageRankType.TIME_USAGE -> batteryDao.getAppBatteryUsageBackgroundAndScreenOffByPackage(oneDayAgo)
            // 后台耗电量
            BatteryUsageRankType.BACKGROUND_USAGE -> batteryDao.getAppBatteryUsageBackgroundByPackage(oneDayAgo)
            // 唤醒锁时间
            BatteryUsageRankType.WAKELOCK_TIME -> batteryDao.getAppBatteryUsageWakelockByPackage(oneDayAgo)
            // 预估耗电量(使用总耗电量)
            BatteryUsageRankType.ESTIMATED_CONSUMPTION -> batteryDao.getAppBatteryUsageTotalByPackage(oneDayAgo)
        }
    }
    
    // 分场景统计
    suspend fun getAppBatteryUsageByScene(sceneType: BatteryUsageSceneType) = withContext(Dispatchers.IO) {
        // 这里实现分场景统计逻辑
        // 暂时返回所有应用的统计数据
        getAllAppBatteryUsage()
    }
    
    // 清空数据库所有数据
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        batteryDao.clearBatteryData()
        batteryDao.clearBatteryHealthData()
        batteryDao.clearAppBatteryUsage()
        batteryDao.clearBatteryHistory()
        batteryDao.clearChargingSessions()
    }
    
    // ChargingSession相关操作
    /**
     * 插入充电会话数据
     */
    suspend fun insertChargingSession(session: ChargingSession) {
        withContext(Dispatchers.IO) {
            batteryDao.insertChargingSession(session)
        }
    }
    
    /**
     * 获取最近的充电会话记录
     */
    suspend fun getRecentChargingSessions(): List<ChargingSession> {
        return withContext(Dispatchers.IO) {
            batteryDao.getRecentChargingSessions()
        }
    }
    
    /**
     * 获取所有充电会话记录
     */
    suspend fun getAllChargingSessions(): List<ChargingSession> {
        return withContext(Dispatchers.IO) {
            batteryDao.getAllChargingSessions()
        }
    }
    
    /**
     * 获取指定时间之后的充电会话记录
     */
    suspend fun getChargingSessionsAfter(cutoffTime: Long): List<ChargingSession> {
        return withContext(Dispatchers.IO) {
            batteryDao.getChargingSessionsAfter(cutoffTime)
        }
    }
    
    /**
     * 分析充电习惯
     */
    suspend fun analyzeChargingHabits(): ChargingHabitsAnalysis {
        val sessions = getAllChargingSessions()
        
        if (sessions.isEmpty()) {
            return ChargingHabitsAnalysis.empty()
        }
        
        // 计算平均充电时间
        val avgChargingTime = sessions.map { it.duration }.average()
        
        // 计算平均起始电量
        val avgStartLevel = sessions.map { it.startLevel }.average()
        
        // 计算平均结束电量
        val avgEndLevel = sessions.map { it.endLevel }.average()
        
        // 找出最常见的充电时间段
        val hourlyDistribution = sessions.groupBy { 
            java.util.Calendar.getInstance().apply { timeInMillis = it.startTime }.get(java.util.Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }
        
        val peakHour = hourlyDistribution.maxByOrNull { it.value }?.key ?: 0
        
        // 分析是否经常过夜充电
        val overnightCharges = sessions.count { session ->
            val startHour = java.util.Calendar.getInstance().apply { timeInMillis = session.startTime }.get(java.util.Calendar.HOUR_OF_DAY)
            val endHour = java.util.Calendar.getInstance().apply { timeInMillis = session.endTime }.get(java.util.Calendar.HOUR_OF_DAY)
            (startHour in 22..23 || startHour in 0..6) && 
            (endHour in 6..12) && session.duration > 6 * 60 * 60 * 1000
        }
        
        val overnightPercentage = overnightCharges.toDouble() / sessions.size * 100
        
        return ChargingHabitsAnalysis(
            totalSessions = sessions.size,
            avgChargingTime = avgChargingTime,
            avgStartLevel = avgStartLevel,
            avgEndLevel = avgEndLevel,
            peakChargingHour = peakHour,
            overnightChargePercentage = overnightPercentage,
            recommendations = generateRecommendations(
                avgStartLevel, avgEndLevel, avgChargingTime, overnightPercentage
            )
        )
    }
    
    private fun generateRecommendations(
        avgStart: Double,
        avgEnd: Double,
        avgTime: Double,
        overnightPercent: Double
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // 充电起始电量建议
        when {
            avgStart < 20 -> recommendations.add(context.getString(R.string.recommendation_start_low, avgStart.toInt()))
            avgStart in 20.0..40.0 -> recommendations.add(context.getString(R.string.recommendation_start_good))
            else -> recommendations.add(context.getString(R.string.recommendation_start_high))
        }
        
        // 充电结束电量建议
        when {
            avgEnd > 90 -> recommendations.add(context.getString(R.string.recommendation_end_high, avgEnd.toInt()))
            avgEnd in 70.0..90.0 -> recommendations.add(context.getString(R.string.recommendation_end_good))
            else -> recommendations.add(context.getString(R.string.recommendation_end_low))
        }
        
        // 过夜充电建议
        if (overnightPercent > 30) {
            recommendations.add(context.getString(R.string.recommendation_overnight, overnightPercent.toInt()))
        }
        
        // 充电时长建议
        val hours = avgTime / (60 * 60 * 1000)
        if (hours > 3) {
            recommendations.add(context.getString(R.string.recommendation_duration_long, hours.toInt()))
        }
        
        return recommendations
    }
    
    enum class BatteryUsageRankType {
        TIME_USAGE,
        BACKGROUND_USAGE,
        WAKELOCK_TIME,
        ESTIMATED_CONSUMPTION
    }
    
    enum class BatteryUsageSceneType {
        SCREEN_ON,
        SCREEN_OFF,
        IDLE
    }

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
     * 根据功率和电流判断充电模式
     */
    fun determineChargingMode(powerW: Float, currentMa: Float = 0f): com.sf.batteryapp.model.ChargingMode {
        val mode = when {
            // 首先检查是否在放电
            powerW <= 0 -> com.sf.batteryapp.model.ChargingMode.Mode.DISCHARGING
            
            // 涓流充电判断
            powerW < PowerThresholds.TRICKLE_MAX -> {
                // 结合电流进一步确认是否为真正的涓流充电
                com.sf.batteryapp.model.ChargingMode.Mode.TRICKLE
            }
            
            // 慢充
            powerW in PowerThresholds.SLOW_MIN..PowerThresholds.SLOW_MAX -> {
                com.sf.batteryapp.model.ChargingMode.Mode.SLOW
            }
            
            // 普通充电
            powerW in PowerThresholds.FAST_MIN..18f -> {
                com.sf.batteryapp.model.ChargingMode.Mode.NORMAL
            }
            
            // 快充
            powerW in 18f..PowerThresholds.FAST_MAX -> {
                com.sf.batteryapp.model.ChargingMode.Mode.FAST
            }
            
            // 超级快充
            powerW in PowerThresholds.SUPER_FAST_MIN..PowerThresholds.ULTRA_FAST_MIN -> {
                com.sf.batteryapp.model.ChargingMode.Mode.SUPER_FAST
            }
            
            // 极速快充
            powerW > PowerThresholds.ULTRA_FAST_MIN -> {
                com.sf.batteryapp.model.ChargingMode.Mode.ULTRA_FAST
            }
            
            // 未知模式
            else -> com.sf.batteryapp.model.ChargingMode.Mode.UNKNOWN
        }
        
        return com.sf.batteryapp.model.ChargingMode(mode, powerW)
    }

    /**
     * 计算涓流充电置信度
     */
    private fun calculateTrickleConfidence(currentMa: Float): Float {
        return when {
            // 涓流特征：小电流 + 高电量（>95%）
            currentMa < CurrentThresholds.TRICKLE_MAX -> 0.9f
            
            // 可能只是弱充电器
            currentMa < CurrentThresholds.USB_STANDARD_MAX -> 0.6f
            
            else -> 0.3f
        }
    }
}
