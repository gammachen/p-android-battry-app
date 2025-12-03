package com.batteryapp.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.batteryapp.data.BatteryRepository
import com.batteryapp.model.BatteryData

/**
 * 电池数据采集Worker，用于定时采集电池状态信息
 */
class BatteryUsageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val batteryRepository = BatteryRepository(appContext)

    override suspend fun doWork(): Result {
        try {
            Log.d("BatteryUsageWorker", "开始采集电池数据")
            
            // 采集电池状态数据
            val batteryData = collectBatteryData()
            Log.d("BatteryUsageWorker", "采集到电池数据---->: $batteryData")
            
            // 存储到数据库
            batteryRepository.insertBatteryData(batteryData)
            
            Log.d("BatteryUsageWorker", "电池数据采集完成")
            return Result.success()
        } catch (e: Exception) {
            Log.e("BatteryUsageWorker", "电池数据采集失败: ${e.message}")
            return Result.retry()
        }
    }

    /**
     * 采集电池状态数据
     */
    private fun collectBatteryData(): BatteryData {
        val intent = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            applicationContext.registerReceiver(null, filter)
        }

        // 获取电池状态信息
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL
        val currentNow = intent?.getIntExtra("current_now", -1) ?: -1
        val chargeCounter = intent?.getIntExtra("charge_counter", -1) ?: -1
        val voltageRaw = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3800) ?: 3800
        val voltage = voltageRaw / 1000.0
        val temperatureRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val temperature = temperatureRaw / 10.0

        // 计算功率（W）= 电压（V） * 电流（A）= 电压（V） * 电流（mA） / 1000
        val current = when {
            currentNow != -1 -> currentNow.toDouble() / 1000.0
            chargeCounter != -1 -> chargeCounter.toDouble() / 1000.0
            else -> 0.0
        }
        val power = (voltage * current) / 1000.0

        return BatteryData(
            timestamp = System.currentTimeMillis(),
            level = level,
            scale = scale,
            status = status,
            isCharging = isCharging,
            currentNow = currentNow,
            chargeCounter = chargeCounter,
            voltage = voltage,
            temperature = temperature,
            power = power
        )
    }
}