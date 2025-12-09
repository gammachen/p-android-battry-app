package com.batteryapp

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import com.batteryapp.data.BatteryRepository
import com.batteryapp.model.ChargingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

class BatteryMonitorService : Service() {

    private val TAG = "BatteryMonitorService"
    private var isRunning = false
    private lateinit var batteryReceiver: BroadcastReceiver
    private var batteryStatusListener: ((BatteryStatus) -> Unit)? = null
    
    // 充电会话跟踪相关
    // 电池数据仓库，用于保存充电会话等数据
    private lateinit var batteryRepository: BatteryRepository
    // 标记当前是否处于活跃的充电会话中
    private var isChargingSessionActive = false
    // 当前充电会话的开始时间（毫秒）
    private var chargingSessionStartTime: Long = 0
    // 当前充电会话开始时的电量百分比
    private var chargingSessionStartLevel: Int = 0
    // 当前充电会话中记录到的最高温度（摄氏度）
    private var chargingSessionMaxTemperature: Float = 0.0f
    // 当前充电会话中记录到的最大功率（瓦）
    private var chargingSessionMaxPowerW: Float = 0.0f
    // 当前充电会话的充电器类型（如 USB、AC、无线等）
    private var chargingSessionChargerType: String = ""
    // 上一次广播接收时的充电状态，用于检测充电开始/结束事件
    private var previousIsCharging = false

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
        batteryRepository = BatteryRepository(this)
        chargingSessionChargerType = getString(R.string.unknown)
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
                    val batteryIntentx = ContextCompat.registerReceiver(
                        this@BatteryMonitorService,
                        null,
                        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    if (batteryIntentx != null) {
                        val x = batteryIntentx.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                        Log.d(TAG, "batteryIntentx.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1): $x")
                    }                    

                    val batteryIntent = intent
                    
                    val chargePlug = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    Log.d(TAG, "充电类型 chargePlug: $chargePlug")

                    val batteryStatus = getBatteryStatus(batteryIntent)
                    Log.d(TAG, "Battery Status Updated batteryStatus: $batteryStatus")
                    batteryStatusListener?.invoke(batteryStatus)
                    
                    // 充电会话跟踪逻辑
                    val isCharging = batteryStatus.isCharging
                    
                    // 检测充电状态变化
                    if (isCharging && !previousIsCharging && chargePlug > 0) {
                        // 获取充电类型
                        val chargePlug = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                        
                        Log.d(TAG, "充电中，并且前一个状态不是充电中，并且能够得到充电类型（USB、AC、无线、dock等），插上电源线并且已经识别出物理电源线的场景: $chargePlug BatteryManager.BATTERY_PLUGGED_USB: ${BatteryManager.BATTERY_PLUGGED_USB} BatteryManager.BATTERY_PLUGGED_AC: ${BatteryManager.BATTERY_PLUGGED_AC} BatteryManager.BATTERY_PLUGGED_WIRELESS: ${BatteryManager.BATTERY_PLUGGED_WIRELESS} BatteryManager.BATTERY_PLUGGED_DOCK: ${BatteryManager.BATTERY_PLUGGED_DOCK}")
                        
                        val chargerType = when {
                            chargePlug == BatteryManager.BATTERY_PLUGGED_USB -> getString(R.string.charger_type_usb)
                            chargePlug == BatteryManager.BATTERY_PLUGGED_AC -> getString(R.string.charger_type_ac)
                            chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS -> getString(R.string.charger_type_wireless)
                            chargePlug == BatteryManager.BATTERY_PLUGGED_DOCK -> getString(R.string.charger_type_dock)
                            else -> getString(R.string.charging_mode_unknown_format, chargePlug)
                        }
                        // 开始新的充电会话
                        startChargingSession(batteryStatus.percentage, batteryStatus.temperature.toFloat(), chargerType)
                    } else if (!isCharging && previousIsCharging) {
                        Log.d(TAG, "放电中，并且前一个状态是充电中，拔掉电源充电线的场景!")
                        // 结束当前充电会话
                        endChargingSession(batteryStatus.percentage, batteryStatus.temperature.toFloat())
                    }
                    
                    // 持续充电中，更新充电会话数据
                    if (isCharging) {
                        Log.d(TAG, "持续充电中.................... chargingSessionMaxTemperature ${chargingSessionMaxTemperature} chargingSessionMaxPowerW ${chargingSessionMaxPowerW}")
                        // 更新充电会话中的最高温度
                        if (batteryStatus.temperature.toFloat() > chargingSessionMaxTemperature) {
                            chargingSessionMaxTemperature = batteryStatus.temperature.toFloat()
                        }
                        // 更新充电会话中的最大充电功率
                        val currentPower = batteryStatus.power.toFloat()
                        if (currentPower > chargingSessionMaxPowerW) {
                            chargingSessionMaxPowerW = currentPower
                        }
                    }
                    
                    // 更新之前的充电状态
                    previousIsCharging = isCharging
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
        
        // 获取电池当前状态（充电中、充满、未充电等）
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        /** 
        BatteryManager.BATTERY_STATUS_UNKNOWN - 值为 1
        未知状态
        BatteryManager.BATTERY_STATUS_CHARGING - 值为 2
        正在充电（通过交流电、USB等充电器）
        BatteryManager.BATTERY_STATUS_DISCHARGING - 值为 3
        未充电，正在放电（使用电池供电）
        BatteryManager.BATTERY_STATUS_NOT_CHARGING - 值为 4
        未充电（连接了充电器但未充电）
        BatteryManager.BATTERY_STATUS_FULL - 值为 5
        电池已充满
        */
        // 判断设备是否处于充电状态：当状态为“正在充电”或“已充满”时视为充电中
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL
        
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) / 1000.0
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        
        // 计算电流（mA）
        val currentMicroAmps = intent.getIntExtra("current_now", -1) // 使用字符串常量避免API版本问题
        var current = if (currentMicroAmps != -1) Math.round(currentMicroAmps / 1000.0).toInt() else 0

