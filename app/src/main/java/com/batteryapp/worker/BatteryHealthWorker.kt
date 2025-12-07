package com.batteryapp.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.batteryapp.data.BatteryRepository

/**
 * 电池健康数据采集Worker，用于定时采集电池健康信息
 */
class BatteryHealthWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val batteryRepository = BatteryRepository(appContext)

    override suspend fun doWork(): Result {
        try {
            Log.d("BatteryHealthWorker", "开始采集电池健康数据")
            
            // 计算电池健康数据
            val healthData = batteryRepository.calculateBatteryHealth()
            Log.d("BatteryHealthWorker", "采集到电池健康数据: $healthData")
            
            // 存储到数据库
            batteryRepository.insertBatteryHealthData(healthData)
            
            Log.d("BatteryHealthWorker", "电池健康数据采集完成")
            return Result.success()
        } catch (e: Exception) {
            Log.e("BatteryHealthWorker", "电池健康数据采集失败: ${e.message}", e)
            return Result.retry()
        }
    }
}
