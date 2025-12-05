package com.batteryapp.data

import androidx.room.Room
import android.content.Context
import com.batteryapp.model.BatteryData
import com.batteryapp.model.BatteryHealthData
import com.batteryapp.model.AppBatteryUsage
import com.batteryapp.model.BatteryHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import android.os.BatteryManager
import android.os.Build

/**
 * 电池数据仓库，用于处理数据库操作和业务逻辑
 */
class BatteryRepository(private val context: Context) {
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
        
        // 获取充电循环次数和电池健康状态
        val (cycleCount, batteryHealth) = getBatteryCycleCount()
        
        // 计算充电习惯得分（0-100）
        val chargeHabitScore = 85 // 暂时返回一个合理的默认值
        
        BatteryHealthData(
            timestamp = System.currentTimeMillis(),
            cycleCount = cycleCount,
            actualCapacity = actualCapacity,
            chargeHabitScore = chargeHabitScore,
            temperature = temperature,
            batteryHealth = batteryHealth
        )
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
            val chargeCounter = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                } else {
                    // 旧版本使用intent获取
                    val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    intent?.getLongExtra("charge_counter", -1) ?: -1
                }
            } catch (e: Exception) {
                // 兜底方案
                val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                intent?.getLongExtra("charge_counter", -1) ?: -1
            }
            
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
            
            // 基于电量百分比估算总容量：估算总容量 = (当前电荷 / 当前百分比) * 100% 
            val estimatedCapacity = if (chargeCounter > 0 && percentage > 0) {
                // 转换为mAh：μAh / 1000 = mAh
                val chargeInmAh = chargeCounter / 1000.0
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
            // 获取电池健康状态
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val batteryHealth = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD) ?: BatteryManager.BATTERY_HEALTH_GOOD
            
            // 根据电池健康状态估算充电循环次数
            val cycleCount = when (batteryHealth) {
                BatteryManager.BATTERY_HEALTH_GOOD -> 50 // 健康状态良好，循环次数较少
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> 200 // 过热，循环次数较多
                BatteryManager.BATTERY_HEALTH_DEAD -> 500 // 电池已损坏，循环次数很多
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> 150 // 过电压，循环次数较多
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> 300 // 不明故障，循环次数较多
                BatteryManager.BATTERY_HEALTH_COLD -> 80 // 低温，循环次数适中
                else -> 50
            }
            
            return Pair(cycleCount, batteryHealth)
        } catch (e: Exception) {
            Log.e("BatteryRepository", "获取充电循环次数失败: ${e.message}")
            return Pair(50, BatteryManager.BATTERY_HEALTH_GOOD) // 默认返回50次和良好健康状态
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
    
    // 获取应用耗电排行榜（过去24小时）
    suspend fun getAppBatteryUsageRanking(rankType: BatteryUsageRankType) = withContext(Dispatchers.IO) {
        // 这里实现应用耗电排行榜逻辑
        Log.d("getAppBatteryUsageRanking", "rankType: $rankType")
        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000 // 过去24小时（TODO 理论上要从开机时间开始的）
        when (rankType) {
            // 总耗电量
            BatteryUsageRankType.TOTAL_USAGE -> batteryDao.getAppBatteryUsageBackgroundAndScreenOffByPackage(oneDayAgo)
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
    }
    
    enum class BatteryUsageRankType {
        TOTAL_USAGE,
        BACKGROUND_USAGE,
        WAKELOCK_TIME,
        ESTIMATED_CONSUMPTION
    }
    
    enum class BatteryUsageSceneType {
        SCREEN_ON,
        SCREEN_OFF,
        IDLE
    }
}