        Log.d(TAG, "Battery Status: $percentage %, isCharging: $isCharging, voltage: $voltage V, temperature: $temperature °C, current: $current mA ")

        // 需要 BATTERY_STATS 权限（系统权限）
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // 返回的是微安(µA)值，负值表示放电，正值表示充电
        current = Math.round(currentNow / 1000.0).toInt()

        Log.d(TAG, "Battery Status: $percentage %, isCharging: $isCharging, voltage: $voltage V, temperature: $temperature °C, current: $current mA, currentNow: $currentNow")
        
        // 计算功率（W）
        val power = if (voltage > 0) (voltage * current) / 1000.0 else 0.0
        
        return BatteryStatus(percentage, isCharging, status, current, voltage, temperature, power)
    }

    fun setBatteryStatusListener(listener: ((BatteryStatus) -> Unit)?) {
        this.batteryStatusListener = listener
    }
    
    /**
     * 开始充电会话
     */
    private fun startChargingSession(startLevel: Int, initialTemperature: Float, chargerType: String) {
        Log.d(TAG, "Starting charging session at $startLevel% with charger type: $chargerType")
        isChargingSessionActive = true
        chargingSessionStartTime = System.currentTimeMillis()
        chargingSessionStartLevel = startLevel
        chargingSessionMaxTemperature = initialTemperature
        chargingSessionMaxPowerW = 0.0f
        chargingSessionChargerType = chargerType
    }
    
    /**
     * 结束充电会话并保存到数据库
     */
    private fun endChargingSession(endLevel: Int, finalTemperature: Float) {
        if (!isChargingSessionActive) return
        
        Log.d(TAG, "Ending charging session at $endLevel% with charger type: $chargingSessionChargerType")
        
        // 更新最高温度
        if (finalTemperature > chargingSessionMaxTemperature) {
            chargingSessionMaxTemperature = finalTemperature
        }
        
        // 判断充电模式
        val chargingMode = batteryRepository.determineChargingMode(chargingSessionMaxPowerW)
        
        // 创建充电会话对象
        val chargingSession = ChargingSession(
            startTime = chargingSessionStartTime,
            endTime = System.currentTimeMillis(),
            startLevel = chargingSessionStartLevel,
            endLevel = endLevel,
            maxTemperature = chargingSessionMaxTemperature,
            chargerType = chargingSessionChargerType,
            chargingMode = chargingMode.mode.name
        )

        Log.d(TAG, "Charging session saved to database with data: $chargingSession")

        // 保存到数据库
        CoroutineScope(Dispatchers.IO).launch {
            try {
                batteryRepository.insertChargingSession(chargingSession)
                Log.d(TAG, "Charging session saved to database with data: $chargingSession")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save charging session: ${e.message}")
            }
        }
        
        // 重置充电会话状态
        isChargingSessionActive = false
        chargingSessionStartTime = 0
        chargingSessionStartLevel = 0
        chargingSessionMaxTemperature = 0.0f
        chargingSessionMaxPowerW = 0.0f
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isRunning = false
        unregisterReceiver(batteryReceiver)
    }
}
