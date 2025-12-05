package com.batteryapp.worker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import android.app.usage.UsageStatsManager
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.batteryapp.data.BatteryRepository
import com.batteryapp.model.AppBatteryUsage
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/**
 * 应用耗电统计Worker，用于定时采集应用耗电数据
 */
class AppBatteryUsageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val batteryRepository = BatteryRepository(appContext)
    private val packageManager = appContext.packageManager

    override suspend fun doWork(): Result {
        try {
            Log.d("AppBatteryUsageWorker", "开始采集应用耗电数据")
            
            // 采集应用耗电数据
            val appUsageList = collectAppBatteryUsage()
            Log.d("AppBatteryUsageWorker", "采集到应用耗电数据---->: $appUsageList")
            
            // 存储到数据库
            Log.d("AppBatteryUsageWorker", "开始将应用耗电数据存储到数据库，共${appUsageList.size}个应用")
            var successCount = 0
            var failCount = 0
            
            appUsageList.forEach { appUsage ->
                try {
                    Log.d("AppBatteryUsageWorker", "准备插入应用耗电数据: ${appUsage.appName} (${appUsage.packageName}), 使用时间: ${appUsage.totalUsage}秒, 后台使用: ${appUsage.backgroundUsage}秒, 唤醒锁时间: ${appUsage.wakelockTime}ms 后台使用: ${appUsage.backgroundUsage}秒, 屏幕关闭使用: ${appUsage.screenOffUsage}秒, 屏幕开启时使用: ${appUsage.screenOnUsage}秒, ")
                    
                    // 执行插入操作
                    batteryRepository.insertAppBatteryUsage(appUsage)
                    
                    Log.d("AppBatteryUsageWorker", "成功插入应用耗电数据: ${appUsage.appName} (${appUsage.packageName}), 使用时间: ${appUsage.totalUsage}秒, 后台使用: ${appUsage.backgroundUsage}秒, 唤醒锁时间: ${appUsage.wakelockTime}ms 后台使用: ${appUsage.backgroundUsage}秒, 屏幕关闭使用: ${appUsage.screenOffUsage}秒, 屏幕开启时使用: ${appUsage.screenOnUsage}秒, ")
                    successCount++
                } catch (e: Exception) {
                    Log.e("AppBatteryUsageWorker", "插入应用耗电数据失败: ${appUsage.appName} (${appUsage.packageName}), 错误信息: ${e.message}")
                    failCount++
                }
            }
            
            Log.d("AppBatteryUsageWorker", "应用耗电数据存储完成，成功: $successCount 个, 失败: $failCount 个, 总计: ${appUsageList.size} 个应用")
            return Result.success()
        } catch (e: Exception) {
            Log.e("AppBatteryUsageWorker", "应用耗电数据采集失败: ${e.message}")
            return Result.retry()
        }
    }

    /**
     * 采集应用耗电数据
     */
    private fun collectAppBatteryUsage(): List<AppBatteryUsage> {
        try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 24 * 60 * 60 * 1000 // 过去24小时
            
            // 调试日志
            Log.d("AppBatteryUsageWorker", "开始查询应用使用统计，时间范围：$startTime 到 $endTime")
            
            // 使用PackageManager获取所有已安装应用
            val packages = packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
            
            Log.d("AppBatteryUsageWorker", "使用PackageManager获取到 ${packages.size} 个应用")
            
            val appUsageList = mutableListOf<AppBatteryUsage>()
            
            // 获取UsageStatsManager实例
            val usageStatsManager = applicationContext.getSystemService(UsageStatsManager::class.java)
            
            // 获取UsageStats数据
            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            Log.d("AppBatteryUsageWorker", "获取到 ${usageStatsList.size} 个应用的UsageStats数据")
            
            // 将UsageStats按包名分组
            val usageStatsMap = usageStatsList.associateBy { it.packageName }
            Log.d("AppBatteryUsageWorker", "已分组应用：${usageStatsMap.keys}")
            
            // 获取应用耗电量数据
            val appBatteryConsumptionMap = getAppBatteryConsumption()
            
            packages.forEach { appInfo ->
                try {
                    // 获取应用名称和包名
                    val appName = appInfo.loadLabel(packageManager).toString()
                    val packageName = appInfo.packageName
                    
                    // 跳过空名称的应用
                    if (appName.isEmpty()) {
                        Log.d("AppBatteryUsageWorker", "跳过空名称应用: ${packageName}")
                        return@forEach
                    }
                    
                    // 只处理用户应用，跳过真正的系统应用
                    // 真正的系统应用：SYSTEM标志为1且UPDATED_SYSTEM_APP标志为0
                    val isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val isUpdatedSystemApp = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    if (isSystemApp && !isUpdatedSystemApp) {
                        Log.w("AppBatteryUsageWorker", "跳过系统应用: ${appName}（${packageName}）")
                        return@forEach
                    }
                    
                    // 从UsageStats获取应用使用时间
                    val usageStats = usageStatsMap[packageName]
                    // 初始化变量
                    // 前台使用时间（秒）
                    var foregroundTime = 0.0
                    // 后台使用时间（秒）
                    var backgroundUsage = 0.0
                    // 唤醒锁时间（毫秒）   
                    var wakelockTime = 0L
                    
                    if (usageStats != null) {
                        // 获取前台使用时间（毫秒转换为秒）
                        foregroundTime = usageStats.totalTimeInForeground / 1000.0
                        // 估算后台使用时间（假设为前台使用时间的30%）
                        backgroundUsage = foregroundTime * 0.3
                        // 估算唤醒锁时间（假设为总使用时间的50%）
                        wakelockTime = ((foregroundTime + backgroundUsage) * 0.5 * 1000).toLong()
                    } else {
                        Log.w("AppBatteryUsageWorker", "没有UsageStats数据，跳过这个应用: ${appName}（${packageName}）")
                        return@forEach
                    }
                    
                    // 计算屏幕开启和关闭状态下的使用时间
                    val screenOnUsage = foregroundTime // 前台使用时屏幕肯定开启
                    val screenOffUsage = backgroundUsage // 后台使用时屏幕可能关闭
                    val idleUsage = screenOffUsage * 0.5 // 假设50%的后台时间是空闲状态
                    
                    // 获取应用WLAN使用情况
                    val (wlanUpload, wlanDownload) = getAppWlanUsage(packageName)
                    
                    // 获取应用耗电量（mAh）
                    val totalUsage = appBatteryConsumptionMap[packageName] ?: calculateBatteryUsageFromTime(
                        foregroundTime,
                        backgroundUsage,
                        wlanUpload,
                        wlanDownload
                    )
                    
                    // 增加调试日志
                    Log.d("AppBatteryUsageWorker", "应用${appName}（${packageName}）耗电量: $totalUsage mAh")
                    Log.d("AppBatteryUsageWorker", "  - 前台时间: $foregroundTime 秒")
                    Log.d("AppBatteryUsageWorker", "  - 后台时间: $backgroundUsage 秒")
                    Log.d("AppBatteryUsageWorker", "  - WLAN上传: $wlanUpload MB")
                    Log.d("AppBatteryUsageWorker", "  - WLAN下载: $wlanDownload MB")

                    val appUsage = AppBatteryUsage(
                        timestamp = System.currentTimeMillis(),
                        packageName = packageName,
                        appName = appName,
                        totalUsage = totalUsage,
                        backgroundUsage = backgroundUsage,
                        wakelockTime = wakelockTime,
                        screenOnUsage = screenOnUsage,
                        screenOffUsage = screenOffUsage,
                        idleUsage = idleUsage,
                        wlanUpload = wlanUpload,
                        wlanDownload = wlanDownload
                    )
                    
                    appUsageList.add(appUsage)
                } catch (e: Exception) {
                    Log.e("AppBatteryUsageWorker", "处理应用${appInfo.packageName}时出错: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            return appUsageList
        } catch (e: Exception) {
            Log.e("AppBatteryUsageWorker", "获取应用耗电数据失败: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * 获取应用耗电量数据
     * 目前返回空Map，所有应用都使用基于时间的估算
     * 注意：Android系统没有公开的API允许普通应用获取其他应用的准确耗电量
     * 要获取准确的耗电量，需要使用ADB命令或系统权限
     */
    private fun getAppBatteryConsumption(): Map<String, Double> {
        Log.d("AppBatteryUsageWorker", "Android系统没有公开API允许普通应用获取准确的应用耗电量")
        Log.d("AppBatteryUsageWorker", "使用基于时间的估算方案")
        return emptyMap()
    }
    
    /**
     * 获取应用的UID
     */
    private fun getUidForPackage(packageName: String): Int {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            return packageInfo.applicationInfo.uid
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("AppBatteryUsageWorker", "获取应用UID失败: $packageName, ${e.message}")
            return -1
        }
    }

    /**
     * 获取应用的WLAN网络使用情况
     * @param packageName 应用包名
     * @return Pair<Double, Double> 第一个值是上传量（MB），第二个值是下载量（MB）
     */
    private fun getAppWlanUsage(packageName: String): Pair<Double, Double> {
        try {
            val uid = getUidForPackage(packageName)
            if (uid == -1) {
                Log.e("AppBatteryUsageWorker", "无法获取应用UID: $packageName")
                return Pair(0.0, 0.0)
            }

            // 使用TrafficStats获取应用的网络流量数据
            val totalRxBytes = TrafficStats.getUidRxBytes(uid)
            val totalTxBytes = TrafficStats.getUidTxBytes(uid)

            // 检查是否完全不支持获取网络流量数据
            if (totalRxBytes == TrafficStats.UNSUPPORTED.toLong() && totalTxBytes == TrafficStats.UNSUPPORTED.toLong()) {
                Log.w("AppBatteryUsageWorker", "TrafficStats完全不支持获取应用网络流量数据: $packageName")
                return Pair(0.0, 0.0)
            }
            
            // 部分支持时的日志
            if (totalRxBytes == TrafficStats.UNSUPPORTED.toLong()) {
                Log.w("AppBatteryUsageWorker", "TrafficStats不支持获取应用下载流量数据: $packageName")
            }
            if (totalTxBytes == TrafficStats.UNSUPPORTED.toLong()) {
                Log.w("AppBatteryUsageWorker", "TrafficStats不支持获取应用上传流量数据: $packageName")
            }

            // 转换为MB
            val rxMB = if (totalRxBytes != TrafficStats.UNSUPPORTED.toLong()) {
                totalRxBytes / (1024.0 * 1024.0)
            } else {
                0.0
            }

            val txMB = if (totalTxBytes != TrafficStats.UNSUPPORTED.toLong()) {
                totalTxBytes / (1024.0 * 1024.0)
            } else {
                0.0
            }

            Log.d("AppBatteryUsageWorker", "应用${packageName} 网络使用情况: 上传${txMB}MB, 下载${rxMB}MB")
            return Pair(txMB, rxMB)
        } catch (e: Exception) {
            Log.e("AppBatteryUsageWorker", "获取应用网络使用情况失败: $packageName, ${e.message}")
            return Pair(0.0, 0.0)
        }
    }

    /**
     * 基于使用时间估算应用耗电量
     * @param foregroundTime 前台使用时间（秒）
     * @param backgroundTime 后台使用时间（秒）
     * @param wlanUpload WLAN上传量（MB）
     * @param wlanDownload WLAN下载量（MB）
     * @return 估算的耗电量（mAh）
     */
    private fun calculateBatteryUsageFromTime(
        foregroundTime: Double,
        backgroundTime: Double,
        wlanUpload: Double,
        wlanDownload: Double
    ): Double {
        try {
            // 基于时间的耗电量估算模型
            // 前台应用：假设每小时消耗20mAh
            // 后台应用：假设每小时消耗5mAh
            // WLAN数据传输：假设每MB上传/下载消耗0.1mAh
            val foregroundConsumptionRate = 20.0 / 3600.0 // mAh/秒
            val backgroundConsumptionRate = 5.0 / 3600.0  // mAh/秒
            val wlanConsumptionRate = 0.1 // mAh/MB
            
            // 计算总耗电量
            val totalUsage = (foregroundTime * foregroundConsumptionRate) + 
                           (backgroundTime * backgroundConsumptionRate) + 
                           ((wlanUpload + wlanDownload) * wlanConsumptionRate)
            
            // 确保耗电量为正数
            return Math.max(totalUsage, 0.0)
        } catch (e: Exception) {
            Log.e("AppBatteryUsageWorker", "计算耗电量时出错: ${e.message}")
            // 默认返回基于总时间的简单估算
            val totalTime = foregroundTime + backgroundTime
            return (totalTime / 3600.0) * 10.0 // 假设每小时消耗10mAh
        }
    }
}