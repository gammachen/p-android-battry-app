package com.batteryapp

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log

class BatteryMonitorService : Service() {

    private val TAG = "BatteryMonitorService"
    private var isRunning = false
    private lateinit var batteryReceiver: BroadcastReceiver
    private var batteryStatusListener: ((BatteryStatus) -> Unit)? = null

    data class BatteryStatus(
        val percentage: Int,
        val isCharging: Boolean,
        val status: Int,
        val current: Int,
        val voltage: Double,
        val temperature: Double,
        val power: Double
    )

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        isRunning = true
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val batteryStatus = getBatteryStatus(intent)
                    Log.d(TAG, "Battery Status Updated: $batteryStatus")
                    batteryStatusListener?.invoke(batteryStatus)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun getBatteryStatus(intent: Intent): BatteryStatus {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = (level * 100 / scale)
        
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL
        
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) / 1000.0
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        
        // 计算电流（mA）
        val currentMicroAmps = intent.getIntExtra("current_now", -1) // 使用字符串常量避免API版本问题
        val current = if (currentMicroAmps != -1) currentMicroAmps / 1000 else 0
        
        // 计算功率（W）
        val power = if (voltage > 0 && current != 0) (voltage * current) / 1000.0 else 0.0
        
        return BatteryStatus(percentage, isCharging, status, current, voltage, temperature, power)
    }

    fun setBatteryStatusListener(listener: ((BatteryStatus) -> Unit)?) {
        this.batteryStatusListener = listener
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isRunning = false
        unregisterReceiver(batteryReceiver)
    }
}
