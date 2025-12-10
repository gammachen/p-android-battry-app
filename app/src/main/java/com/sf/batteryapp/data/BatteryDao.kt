package com.sf.batteryapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sf.batteryapp.model.BatteryData
import com.sf.batteryapp.model.BatteryHealthData
import com.sf.batteryapp.model.BatteryHistory
import com.sf.batteryapp.model.AppBatteryUsage
import com.sf.batteryapp.model.ChargingSession

/**
 * 电池数据访问对象，用于数据库操作
 */
@Dao
interface BatteryDao {
    // BatteryData相关操作
    @Insert
    suspend fun insertBatteryData(data: BatteryData)
    
    @Query("SELECT * FROM BatteryData ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentBatteryData(): List<BatteryData>
    
    @Query("SELECT * FROM BatteryData WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp")
    suspend fun getBatteryDataByTimeRange(startTime: Long, endTime: Long): List<BatteryData>
    
    // BatteryHealthData相关操作
    @Insert
    suspend fun insertBatteryHealthData(data: BatteryHealthData)
    
    @Query("SELECT * FROM BatteryHealthData ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBatteryHealthData(): BatteryHealthData?
    
    @Query("SELECT * FROM BatteryHealthData ORDER BY timestamp DESC LIMIT 30")
    suspend fun getRecentBatteryHealthData(): List<BatteryHealthData>
    
    // AppBatteryUsage相关操作
    @Insert
    suspend fun insertAppBatteryUsage(usage: AppBatteryUsage)
    
    // 原始数据查询
    @Query("SELECT * FROM AppBatteryUsage ORDER BY totalUsage DESC")
    suspend fun getAllAppBatteryUsage(): List<AppBatteryUsage>
    
    @Query("SELECT * FROM AppBatteryUsage ORDER BY backgroundUsage DESC")
    suspend fun getAppBatteryUsageByBackground(): List<AppBatteryUsage>
    
    @Query("SELECT * FROM AppBatteryUsage ORDER BY wakelockTime DESC")
    suspend fun getAppBatteryUsageByWakelock(): List<AppBatteryUsage>
    
    // 按应用分组累加用电量查询（过去24小时）
    // 按消耗的总电量（预估的）进行排序
    @Query("SELECT 0 AS id, packageName, appName, SUM(totalUsage) AS totalUsage, SUM(backgroundUsage) AS backgroundUsage, SUM(wakelockTime) AS wakelockTime, SUM(screenOnUsage) AS screenOnUsage, SUM(screenOffUsage) AS screenOffUsage, SUM(idleUsage) AS idleUsage, SUM(wlanUpload) AS wlanUpload, SUM(wlanDownload) AS wlanDownload, MAX(timestamp) AS timestamp FROM AppBatteryUsage WHERE timestamp > :oneDayAgo GROUP BY packageName, appName ORDER BY totalUsage DESC")
    suspend fun getAppBatteryUsageTotalByPackage(oneDayAgo: Long): List<AppBatteryUsage>
    
    // 按backgroundUsage进行排序
    @Query("SELECT 0 AS id, packageName, appName, SUM(totalUsage) AS totalUsage, SUM(backgroundUsage) AS backgroundUsage, SUM(wakelockTime) AS wakelockTime, SUM(screenOnUsage) AS screenOnUsage, SUM(screenOffUsage) AS screenOffUsage, SUM(idleUsage) AS idleUsage, SUM(wlanUpload) AS wlanUpload, SUM(wlanDownload) AS wlanDownload, MAX(timestamp) AS timestamp FROM AppBatteryUsage WHERE timestamp > :oneDayAgo GROUP BY packageName, appName ORDER BY backgroundUsage DESC")
    suspend fun getAppBatteryUsageBackgroundByPackage(oneDayAgo: Long): List<AppBatteryUsage>
    
    // 按backgroundUsage+screenOffUsage的和进行排序
    @Query("SELECT 0 AS id, packageName, appName, SUM(totalUsage) AS totalUsage, SUM(backgroundUsage) AS backgroundUsage, SUM(wakelockTime) AS wakelockTime, SUM(screenOnUsage) AS screenOnUsage, SUM(screenOffUsage) AS screenOffUsage, SUM(idleUsage) AS idleUsage, SUM(wlanUpload) AS wlanUpload, SUM(wlanDownload) AS wlanDownload, MAX(timestamp) AS timestamp FROM AppBatteryUsage WHERE timestamp > :oneDayAgo GROUP BY packageName, appName ORDER BY backgroundUsage + screenOffUsage DESC")
    suspend fun getAppBatteryUsageBackgroundAndScreenOffByPackage(oneDayAgo: Long): List<AppBatteryUsage>

    // 按wakelockTime进行排序
    @Query("SELECT 0 AS id, packageName, appName, SUM(totalUsage) AS totalUsage, SUM(backgroundUsage) AS backgroundUsage, SUM(wakelockTime) AS wakelockTime, SUM(screenOnUsage) AS screenOnUsage, SUM(screenOffUsage) AS screenOffUsage, SUM(idleUsage) AS idleUsage, SUM(wlanUpload) AS wlanUpload, SUM(wlanDownload) AS wlanDownload, MAX(timestamp) AS timestamp FROM AppBatteryUsage WHERE timestamp > :oneDayAgo GROUP BY packageName, appName ORDER BY wakelockTime DESC")
    suspend fun getAppBatteryUsageWakelockByPackage(oneDayAgo: Long): List<AppBatteryUsage>
    
    // BatteryHistory相关操作
    @Insert
    suspend fun insertBatteryHistory(history: BatteryHistory)
    
    @Query("SELECT * FROM BatteryHistory ORDER BY date DESC LIMIT 30")
    suspend fun getRecentBatteryHistory(): List<BatteryHistory>
    
    @Query("SELECT * FROM BatteryHistory WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getBatteryHistoryByDateRange(startDate: Long, endDate: Long): List<BatteryHistory>
    
    // ChargingSession相关操作
    @Insert
    suspend fun insertChargingSession(session: ChargingSession)
    
    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    suspend fun getAllChargingSessions(): List<ChargingSession>
    
    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT 30")
    suspend fun getRecentChargingSessions(): List<ChargingSession>
    
    @Query("SELECT * FROM charging_sessions WHERE startTime > :cutoffTime ORDER BY startTime")
    suspend fun getChargingSessionsAfter(cutoffTime: Long): List<ChargingSession>
    
    // 清空数据相关操作
    @Query("DELETE FROM BatteryData")
    suspend fun clearBatteryData()
    
    @Query("DELETE FROM BatteryHealthData")
    suspend fun clearBatteryHealthData()
    
    @Query("DELETE FROM AppBatteryUsage")
    suspend fun clearAppBatteryUsage()
    
    @Query("DELETE FROM BatteryHistory")
    suspend fun clearBatteryHistory()
    
    @Query("DELETE FROM charging_sessions")
    suspend fun clearChargingSessions()
}
